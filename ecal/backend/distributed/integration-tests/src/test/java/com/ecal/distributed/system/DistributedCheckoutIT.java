package com.ecal.distributed.system;

import static org.junit.jupiter.api.Assertions.*;

import com.ecal.distributed.inventory.InventoryApplication;
import com.ecal.distributed.orders.*;
import com.ecal.distributed.payment.PaymentApplication;
import com.ecal.distributed.support.Fault;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Three real HTTP applications, three independently credentialed databases, and test-only failure proxies.
 * One PostgreSQL process hosts test databases to bound test resources; Compose uses three separate instances. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistributedCheckoutIT {

  @Container
  static final PostgreSQLContainer PG = new PostgreSQLContainer(
    "postgres:17.6"
  );

  static final String PRODUCT = "10000000-0000-0000-0000-000000000001";
  ConfigurableApplicationContext inventory, payment, orders;
  JdbcTemplate inventoryDb, paymentDb, ordersDb;
  FaultProxy inventoryProxy, paymentProxy;
  final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(2))
    .build();
  final JsonMapper json = JsonMapper.builder().build();
  String base;

  String url(String db) {
    return (
      "jdbc:postgresql://" +
      PG.getHost() +
      ":" +
      PG.getMappedPort(5432) +
      "/" +
      db
    );
  }

  JdbcTemplate database(String name) {
    return new JdbcTemplate(
      new DriverManagerDataSource(url(name), name, name + "_test_only")
    );
  }

  ConfigurableApplicationContext app(
    Class<?> type,
    String name,
    String... extra
  ) {
    var args = new ArrayList<>(
      List.of(
        "--spring.config.name=" + name + "-application",
        "--server.port=0",
        "--spring.datasource.url=" + url(name),
        "--spring.datasource.username=" + name,
        "--spring.datasource.password=" + name + "_test_only",
        "--spring.datasource.hikari.maximum-pool-size=8",
        "--spring.main.banner-mode=off"
      )
    );
    args.addAll(List.of(extra));
    return new SpringApplicationBuilder(type).run(args.toArray(String[]::new));
  }

  int port(ConfigurableApplicationContext c) {
    return ((WebServerApplicationContext) c).getWebServer().getPort();
  }

  void startOrders() {
    orders = app(
      OrdersApplication.class,
      "orders",
      "--ecal.inventory-url=" + inventoryProxy.url(),
      "--ecal.payment-url=" + paymentProxy.url(),
      "--ecal.remote-request-ms=700",
      "--ecal.remote-connect-ms=300"
    );
    base = "http://127.0.0.1:" + port(orders);
  }

  @BeforeAll
  void start() throws Exception {
    var admin = new JdbcTemplate(
      new DriverManagerDataSource(
        PG.getJdbcUrl(),
        PG.getUsername(),
        PG.getPassword()
      )
    );
    for (String name : List.of("inventory", "payment", "orders", "legacy")) {
      admin.execute(
        "create role " + name + " login password '" + name + "_test_only'"
      );
      admin.execute("create database " + name + " owner " + name);
      admin.execute("revoke connect on database " + name + " from public");
    }
    org.flywaydb.core.Flyway.configure()
      .dataSource(url("legacy"), "legacy", "legacy_test_only")
      .locations(
        "filesystem:" +
          java.nio.file.Path.of(
            "..",
            "..",
            "modular-monolith",
            "src",
            "main",
            "resources",
            "db",
            "migration"
          ).toAbsolutePath()
      )
      .load()
      .migrate();
    inventory = app(InventoryApplication.class, "inventory");
    payment = app(PaymentApplication.class, "payment");
    inventoryProxy = new FaultProxy("http://127.0.0.1:" + port(inventory));
    paymentProxy = new FaultProxy("http://127.0.0.1:" + port(payment));
    startOrders();
    inventoryDb = database("inventory");
    paymentDb = database("payment");
    ordersDb = database("orders");
    // Warm classloading separately so a fault test reaches its intended dependency, not an earlier cold-start timeout.
    String warmKey = UUID.randomUUID().toString();
    int warmStatus = 503;
    for (
      int attempt = 0;
      attempt < 5 && warmStatus == 503;
      attempt++
    ) warmStatus = checkout(warmKey, "APPROVE").statusCode();
    assertTrue(
      warmStatus == 200 || warmStatus == 201,
      "Warm checkout must recover with its original identity."
    );
  }

  @AfterAll
  void stop() {
    if (orders != null) orders.close();
    if (inventoryProxy != null) inventoryProxy.close();
    if (paymentProxy != null) paymentProxy.close();
    if (inventory != null) inventory.close();
    if (payment != null) payment.close();
  }

  @BeforeEach
  void reset() {
    inventoryProxy.rule.set(null);
    paymentProxy.rule.set(null);
    inventoryProxy.seen.clear();
    paymentProxy.seen.clear();
    ordersDb.execute(
      "truncate notification_record,purchase_order,migration_receipt"
    );
    inventoryDb.execute("truncate reservation,migration_receipt");
    paymentDb.execute("truncate payment,migration_receipt");
    inventoryDb.update(
      "delete from stock where product_id not in ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000003')"
    );
    ordersDb.update(
      "delete from catalog_product where id not in ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000003')"
    );
    inventoryDb.update("update stock set available=20");
  }

  String command(String outcome) {
    return (
      "{\"productId\":\"" +
      PRODUCT +
      "\",\"quantity\":1,\"paymentOutcome\":\"" +
      outcome +
      "\"}"
    );
  }

  HttpResponse<String> call(String url, String method, String body, String key)
    throws Exception {
    var b = HttpRequest.newBuilder(URI.create(url)).timeout(
      Duration.ofSeconds(10)
    );
    if (key != null) b.header("Idempotency-Key", key);
    if (body == null) b.GET();
    else b
      .header("Content-Type", "application/json")
      .method(method, HttpRequest.BodyPublishers.ofString(body));
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> checkout(String key, String outcome) throws Exception {
    return call(base + "/api/orders", "POST", command(outcome), key);
  }

  JsonNode body(HttpResponse<String> r) {
    return json.readTree(r.body());
  }

  int count(JdbcTemplate db, String table) {
    return db.queryForObject("select count(*) from " + table, Integer.class);
  }

  int stock() {
    return inventoryDb.queryForObject(
      "select available from stock where product_id=?",
      Integer.class,
      UUID.fromString(PRODUCT)
    );
  }

  String state() {
    return ordersDb.queryForObject(
      "select status from purchase_order",
      String.class
    );
  }

  void effects(int order, int reservation, int payment, int notifications) {
    assertEquals(order, count(ordersDb, "purchase_order"));
    assertEquals(reservation, count(inventoryDb, "reservation"));
    assertEquals(payment, count(paymentDb, "payment"));
    assertEquals(notifications, count(ordersDb, "notification_record"));
  }

  @Test
  void approvalIsServerPricedAndExactReplayDoesNotRepeatEffects()
    throws Exception {
    String key = UUID.randomUUID().toString();
    var first = checkout(key, "APPROVE");
    assertEquals(201, first.statusCode());
    assertEquals("499.00", body(first).get("totalAmount").asText());
    assertEquals("CONFIRMED", state());
    assertEquals(19, stock());
    var replay = checkout(key, "APPROVE");
    assertEquals(200, replay.statusCode());
    assertEquals(body(first), body(replay));
    effects(1, 1, 1, 1);
    assertEquals(409, checkout(key, "DECLINE").statusCode());
    assertEquals(19, stock());
  }

  @Test
  void declineIsNotCancelledUntilReleaseIsAcknowledged() throws Exception {
    inventoryProxy.fail(p -> p.endsWith("/release"), Mode.BEFORE_503);
    String key = UUID.randomUUID().toString();
    assertEquals(503, checkout(key, "DECLINE").statusCode());
    assertEquals("RELEASE_PENDING", state());
    assertEquals(19, stock());
    effects(1, 1, 1, 0);
    assertEquals(
      "CANCELLED",
      body(checkout(key, "DECLINE")).get("status").asText()
    );
    assertEquals(20, stock());
    effects(1, 1, 1, 1);
  }

  @Test
  void reservationCommitWithoutAcknowledgementResumesOneReservation()
    throws Exception {
    inventoryProxy.fail(p -> !p.endsWith("/release"), Mode.AFTER_503);
    String key = UUID.randomUUID().toString();
    assertEquals(503, checkout(key, "APPROVE").statusCode());
    assertEquals("PENDING", state());
    assertEquals(19, stock());
    effects(1, 1, 0, 0);
    assertEquals(200, checkout(key, "APPROVE").statusCode());
    assertEquals(19, stock());
    effects(1, 1, 1, 1);
  }

  @Test
  void paymentReadTimeoutAfterCommitRemainsUnknownAndReconciles()
    throws Exception {
    paymentProxy.fail(p -> true, Mode.DELAY_AFTER);
    String key = UUID.randomUUID().toString();
    assertEquals(503, checkout(key, "APPROVE").statusCode());
    assertEquals("PAYMENT_UNKNOWN", state());
    assertEquals(19, stock());
    effects(1, 1, 1, 0);
    assertEquals(
      "CONFIRMED",
      body(checkout(key, "APPROVE")).get("status").asText()
    );
    effects(1, 1, 1, 1);
    assertEquals(19, stock());
  }

  @Test
  void releaseCommitWithoutAcknowledgementCannotIncrementStockTwice()
    throws Exception {
    inventoryProxy.fail(p -> p.endsWith("/release"), Mode.AFTER_503);
    String key = UUID.randomUUID().toString();
    assertEquals(503, checkout(key, "DECLINE").statusCode());
    assertEquals("RELEASE_PENDING", state());
    assertEquals(20, stock());
    assertEquals(200, checkout(key, "DECLINE").statusCode());
    assertEquals(20, stock());
    effects(1, 1, 1, 1);
  }

  @Test
  void malformedPaymentSuccessIsUncertainAndNeverBecomesAFalseDecline()
    throws Exception {
    paymentProxy.fail(p -> true, Mode.MALFORMED_AFTER);
    String key = UUID.randomUUID().toString();
    assertEquals(503, checkout(key, "APPROVE").statusCode());
    assertEquals("PAYMENT_UNKNOWN", state());
    assertEquals(19, stock());
    assertEquals(200, checkout(key, "APPROVE").statusCode());
    effects(1, 1, 1, 1);
  }

  @Test
  void additiveProviderResponseFieldsAreCompatible() throws Exception {
    paymentProxy.fail(p -> true, Mode.ADDITIVE_AFTER);
    assertEquals(
      201,
      checkout(UUID.randomUUID().toString(), "APPROVE").statusCode()
    );
    effects(1, 1, 1, 1);
  }

  @Test
  void recoverySurvivesOrdersRestart() throws Exception {
    paymentProxy.fail(p -> true, Mode.AFTER_503);
    String key = UUID.randomUUID().toString();
    assertEquals(503, checkout(key, "APPROVE").statusCode());
    String id = ordersDb.queryForObject(
      "select id::text from purchase_order",
      String.class
    );
    orders.close();
    startOrders();
    var response = checkout(key, "APPROVE");
    assertEquals(200, response.statusCode());
    assertEquals(id, body(response).get("id").asText());
    effects(1, 1, 1, 1);
    assertEquals(19, stock());
  }

  @Test
  void localTerminalCommitFailureDoesNotRollBackRemotePayment()
    throws Exception {
    ordersDb.execute(
      "create function reject_notification() returns trigger language plpgsql as $$ begin raise exception 'injected local failure'; end $$"
    );
    ordersDb.execute(
      "create trigger reject_notification before insert on notification_record for each row execute function reject_notification()"
    );
    String key = UUID.randomUUID().toString();
    try {
      assertEquals(503, checkout(key, "APPROVE").statusCode());
      assertEquals("PAYMENT_UNKNOWN", state());
      effects(1, 1, 1, 0);
    } finally {
      ordersDb.execute(
        "drop trigger reject_notification on notification_record"
      );
      ordersDb.execute("drop function reject_notification()");
    }
    assertEquals(200, checkout(key, "APPROVE").statusCode());
    effects(1, 1, 1, 1);
    assertEquals(19, stock());
  }

  @Test
  void releaseTombstoneBlocksDelayedReserveAndChangedPayloadConflicts()
    throws Exception {
    String id = UUID.randomUUID().toString();
    String payload = "{\"productId\":\"" + PRODUCT + "\",\"quantity\":1}";
    String endpoint =
      "http://127.0.0.1:" + port(inventory) + "/internal/v1/reservations/" + id;
    assertEquals(
      200,
      call(endpoint + "/release", "PUT", payload, null).statusCode()
    );
    assertEquals(
      "RELEASED",
      body(call(endpoint, "PUT", payload, null)).get("state").asText()
    );
    assertEquals(409, call(endpoint, "PUT", payload, null).statusCode());
    assertEquals(
      409,
      call(
        endpoint + "/release",
        "PUT",
        payload.replace(":1}", ":2}"),
        null
      ).statusCode()
    );
    assertEquals(20, stock());
  }

  @Test
  void changedPaymentIdentityCannotOverwriteAuthorization() throws Exception {
    String endpoint =
      "http://127.0.0.1:" +
      port(payment) +
      "/internal/v1/payments/" +
      UUID.randomUUID();
    String payload =
      "{\"amount\":\"10.00\",\"currency\":\"INR\",\"outcome\":\"APPROVE\"}";
    assertEquals(200, call(endpoint, "PUT", payload, null).statusCode());
    assertEquals(
      409,
      call(
        endpoint,
        "PUT",
        payload.replace("10.00", "11.00"),
        null
      ).statusCode()
    );
    assertEquals(
      "10.00",
      paymentDb.queryForObject("select amount::text from payment", String.class)
    );
  }

  @Test
  void expiredCoordinatorCannotOverwriteNewLeaseOwner() {
    var store = orders.getBean(OrderStore.class);
    UUID id = store
      .claim(
        UUID.randomUUID(),
        new OrderStore.Command(UUID.fromString(PRODUCT), 1, "APPROVE")
      )
      .stored()
      .view()
      .id();
    UUID old = UUID.randomUUID(),
      newer = UUID.randomUUID();
    assertTrue(store.lease(id, old));
    ordersDb.update(
      "update purchase_order set lease_until=now()-interval '1 second' where id=?",
      id
    );
    assertTrue(store.lease(id, newer));
    assertThrows(Fault.class, () ->
      store.advance(id, old, "PENDING", "RESERVED", null)
    );
    store.releaseLease(id, old);
    assertEquals(
      newer,
      ordersDb.queryForObject(
        "select lease_token from purchase_order where id=?",
        UUID.class,
        id
      )
    );
    store.releaseLease(id, newer);
  }

  @Test
  void lastUnitAndSameKeyConcurrencyPreserveBusinessEffects() throws Exception {
    inventoryDb.update(
      "update stock set available=1 where product_id=?",
      UUID.fromString(PRODUCT)
    );
    try (var pool = Executors.newFixedThreadPool(8)) {
      var start = new CountDownLatch(1);
      List<Future<Integer>> pending = new ArrayList<>();
      for (int n = 0; n < 8; n++) pending.add(
        pool.submit(() -> {
          start.await();
          return checkout(UUID.randomUUID().toString(), "APPROVE").statusCode();
        })
      );
      start.countDown();
      var codes = new ArrayList<Integer>();
      for (var f : pending) codes.add(f.get(15, TimeUnit.SECONDS));
      assertEquals(
        1,
        codes
          .stream()
          .filter(c -> c == 201)
          .count()
      );
      assertEquals(
        7,
        codes
          .stream()
          .filter(c -> c == 409)
          .count()
      );
      assertEquals(0, stock());
      assertEquals(1, count(paymentDb, "payment"));
    }
    reset();
    String key = UUID.randomUUID().toString();
    try (var pool = Executors.newFixedThreadPool(8)) {
      var start = new CountDownLatch(1);
      List<Future<Integer>> pending = new ArrayList<>();
      for (int n = 0; n < 8; n++) pending.add(
        pool.submit(() -> {
          start.await();
          return checkout(key, "APPROVE").statusCode();
        })
      );
      start.countDown();
      for (var f : pending)
        assertTrue(Set.of(200, 201, 503).contains(f.get(15, TimeUnit.SECONDS)));
    }
    assertEquals(200, checkout(key, "APPROVE").statusCode());
    assertEquals(19, stock());
    effects(1, 1, 1, 1);
  }

  @Test
  void databaseOwnersCannotConnectToOtherServicesDatabase() {
    var intruder = new JdbcTemplate(
      new DriverManagerDataSource(
        url("orders"),
        "inventory",
        "inventory_test_only"
      )
    );
    assertThrows(org.springframework.dao.DataAccessException.class, () ->
      intruder.queryForObject(
        "select count(*) from purchase_order",
        Integer.class
      )
    );
  }

  @Test
  void correlationAndW3cTraceContextSurviveBothHops() throws Exception {
    String correlation = UUID.randomUUID().toString(),
      trace = "1234567890abcdef1234567890abcdef";
    var request = HttpRequest.newBuilder(URI.create(base + "/api/orders"))
      .header("Content-Type", "application/json")
      .header("Idempotency-Key", UUID.randomUUID().toString())
      .header("X-Correlation-ID", correlation)
      .header("traceparent", "00-" + trace + "-1234567890abcdef-01")
      .POST(HttpRequest.BodyPublishers.ofString(command("APPROVE")))
      .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(201, response.statusCode());
    assertEquals(
      correlation,
      response.headers().firstValue("X-Correlation-ID").orElseThrow()
    );
    assertEquals(
      trace,
      response.headers().firstValue("X-Trace-ID").orElseThrow()
    );
    for (var proxy : List.of(inventoryProxy, paymentProxy)) {
      assertFalse(proxy.seen.isEmpty());
      assertTrue(
        proxy.seen
          .stream()
          .allMatch(
            s ->
              s.correlation().equals(correlation) &&
              s.trace().startsWith("00-" + trace + "-") &&
              s.trace().endsWith("-01")
          )
      );
    }
  }

  @Test
  void publicValidationHistoryAndBuyerScopeAreStable() throws Exception {
    assertEquals(
      400,
      call(base + "/api/orders", "POST", command("APPROVE"), "bad").statusCode()
    );
    assertEquals(
      400,
      call(
        base + "/api/orders",
        "POST",
        command("APPROVE").replace("}", ",\"unitPrice\":\"1.00\"}"),
        UUID.randomUUID().toString()
      ).statusCode()
    );
    effects(0, 0, 0, 0);
    String key = UUID.randomUUID().toString();
    var placed = checkout(key, "DECLINE");
    String id = body(placed).get("id").asText();
    assertEquals(
      "CANCELLED",
      body(call(base + "/api/orders/" + id, "GET", null, null))
        .get("status")
        .asText()
    );
    assertEquals(
      1,
      body(
        call(
          base + "/api/orders/page?status=CANCELLED&limit=1",
          "GET",
          null,
          null
        )
      )
        .get("items")
        .size()
    );
    assertEquals(
      400,
      call(base + "/api/orders/page?limit=51", "GET", null, null).statusCode()
    );
    assertEquals(
      400,
      call(
        base + "/api/orders/page?cursor=broken",
        "GET",
        null,
        null
      ).statusCode()
    );
    ordersDb.update(
      "update purchase_order set buyer_id=? where id=?",
      UUID.randomUUID(),
      UUID.fromString(id)
    );
    assertEquals(
      404,
      call(base + "/api/orders/" + id, "GET", null, null).statusCode()
    );
  }

  @Test
  void versionedPagesContinueWithoutDuplicatesAndRejectOtherFilterCursors()
    throws Exception {
    for (String outcome : List.of("APPROVE", "DECLINE", "APPROVE"))
      assertEquals(
        201,
        checkout(UUID.randomUUID().toString(), outcome).statusCode()
      );
    ordersDb.update(
      "update purchase_order set created_at='2026-01-01T00:00:00Z'"
    );
    Set<String> visited = new HashSet<>();
    String cursor = null;
    int pages = 0;
    do {
      var response = call(
        base +
          "/api/v1/orders/page?limit=1" +
          (cursor == null ? "" : "&cursor=" + cursor),
        "GET",
        null,
        null
      );
      assertEquals(200, response.statusCode());
      var page = body(response);
      assertEquals(1, page.get("items").size());
      assertTrue(visited.add(page.get("items").get(0).get("id").asText()));
      cursor = page.get("nextCursor").isNull()
        ? null
        : page.get("nextCursor").asText();
      assertTrue(++pages <= 3);
      if (pages == 1) assertEquals(
        400,
        call(
          base +
            "/api/v1/orders/page?status=CANCELLED&limit=1&cursor=" +
            cursor,
          "GET",
          null,
          null
        ).statusCode()
      );
    } while (cursor != null);
    assertEquals(3, visited.size());
    visited.clear();
    cursor = null;
    pages = 0;
    do {
      var response = call(
        base +
          "/api/v1/catalog/products/page?limit=1" +
          (cursor == null ? "" : "&cursor=" + cursor),
        "GET",
        null,
        null
      );
      assertEquals(200, response.statusCode());
      var page = body(response);
      assertEquals(1, page.get("items").size());
      assertTrue(visited.add(page.get("items").get(0).get("id").asText()));
      cursor = page.get("nextCursor").isNull()
        ? null
        : page.get("nextCursor").asText();
      assertTrue(++pages <= 3);
    } while (cursor != null);
    assertEquals(3, visited.size());
  }

  @Test
  void offlineBackfillRetainsIdentitiesResumesPartialCopyAndRefusesDrift()
    throws Exception {
    var legacy = database("legacy");
    for (int n = 1; n <= 2; n++) {
      boolean approved = n == 1;
      legacy.update(
        "insert into purchase_order(id,buyer_id,idempotency_key,product_id,product_name,quantity,unit_price,total_amount,currency,requested_payment,payment_status,status,created_at) values (?,'00000000-0000-0000-0000-000000000001',?,'10000000-0000-0000-0000-000000000001','Legacy notebook',1,499,499,'INR',?,?,?,'2026-01-01T00:00:00Z')",
        UUID.fromString("90000000-0000-0000-0000-00000000000" + n),
        UUID.fromString("90000000-0000-0000-0000-00000000010" + n),
        approved ? "APPROVE" : "DECLINE",
        approved ? "AUTHORIZED" : "DECLINED",
        approved ? "CONFIRMED" : "CANCELLED"
      );
    }
    legacy.execute(
      "insert into simulated_payment(order_id,amount,status) select id,total_amount,payment_status from purchase_order"
    );
    legacy.execute(
      "insert into notification_record(order_id,message) select id,status from purchase_order"
    );
    legacy.update(
      "update inventory_stock set available=19 where product_id=?",
      UUID.fromString(PRODUCT)
    );
    UUID custom = UUID.fromString("90000000-0000-0000-0000-000000000999");
    String hostile = "Quoted ', comma, and \"name\"";
    legacy.update(
      "insert into catalog_product(id,name,description,price,currency) values (?,?,?,1.25,'INR')",
      custom,
      hostile,
      "Multiline\ntext, safely copied"
    );
    legacy.update(
      "insert into inventory_stock(product_id,available) values (?,4)",
      custom
    );
    String code = """
      import importlib.util,sys,copy
      spec=importlib.util.spec_from_file_location('migration',sys.argv[1]);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
      source=m.Node(sys.argv[2],'legacy','legacy');nodes={name:m.Node(sys.argv[2],name,name) for name in ('orders','inventory','payment')}
      snapshot=m.export_snapshot(source)
      assert set(m.apply_snapshot(snapshot,nodes).values())=={'copy and verify'}
      assert nodes['orders'].sql('select count(*) from purchase_order')=='0'
      m.apply_owner(snapshot,nodes,'inventory')
      assert m.preflight(snapshot,nodes)['inventory']=='already verified'
      m.apply_snapshot(snapshot,nodes,execute=True)
      assert set(m.apply_snapshot(snapshot,nodes,execute=True).values())=={'already verified'}
      damaged=copy.deepcopy(snapshot);damaged['payload']['stock'][0]['available']=0
      try:m.validate(damaged);raise AssertionError('Checksum modification was accepted')
      except ValueError:pass
      nodes['orders'].sql("update notification_record set message='drift' where order_id='90000000-0000-0000-0000-000000000001'")
      try:m.preflight(snapshot,nodes);raise AssertionError('Changed target was accepted')
      except ValueError:pass
      print('PASS: dry-run, partial-copy resume, exact replay, checksum and target drift guards')
      """;
    var process = new ProcessBuilder(
      System.getenv().getOrDefault(
        "ECAL_PYTHON",
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
          ? "python"
          : "python3"
      ),
      "-c",
      code,
      java.nio.file.Path.of("..", "..", "..", "scripts", "phase3_migrate.py")
        .toAbsolutePath()
        .toString(),
      PG.getContainerId()
    )
      .redirectErrorStream(true)
      .start();
    String output = new String(
      process.getInputStream().readAllBytes(),
      StandardCharsets.UTF_8
    );
    assertEquals(0, process.waitFor(), output);
    assertEquals(19, stock());
    effects(2, 2, 2, 2);
    assertEquals(
      hostile,
      ordersDb.queryForObject(
        "select name from catalog_product where id=?",
        String.class,
        custom
      )
    );
    assertEquals(
      200,
      checkout("90000000-0000-0000-0000-000000000101", "APPROVE").statusCode()
    );
    assertEquals(19, stock());
    effects(2, 2, 2, 2);
    assertEquals(2, count(legacy, "purchase_order"));
  }

  enum Mode {
    BEFORE_503,
    AFTER_503,
    DELAY_AFTER,
    MALFORMED_AFTER,
    ADDITIVE_AFTER,
  }

  record Rule(Predicate<String> matches, Mode mode) {}

  record Seen(String correlation, String trace) {}

  class FaultProxy implements AutoCloseable {

    final HttpServer server;
    final ExecutorService workers = Executors.newFixedThreadPool(16);
    final AtomicReference<Rule> rule = new AtomicReference<>();
    final List<Seen> seen = new CopyOnWriteArrayList<>();
    final String target;

    FaultProxy(String target) throws IOException {
      this.target = target;
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::handle);
      server.setExecutor(workers);
      server.start();
    }

    String url() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void fail(Predicate<String> matches, Mode mode) {
      rule.set(new Rule(matches, mode));
    }

    void handle(HttpExchange e) throws IOException {
      try {
        byte[] data = e.getRequestBody().readAllBytes();
        String path = e.getRequestURI().getPath();
        Rule candidate = rule.get();
        Rule selected = candidate != null &&
          e.getRequestMethod().equals("PUT") &&
          candidate.matches().test(path) &&
          rule.compareAndSet(candidate, null)
          ? candidate
          : null;
        seen.add(
          new Seen(
            e.getRequestHeaders().getFirst("X-Correlation-ID"),
            e.getRequestHeaders().getFirst("traceparent")
          )
        );
        if (selected != null && selected.mode() == Mode.BEFORE_503) {
          reply(e, 503, "{}");
          return;
        }
        var b = HttpRequest.newBuilder(
          URI.create(target + e.getRequestURI())
        ).timeout(Duration.ofSeconds(5));
        for (String header : List.of(
          "Content-Type",
          "X-Correlation-ID",
          "traceparent"
        )) {
          String value = e.getRequestHeaders().getFirst(header);
          if (value != null) b.header(header, value);
        }
        var upstream = http.send(
          b
            .method(
              e.getRequestMethod(),
              data.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(data)
            )
            .build(),
          HttpResponse.BodyHandlers.ofString()
        );
        if (selected != null && selected.mode() == Mode.AFTER_503) {
          reply(e, 503, "{}");
          return;
        }
        if (
          selected != null && selected.mode() == Mode.DELAY_AFTER
        ) Thread.sleep(1500);
        if (selected != null && selected.mode() == Mode.MALFORMED_AFTER) {
          reply(e, 200, "{}");
          return;
        }
        String result = upstream.body();
        if (selected != null && selected.mode() == Mode.ADDITIVE_AFTER) {
          var parsed = (tools.jackson.databind.node.ObjectNode) json.readTree(
            result
          );
          parsed.put("futureOptionalField", "accepted");
          result = json.writeValueAsString(parsed);
        }
        reply(e, upstream.statusCode(), result);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } finally {
        e.close();
      }
    }

    void reply(HttpExchange e, int status, String value) throws IOException {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      e.getResponseHeaders().set("Content-Type", "application/json");
      e.sendResponseHeaders(status, bytes.length);
      e.getResponseBody().write(bytes);
    }

    public void close() {
      server.stop(0);
      workers.shutdownNow();
    }
  }
}
