package com.ecal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecal.inventory.api.Inventory;
import com.ecal.notification.api.Notifications;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs against a disposable PostgreSQL 17.6, not H2. HTTP requests cross real Spring transaction proxies. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutIntegrationTestIT {

  private static final String PRODUCT = "10000000-0000-0000-0000-000000000001";

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
    "postgres:17.6"
  );

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort
  int port;

  @Autowired
  JdbcTemplate db;

  @Autowired
  Inventory inventory;

  @MockitoSpyBean
  Notifications notifications;

  private final HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build();
  private final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void resetDatabase() {
    reset(
      (Notifications) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(
        notifications
      )
    );
    db.execute("truncate notification_record,simulated_payment,purchase_order");
    db.update("update inventory_stock set available=20");
  }

  private String command(int qty, String outcome) {
    return (
      "{\"productId\":\"" +
      PRODUCT +
      "\",\"quantity\":" +
      qty +
      ",\"paymentOutcome\":\"" +
      outcome +
      "\"}"
    );
  }

  private HttpResponse<String> post(String key, String body) throws Exception {
    var r = HttpRequest.newBuilder(
      URI.create("http://localhost:" + port + "/api/orders")
    )
      .timeout(Duration.ofSeconds(20))
      .header("Content-Type", "application/json");
    if (key != null) r.header("Idempotency-Key", key);
    return client.send(
      r.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(
      HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .GET()
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  private long count(String table) {
    return db.queryForObject("select count(*) from " + table, Long.class);
  }

  private int stock() {
    return db.queryForObject(
      "select available from inventory_stock where product_id=?",
      Integer.class,
      UUID.fromString(PRODUCT)
    );
  }

  @Test
  void approvedOrderPersistsExactServerPriceAndIsVisibleInStatusAndHistory()
    throws Exception {
    var r = post(UUID.randomUUID().toString(), command(2, "APPROVE"));
    assertEquals(201, r.statusCode(), r.body());
    var order = json.readTree(r.body());
    assertEquals("998.00", order.path("totalAmount").asText());
    assertEquals("CONFIRMED", order.path("status").asText());
    assertEquals("AUTHORIZED", order.path("paymentStatus").asText());
    assertEquals(18, stock());
    assertEquals(
      order,
      json.readTree(
        get(r.headers().firstValue("Location").orElseThrow()).body()
      )
    );
    assertEquals(order, json.readTree(get("/api/orders").body()).get(0));
    assertEquals(1, count("simulated_payment"));
    assertEquals(1, count("notification_record"));
  }

  @Test
  void exactReplayIsIdenticalIncludingTimestampAndHasNoRepeatedEffects()
    throws Exception {
    var key = UUID.randomUUID().toString();
    var first = post(key, command(1, "APPROVE"));
    var replay = post(key, command(1, "APPROVE"));
    assertEquals(201, first.statusCode());
    assertEquals(200, replay.statusCode());
    assertEquals(json.readTree(first.body()), json.readTree(replay.body()));
    assertEquals(19, stock());
    assertEquals(1, count("purchase_order"));
    assertEquals(1, count("simulated_payment"));
    assertEquals(1, count("notification_record"));
  }

  @Test
  void changedPayloadUnderAnExistingKeyIsRejected() throws Exception {
    var key = UUID.randomUUID().toString();
    assertEquals(201, post(key, command(1, "APPROVE")).statusCode());
    var r = post(key, command(2, "APPROVE"));
    assertEquals(409, r.statusCode());
    assertEquals(
      "IDEMPOTENCY_CONFLICT",
      json.readTree(r.body()).path("code").asText()
    );
    assertEquals(19, stock());
    assertEquals(1, count("simulated_payment"));
  }

  @Test
  void declinedPaymentReleasesReservationAndReplaysItsCancellation()
    throws Exception {
    var key = UUID.randomUUID().toString();
    var first = post(key, command(3, "DECLINE"));
    assertEquals(201, first.statusCode());
    assertEquals(
      "CANCELLED",
      json.readTree(first.body()).path("status").asText()
    );
    assertEquals(
      "DECLINED",
      json.readTree(first.body()).path("paymentStatus").asText()
    );
    assertEquals(20, stock());
    assertEquals(200, post(key, command(3, "DECLINE")).statusCode());
    assertEquals(1, count("simulated_payment"));
    assertEquals(1, count("notification_record"));
  }

  @Test
  void insufficientStockRollsBackClaimAndAllowsTheSameKeyAfterReplenishment()
    throws Exception {
    db.update(
      "update inventory_stock set available=0 where product_id=?",
      UUID.fromString(PRODUCT)
    );
    var key = UUID.randomUUID().toString();
    var r = post(key, command(1, "APPROVE"));
    assertEquals(409, r.statusCode());
    assertEquals(
      "INSUFFICIENT_STOCK",
      json.readTree(r.body()).path("code").asText()
    );
    assertEquals(0, count("purchase_order"));
    assertEquals(0, count("simulated_payment"));
    db.update(
      "update inventory_stock set available=1 where product_id=?",
      UUID.fromString(PRODUCT)
    );
    assertEquals(201, post(key, command(1, "APPROVE")).statusCode());
    assertEquals(0, stock());
  }

  @Test
  void concurrentDistinctOrdersCannotOversellTheLastUnit() throws Exception {
    db.update(
      "update inventory_stock set available=1 where product_id=?",
      UUID.fromString(PRODUCT)
    );
    var responses = race(8, false);
    assertEquals(
      1,
      responses
        .stream()
        .filter(r -> r.statusCode() == 201)
        .count()
    );
    assertEquals(
      7,
      responses
        .stream()
        .filter(r -> r.statusCode() == 409)
        .count()
    );
    assertEquals(0, stock());
    assertEquals(1, count("purchase_order"));
    assertEquals(1, count("simulated_payment"));
  }

  @Test
  void concurrentSameKeyHasOneCommitAndSevenExactReplays() throws Exception {
    var responses = race(8, true);
    assertEquals(
      1,
      responses
        .stream()
        .filter(r -> r.statusCode() == 201)
        .count()
    );
    assertEquals(
      7,
      responses
        .stream()
        .filter(r -> r.statusCode() == 200)
        .count()
    );
    JsonNode expected = json.readTree(responses.getFirst().body());
    for (var r : responses) assertEquals(expected, json.readTree(r.body()));
    assertEquals(19, stock());
    assertEquals(1, count("purchase_order"));
    assertEquals(1, count("simulated_payment"));
    assertEquals(1, count("notification_record"));
  }

  private List<HttpResponse<String>> race(int threads, boolean sameKey)
    throws Exception {
    var start = new CountDownLatch(1);
    var ready = new CountDownLatch(threads);
    var shared = UUID.randomUUID().toString();
    try (var pool = Executors.newFixedThreadPool(threads)) {
      List<Future<HttpResponse<String>>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) futures.add(
        pool.submit(() -> {
          ready.countDown();
          if (
            !start.await(5, TimeUnit.SECONDS)
          ) throw new IllegalStateException("Race barrier timeout");
          return post(
            sameKey ? shared : UUID.randomUUID().toString(),
            command(1, "APPROVE")
          );
        })
      );
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<HttpResponse<String>> result = new ArrayList<>();
      for (var f : futures) result.add(f.get(25, TimeUnit.SECONDS));
      return result;
    } finally {
      start.countDown();
    }
  }

  @Test
  void notificationFailureRollsBackEvenTheSimulatedPaymentAndStock()
    throws Exception {
    // Stub the spy target, not the Spring proxy: configuring the proxy would invoke MANDATORY advice without a transaction.
    doThrow(new IllegalStateException("Deterministic local stub failure"))
      .when(
        (Notifications) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(
          notifications
        )
      )
      .record(any(UUID.class), anyString());
    var key = UUID.randomUUID().toString();
    var r = post(key, command(2, "APPROVE"));
    assertEquals(500, r.statusCode());
    assertEquals(
      "INTERNAL_ERROR",
      json.readTree(r.body()).path("code").asText()
    );
    assertEquals(20, stock());
    assertEquals(0, count("purchase_order"));
    assertEquals(0, count("simulated_payment"));
    assertEquals(0, count("notification_record"));
    reset(
      (Notifications) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(
        notifications
      )
    );
    assertEquals(201, post(key, command(2, "APPROVE")).statusCode());
    assertEquals(18, stock());
    assertEquals(1, count("simulated_payment"));
  }

  @Test
  void inventoryCannotBeMutatedOutsideTheCheckoutTransaction() {
    assertThrows(IllegalTransactionStateException.class, () ->
      inventory.reserve(UUID.fromString(PRODUCT), 1)
    );
    assertEquals(20, stock());
  }

  @Test
  void invalidInputsAndClientPriceAreProblemDetailsWithoutEffects()
    throws Exception {
    for (String body : List.of(
      command(0, "APPROVE"),
      command(11, "APPROVE"),
      command(1, "REAL_PAYMENT"),
      command(1, "APPROVE").replace("}", ",\"unitPrice\":\"0.01\"}")
    )) {
      var r = post(UUID.randomUUID().toString(), body);
      assertEquals(400, r.statusCode(), r.body());
      assertTrue(
        r
          .headers()
          .firstValue("content-type")
          .orElse("")
          .contains("application/problem+json")
      );
      assertEquals(
        "INVALID_REQUEST",
        json.readTree(r.body()).path("code").asText()
      );
    }
    assertEquals(400, post(null, command(1, "APPROVE")).statusCode());
    assertEquals(400, post("bad-key", command(1, "APPROVE")).statusCode());
    assertEquals(0, count("purchase_order"));
    assertEquals(20, stock());
  }

  @Test
  void databaseRejectsMissingTerminalPaymentAndNegativeStock()
    throws Exception {
    var response = post(UUID.randomUUID().toString(), command(1, "APPROVE"));
    assertEquals(201, response.statusCode());
    assertThrows(
      org.springframework.dao.DataIntegrityViolationException.class,
      () -> db.update("update purchase_order set payment_status=null")
    );
    assertThrows(
      org.springframework.dao.DataIntegrityViolationException.class,
      () ->
        db.update(
          "update inventory_stock set available=-1 where product_id=?",
          UUID.fromString(PRODUCT)
        )
    );
    assertEquals(19, stock());
    assertEquals(
      "AUTHORIZED",
      db.queryForObject(
        "select payment_status from purchase_order",
        String.class
      )
    );
  }

  @Test
  void unsupportedHttpRequestsRemainClientErrors() throws Exception {
    assertEquals(404, get("/api/not-a-resource").statusCode());
    var wrongMethod = HttpRequest.newBuilder(
      URI.create("http://localhost:" + port + "/api/catalog/products")
    )
      .POST(HttpRequest.BodyPublishers.noBody())
      .build();
    assertEquals(
      405,
      client
        .send(wrongMethod, HttpResponse.BodyHandlers.ofString())
        .statusCode()
    );
    var wrongType = HttpRequest.newBuilder(
      URI.create("http://localhost:" + port + "/api/orders")
    )
      .header("Idempotency-Key", UUID.randomUUID().toString())
      .header("Content-Type", "text/plain")
      .POST(HttpRequest.BodyPublishers.ofString(command(1, "APPROVE")))
      .build();
    assertEquals(
      415,
      client.send(wrongType, HttpResponse.BodyHandlers.ofString()).statusCode()
    );
    assertEquals(0, count("purchase_order"));
  }

  @Test
  void missingResourcesReturn404AndCatalogContractUsesDecimalStrings()
    throws Exception {
    var catalog = get("/api/catalog/products");
    assertEquals(200, catalog.statusCode());
    var product = json.readTree(catalog.body()).get(0);
    assertTrue(product.path("price").isString());
    assertTrue(product.has("availableStock"));
    assertEquals("INR", product.path("currency").asText());
    assertEquals(404, get("/api/orders/" + UUID.randomUUID()).statusCode());
    assertEquals(
      404,
      post(
        UUID.randomUUID().toString(),
        command(1, "APPROVE").replace(PRODUCT, UUID.randomUUID().toString())
      ).statusCode()
    );
    assertEquals(0, count("purchase_order"));
  }
}
