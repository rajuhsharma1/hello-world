package com.ecal.distributed.system;

import static org.junit.jupiter.api.Assertions.*;

import com.ecal.distributed.inventory.InventoryApplication;
import com.ecal.distributed.orders.*;
import com.ecal.distributed.payment.PaymentApplication;
import com.ecal.distributed.reporting.*;
import com.ecal.distributed.support.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real Kafka, four separately credentialed databases, and HTTP commands. Schedulers are controlled
 * so failures occur at the intended boundary, not in a race with a background test thread. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventWorkflowIT {

  @Container
  static final PostgreSQLContainer PG = new PostgreSQLContainer(
    "postgres:17.6"
  );

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1")
    .withEnv("KAFKA_LOG_SEGMENT_BYTES", "1048576")
    .withEnv("KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS", "1")
    .withEnv("KAFKA_OFFSETS_TOPIC_SEGMENT_BYTES", "1048576");

  static final UUID PRODUCT = UUID.fromString(
    "10000000-0000-0000-0000-000000000001"
  );
  final Map<String, ConfigurableApplicationContext> apps =
    new LinkedHashMap<>();
  final Map<String, JdbcTemplate> databases = new HashMap<>();
  final HttpClient http = HttpClient.newHttpClient();
  String base;

  String url(String name) {
    return (
      "jdbc:postgresql://" +
      PG.getHost() +
      ":" +
      PG.getMappedPort(5432) +
      "/" +
      name
    );
  }

  int port(ConfigurableApplicationContext c) {
    return ((WebServerApplicationContext) c).getWebServer().getPort();
  }

  ConfigurableApplicationContext start(
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
        "--spring.datasource.password=" + name,
        "--ecal.events-enabled=true",
        "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
        "--ecal.worker-initial-delay-ms=3600000",
        "--spring.main.banner-mode=off",
        "--spring.kafka.consumer.properties.max.poll.interval.ms=600000"
      )
    );
    if (!name.equals("reporting")) args.add(
      "--spring.flyway.locations=classpath:db/" +
        name +
        ",classpath:db/events,classpath:db/events-" +
        name
    );
    for (String option : extra) {
      String key = option.substring(0, option.indexOf("=") + 1);
      args.removeIf(a -> a.startsWith(key));
      args.add(option);
    }
    var app = new SpringApplicationBuilder(type).run(
      args.toArray(String[]::new)
    );
    apps.put(name, app);
    return app;
  }

  @BeforeAll
  void start() {
    var admin = new JdbcTemplate(
      new DriverManagerDataSource(
        PG.getJdbcUrl(),
        PG.getUsername(),
        PG.getPassword()
      )
    );
    for (String name : List.of("orders", "inventory", "payment", "reporting")) {
      admin.execute("create role " + name + " login password '" + name + "'");
      admin.execute("create database " + name + " owner " + name);
      admin.execute("revoke connect on database " + name + " from public");
      databases.put(
        name,
        new JdbcTemplate(new DriverManagerDataSource(url(name), name, name))
      );
    }
    var inv = start(InventoryApplication.class, "inventory");
    var pay = start(PaymentApplication.class, "payment");
    start(
      OrdersApplication.class,
      "orders",
      "--ecal.inventory-url=http://127.0.0.1:" + port(inv),
      "--ecal.payment-url=http://127.0.0.1:" + port(pay)
    );
    start(ReportingApplication.class, "reporting");
    base = "http://127.0.0.1:" + port(apps.get("orders"));
  }

  @AfterAll
  void stop() {
    var list = new ArrayList<>(apps.values());
    Collections.reverse(list);
    list.forEach(ConfigurableApplicationContext::close);
  }

  JdbcTemplate db(String name) {
    return databases.get(name);
  }

  long count(String name, String sql, Object... args) {
    return db(name).queryForObject(sql, Long.class, args);
  }

  HttpResponse<String> post(UUID key, String outcome) throws Exception {
    return http.send(
      HttpRequest.newBuilder(URI.create(base + "/api/v1/orders"))
        .header("Content-Type", "application/json")
        .header("Idempotency-Key", key.toString())
        .header("X-Correlation-ID", "12345678-1234-1234-1234-123456789abc")
        .POST(
          HttpRequest.BodyPublishers.ofString(
            "{\"productId\":\"" +
              PRODUCT +
              "\",\"quantity\":1,\"paymentOutcome\":\"" +
              outcome +
              "\"}"
          )
        )
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  UUID id(HttpResponse<String> response) {
    return UUID.fromString(
      EventEnvelope.JSON.readTree(response.body()).path("id").asText()
    );
  }

  void until(BooleanSupplier condition) {
    long end = System.nanoTime() + Duration.ofSeconds(25).toNanos();
    while (System.nanoTime() < end) {
      if (condition.getAsBoolean()) return;
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    assertTrue(
      condition.getAsBoolean(),
      "Timed out waiting for durable asynchronous progress"
    );
  }

  void drain(String owner) {
    var relay = apps.get(owner).getBean(EventPlatform.Relay.class);
    while (relay.publishOne(false)) {}
  }

  void work(UUID id) {
    drain("orders");
    until(
      () ->
        count(
          "orders",
          "select count(*) from saga_work where order_id=?",
          id
        ) ==
        1
    );
    apps.get("orders").getBean(AsyncSaga.class).tick();
  }

  String state(UUID id) {
    return db("orders").queryForObject(
      "select status from purchase_order where id=?",
      String.class,
      id
    );
  }

  @Test
  @Order(1)
  void acceptanceThenAutonomousExecutionAndEventualProjection()
    throws Exception {
    var key = UUID.randomUUID();
    var response = post(key, "APPROVE");
    UUID id = id(response);
    assertEquals(202, response.statusCode());
    assertEquals("PENDING", state(id));
    assertEquals(
      0,
      count(
        "inventory",
        "select count(*) from reservation where order_id=?",
        id
      )
    );
    assertEquals(
      1,
      count(
        "orders",
        "select count(*) from event_outbox where aggregate_id=?",
        id
      )
    );
    assertEquals(response.body(), post(key, "APPROVE").body());
    assertEquals(
      1,
      count(
        "orders",
        "select count(*) from event_outbox where aggregate_id=?",
        id
      ),
      "Replaying an existing checkout must not emit another creation event"
    );
    work(id);
    assertEquals("CONFIRMED", state(id));
    assertEquals(
      0,
      count(
        "reporting",
        "select count(*) from order_projection where order_id=? and status='CONFIRMED'",
        id
      ),
      "Projection has not consumed terminal outbox yet"
    );
    drain("orders");
    drain("inventory");
    drain("payment");
    until(
      () ->
        count(
          "reporting",
          "select count(*) from order_projection where order_id=? and status='CONFIRMED'",
          id
        ) ==
        1
    );
    assertEquals(
      1,
      count(
        "inventory",
        "select count(*) from reservation where order_id=?",
        id
      )
    );
    assertEquals(
      1,
      count("payment", "select count(*) from payment where order_id=?", id)
    );
    assertEquals(
      1,
      count(
        "orders",
        "select count(*) from event_outbox where aggregate_id=? and event_type='OrderConfirmed'",
        id
      )
    );
    assertEquals(
      1,
      count(
        "inventory",
        "select count(*) from event_outbox where aggregate_id=? and event_type='InventoryReserved'",
        id
      )
    );
    assertEquals(
      1,
      count(
        "payment",
        "select count(*) from event_outbox where aggregate_id=? and event_type='PaymentAuthorized'",
        id
      )
    );
    assertEquals(
      "12345678-1234-1234-1234-123456789abc",
      db("payment").queryForObject(
        "select correlation_id from event_outbox where aggregate_id=?",
        String.class,
        id
      )
    );
  }

  @Test
  @Order(2)
  void relayCrashAfterBrokerCommitRedeliversWithoutAnotherEffect()
    throws Exception {
    var response = post(UUID.randomUUID(), "APPROVE");
    UUID id = id(response);
    var relay = apps.get("orders").getBean(EventPlatform.Relay.class);
    assertThrows(IllegalStateException.class, () -> relay.publishOne(true));
    until(
      () ->
        count(
          "orders",
          "select count(*) from saga_work where order_id=?",
          id
        ) ==
        1
    );
    assertEquals(
      1,
      count(
        "orders",
        "select count(*) from event_outbox where aggregate_id=? and published_at is null",
        id
      )
    );
    drain("orders");
    apps.get("orders").getBean(AsyncSaga.class).tick();
    assertEquals("CONFIRMED", state(id));
    drain("orders");
    until(
      () ->
        count(
          "reporting",
          "select count(*) from order_projection where order_id=? and status='CONFIRMED'",
          id
        ) ==
        1
    );
    assertEquals(
      1,
      count("payment", "select count(*) from payment where order_id=?", id)
    );
  }

  @Test
  @Order(3)
  void paymentOutageResumesWithoutAnyClientReplay() throws Exception {
    int stock = db("inventory").queryForObject(
      "select available from stock where product_id=?",
      Integer.class,
      PRODUCT
    );
    var payment = apps.get("payment");
    payment.close();
    UUID id = id(post(UUID.randomUUID(), "DECLINE"));
    work(id);
    assertEquals("PAYMENT_UNKNOWN", state(id));
    assertEquals(
      1,
      count("orders", "select count(*) from saga_work where order_id=?", id)
    );
    // Restart the owner on its original HTTP port, preserving its database and ledger.
    start(
      PaymentApplication.class,
      "payment",
      "--server.port=" + portOfClosedPayment(payment)
    );
    db("orders").update(
      "update saga_work set next_attempt_at=now() where order_id=?",
      id
    );
    apps.get("orders").getBean(AsyncSaga.class).tick();
    assertEquals("CANCELLED", state(id));
    assertEquals(
      stock,
      db("inventory").queryForObject(
        "select available from stock where product_id=?",
        Integer.class,
        PRODUCT
      )
    );
    assertEquals(
      1,
      count("payment", "select count(*) from payment where order_id=?", id)
    );
    drain("orders");
    drain("inventory");
    drain("payment");
    until(
      () ->
        count(
          "reporting",
          "select count(*) from order_projection where order_id=? and status='CANCELLED'",
          id
        ) ==
        1
    );
  }

  int oldPaymentPort;

  int portOfClosedPayment(ConfigurableApplicationContext ignored) {
    return oldPaymentPort;
  }

  @BeforeEach
  void rememberPaymentPort() {
    oldPaymentPort = port(apps.get("payment"));
  }

  EventEnvelope snapshot(UUID id, long version, String status) {
    String payload =
      "{\"id\":\"" +
      id +
      "\",\"productId\":\"" +
      PRODUCT +
      "\",\"quantity\":1,\"status\":\"" +
      status +
      "\",\"totalAmount\":\"499.00\",\"currency\":\"INR\",\"createdAt\":\"2026-10-23T00:00:00Z\"}";
    return new EventEnvelope(
      UUID.randomUUID(),
      "orders",
      id,
      version,
      1,
      status.equals("CONFIRMED") ? "OrderConfirmed" : "OrderProgress",
      Instant.now(),
      null,
      null,
      EventEnvelope.JSON.readTree(payload)
    );
  }

  ConsumerRecord<String, String> record(EventEnvelope e) {
    return new ConsumerRecord<>(
      EventPlatform.TOPIC,
      0,
      0,
      e.aggregateId().toString(),
      EventEnvelope.JSON.writeValueAsString(e)
    );
  }

  @Test
  @Order(4)
  void consumerCrashAfterDbCommitBeforeOffsetAckIsSafe() {
    var reports = apps.get("reporting").getBean(Reports.class);
    var e = snapshot(UUID.randomUUID(), 4, "CONFIRMED");
    assertThrows(IllegalStateException.class, () ->
      reports.event(record(e), () -> {
        throw new IllegalStateException("Simulated crash before offset commit");
      })
    );
    assertEquals(
      1,
      count(
        "reporting",
        "select count(*) from order_projection where order_id=?",
        e.aggregateId()
      )
    );
    var ack = new AtomicBoolean();
    reports.event(record(e), () -> ack.set(true));
    assertTrue(ack.get());
    assertEquals(
      1,
      count(
        "reporting",
        "select count(*) from event_inbox where event_id=?",
        e.eventId()
      )
    );
  }

  @Test
  @Order(5)
  void poisonAndFutureSchemasAreDurablyQuarantinedAndDoNotBlockNextRecord() {
    var reports = apps.get("reporting").getBean(Reports.class);
    long before = count("reporting", "select count(*) from event_quarantine");
    var ack = new AtomicBoolean();
    reports.event(
      new ConsumerRecord<>(EventPlatform.TOPIC, 0, 1, "bad", "{broken"),
      () -> ack.set(true)
    );
    assertTrue(ack.get());
    var e = snapshot(UUID.randomUUID(), 4, "CONFIRMED");
    String future = EventEnvelope.JSON.writeValueAsString(e).replace(
      "\"schemaVersion\":1",
      "\"schemaVersion\":2"
    );
    reports.event(
      new ConsumerRecord<>(
        EventPlatform.TOPIC,
        0,
        2,
        e.aggregateId().toString(),
        future
      ),
      () -> {}
    );
    reports.event(
      new ConsumerRecord<>(EventPlatform.TOPIC, 0, 3, "tombstone", null),
      () -> {}
    );
    assertEquals(
      before + 3,
      count("reporting", "select count(*) from event_quarantine")
    );
    reports.event(record(e), () -> {});
    assertEquals(
      1,
      count(
        "reporting",
        "select count(*) from order_projection where order_id=?",
        e.aggregateId()
      )
    );
  }

  @Test
  @Order(6)
  void newerSnapshotThenOldVersionNeverRegressesAndAdditiveFieldsWork() {
    var reports = apps.get("reporting").getBean(Reports.class);
    UUID id = UUID.randomUUID();
    var newer = snapshot(id, 4, "CONFIRMED");
    var older = snapshot(id, 2, "RESERVED");
    reports.event(record(newer), () -> {});
    var raw = EventEnvelope.JSON.writeValueAsString(older).replace(
      "\"schemaVersion\":1",
      "\"extraFutureField\":true,\"schemaVersion\":1"
    );
    reports.event(
      new ConsumerRecord<>(EventPlatform.TOPIC, 0, 3, id.toString(), raw),
      () -> {}
    );
    assertEquals(
      "CONFIRMED",
      db("reporting").queryForObject(
        "select status from order_projection where order_id=?",
        String.class,
        id
      )
    );
    assertEquals(
      4,
      count(
        "reporting",
        "select aggregate_version from order_projection where order_id=?",
        id
      )
    );
  }

  @Test
  @Order(7)
  void handlerDatabaseFailureRollsBackInboxAndDoesNotAck() {
    var inbox = apps.get("reporting").getBean(EventInbox.class);
    var e = snapshot(UUID.randomUUID(), 4, "CONFIRMED");
    assertThrows(IllegalStateException.class, () ->
      inbox.receive(
        e.aggregateId().toString(),
        EventEnvelope.JSON.writeValueAsString(e),
        ignored -> {
          throw new IllegalStateException("Database unavailable");
        }
      )
    );
    assertEquals(
      0,
      count(
        "reporting",
        "select count(*) from event_inbox where event_id=?",
        e.eventId()
      )
    );
    apps
      .get("reporting")
      .getBean(Reports.class)
      .event(record(e), () -> {});
    assertEquals(
      1,
      count(
        "reporting",
        "select count(*) from event_inbox where event_id=?",
        e.eventId()
      )
    );
  }

  @Test
  @Order(8)
  void outboxInsertFailureRollsBackOrderAndProjectionCannotAccessOperationalDb() {
    db("orders").execute(
      "create function deny_outbox() returns trigger language plpgsql as $$begin raise exception 'test-only outbox failure'; end $$"
    );
    db("orders").execute(
      "create trigger deny_outbox before insert on event_outbox for each row execute function deny_outbox()"
    );
    UUID key = UUID.randomUUID();
    try {
      assertThrows(RuntimeException.class, () ->
        apps
          .get("orders")
          .getBean(OrderStore.class)
          .claim(key, new OrderStore.Command(PRODUCT, 1, "APPROVE"))
      );
    } finally {
      db("orders").execute("drop trigger deny_outbox on event_outbox");
      db("orders").execute("drop function deny_outbox()");
    }
    assertEquals(
      0,
      count(
        "orders",
        "select count(*) from purchase_order where idempotency_key=?",
        key
      )
    );
    assertThrows(Exception.class, () ->
      new DriverManagerDataSource(
        url("orders"),
        "reporting",
        "reporting"
      ).getConnection()
    );
  }

  @Test
  @Order(9)
  void brokerOutageLeavesDurableAcceptanceAndRelayRecovers() throws Exception {
    UUID id;
    var docker = org.testcontainers.DockerClientFactory.instance().client();
    docker.pauseContainerCmd(KAFKA.getContainerId()).exec();
    try {
      var response = post(UUID.randomUUID(), "APPROVE");
      assertEquals(202, response.statusCode());
      id = id(response);
      assertThrows(IllegalStateException.class, () ->
        apps.get("orders").getBean(EventPlatform.Relay.class).publishOne(false)
      );
      assertEquals(
        1,
        count(
          "orders",
          "select count(*) from event_outbox where aggregate_id=? and published_at is null",
          id
        )
      );
    } finally {
      docker.unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }
    work(id);
    assertEquals("CONFIRMED", state(id));
    drain("orders");
    until(
      () ->
        count(
          "reporting",
          "select count(*) from order_projection where order_id=? and status='CONFIRMED'",
          id
        ) ==
        1
    );
  }

  @Test
  @Order(10)
  void ordersRestartRecoversCommittedAcceptanceWithoutClientReplay()
    throws Exception {
    UUID id = id(post(UUID.randomUUID(), "APPROVE"));
    apps.get("orders").close();
    start(
      OrdersApplication.class,
      "orders",
      "--ecal.inventory-url=http://127.0.0.1:" + port(apps.get("inventory")),
      "--ecal.payment-url=http://127.0.0.1:" + port(apps.get("payment"))
    );
    base = "http://127.0.0.1:" + port(apps.get("orders"));
    work(id);
    assertEquals("CONFIRMED", state(id));
  }
}
