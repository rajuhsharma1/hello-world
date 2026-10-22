package com.ecal;

import static org.junit.jupiter.api.Assertions.*;

import com.ecal.catalog.internal.importing.CatalogImportService;
import com.ecal.shared.PageCursor;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL proves actual transaction boundaries and durable replay; mocks cannot prove rollback. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PhaseTwoIntegrationTestIT {

  static final java.util.concurrent.atomic.AtomicInteger SELECTS =
    new java.util.concurrent.atomic.AtomicInteger();

  /** Test-only instrumentation at JDBC's physical prepare boundary, below repository methods. */
  @org.springframework.boot.test.context.TestConfiguration(
    proxyBeanMethods = false
  )
  static class QueryCounterConfiguration {

    @org.springframework.context.annotation.Bean
    static org.springframework.beans.factory.config.BeanPostProcessor countSelects() {
      return new org.springframework.beans.factory.config.BeanPostProcessor() {
        public Object postProcessAfterInitialization(Object bean, String name) {
          if (!(bean instanceof javax.sql.DataSource)) return bean;
          return java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { javax.sql.DataSource.class, AutoCloseable.class },
            (proxy, method, args) -> {
              try {
                Object value = method.invoke(bean, args);
                if (
                  value instanceof java.sql.Connection connection
                ) return java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] { java.sql.Connection.class },
                  (p, m, a) -> {
                    if (
                      m.getName().equals("prepareStatement") &&
                      a[0] instanceof String sql &&
                      sql
                        .stripLeading()
                        .toLowerCase(java.util.Locale.ROOT)
                        .startsWith("select")
                    ) SELECTS.incrementAndGet();
                    try {
                      return m.invoke(connection, a);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                      throw e.getCause();
                    }
                  }
                );
                return value;
              } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
              }
            }
          );
        }
      };
    }
  }

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

  @Autowired
  JdbcTemplate db;

  @Autowired
  CatalogImportService imports;

  @LocalServerPort
  int port;

  final HttpClient http = HttpClient.newHttpClient();
  final JsonMapper json = JsonMapper.builder().build();
  final String header = "productId,name,description,price,currency,stock\r\n";

  @BeforeEach
  void clean() {
    db.execute(
      "truncate catalog_import_product,notification_record,simulated_payment,purchase_order"
    );
    db.update(
      "delete from inventory_stock where product_id::text like '20000000%'"
    );
    db.update("delete from catalog_product where id::text like '20000000%'");
  }

  UUID id(int n) {
    return UUID.fromString(
      "20000000-0000-0000-0000-" + String.format("%012d", n)
    );
  }

  String row(int n, String price) {
    return (
      id(n) +
      ",Imported " +
      String.format("%04d", n) +
      ",Description," +
      price +
      ",INR,20\r\n"
    );
  }

  CatalogImportService.Result upload(String csv) {
    return imports.run(
      new MockMultipartFile(
        "file",
        "test.csv",
        "text/csv",
        csv.getBytes(StandardCharsets.UTF_8)
      )
    );
  }

  HttpResponse<String> get(String path) throws Exception {
    return http.send(
      HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .GET()
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  @Test
  void commitsAndReplaysWithoutRestoringConsumedStock() {
    assertEquals(1, upload(header + row(1, "12.50")).created());
    db.update(
      "update inventory_stock set available=7 where product_id=?",
      id(1)
    );
    assertEquals(1, upload(header + row(1, "12.50")).skipped());
    assertEquals(
      7,
      db.queryForObject(
        "select available from inventory_stock where product_id=?",
        Integer.class,
        id(1)
      )
    );
    assertEquals(1, upload(header + row(1, "13.00")).failedBatches());
    assertEquals(
      "12.50",
      db.queryForObject(
        "select price::text from catalog_product where id=?",
        String.class,
        id(1)
      )
    );
  }

  @Test
  void aFailedBatchRollsBackItsEarlierWritesAndOtherBatchesCommit() {
    var csv = new StringBuilder(header);
    for (int n = 1; n <= 52; n++) csv.append(
      row(n, n == 52 ? "-1.00" : "10.00")
    );
    var first = upload(csv.toString());
    assertEquals(50, first.created());
    assertEquals(1, first.failedBatches());
    assertEquals(
      0,
      db.queryForObject(
        "select count(*) from catalog_product where id=?",
        Integer.class,
        id(51)
      )
    );
    var replay = upload(csv.toString());
    assertEquals(50, replay.skipped());
    assertEquals(1, replay.failedBatches());
    var corrected = upload(csv.toString().replace("-1.00", "11.00"));
    assertEquals(50, corrected.skipped());
    assertEquals(2, corrected.created());
    assertEquals(0, corrected.failedBatches());
  }

  @Test
  void malformedFileAndDuplicateIdsMakeNoWrites() {
    assertThrows(IllegalArgumentException.class, () ->
      upload("wrong\r\nvalue\r\n")
    );
    assertThrows(IllegalArgumentException.class, () ->
      upload(header + row(1, "1.00") + row(1, "1.00"))
    );
    assertThrows(IllegalArgumentException.class, () ->
      upload(header + "bad-id,Name,Description,1,INR,1\r\n")
    );
    assertEquals(
      0,
      db.queryForObject(
        "select count(*) from catalog_import_product",
        Integer.class
      )
    );
  }

  @Test
  void malformedUtf8AndUnclosedQuotesRejectBeforeWriting() {
    byte[] prefix = header.getBytes(StandardCharsets.UTF_8);
    byte[] broken = java.util.Arrays.copyOf(prefix, prefix.length + 2);
    broken[prefix.length] = (byte) 0xc3;
    broken[prefix.length + 1] = (byte) 0x28;
    assertThrows(IllegalArgumentException.class, () ->
      imports.run(
        new MockMultipartFile("file", "broken.csv", "text/csv", broken)
      )
    );
    assertThrows(IllegalArgumentException.class, () ->
      upload(header + id(1) + ",\"unclosed name")
    );
    assertEquals(
      0,
      db.queryForObject(
        "select count(*) from catalog_import_product",
        Integer.class
      )
    );
  }

  @Test
  void quotedCommasAndMultilineDescriptionsAreRealCsv() {
    assertEquals(
      1,
      upload(
        header +
          id(1) +
          ",\"Notebook, ruled\",\"line one\r\nline two\",1.50,INR,5\r\n"
      ).created()
    );
    assertEquals(
      "Notebook, ruled",
      db.queryForObject(
        "select name from catalog_product where id=?",
        String.class,
        id(1)
      )
    );
  }

  @Test
  void rejectsOversizedAndExcessRowsBeforeWriting() {
    assertThrows(IllegalArgumentException.class, () ->
      upload("x".repeat(262145))
    );
    var csv = new StringBuilder(header);
    for (int n = 1; n <= 1001; n++) csv.append(row(n, "1.00"));
    assertThrows(IllegalArgumentException.class, () -> upload(csv.toString()));
    assertEquals(
      0,
      db.queryForObject(
        "select count(*) from catalog_import_product",
        Integer.class
      )
    );
  }

  @Test
  void catalogKeysetVisitsEveryProductOnceAndRejectsInvalidCursors()
    throws Exception {
    var csv = new StringBuilder(header);
    for (int n = 1; n <= 51; n++) csv.append(row(n, "1.00"));
    upload(csv.toString());
    Set<String> ids = new HashSet<>();
    String cursor = null;
    int pages = 0;
    do {
      var response = get(
        "/api/catalog/products/page?limit=7" +
          (cursor == null ? "" : "&cursor=" + cursor)
      );
      assertEquals(200, response.statusCode());
      var page = json.readTree(response.body());
      assertTrue(page.get("items").size() <= 7);
      for (var item : page.get("items"))
        assertTrue(ids.add(item.get("id").asText()));
      cursor = page.get("nextCursor").isNull()
        ? null
        : page.get("nextCursor").asText();
      assertTrue(++pages < 20);
    } while (cursor != null);
    assertEquals(54, ids.size());
    assertEquals(
      400,
      get("/api/catalog/products/page?cursor=broken").statusCode()
    );
    assertEquals(400, get("/api/catalog/products/page?limit=51").statusCode());
  }

  @Test
  void historyUsesStableTieBreakerFilterAndServerOwnedBuyer() throws Exception {
    UUID buyer = UUID.fromString("00000000-0000-0000-0000-000000000001");
    for (int n = 1; n <= 6; n++) {
      boolean cancelled = n % 2 == 0;
      db.update(
        "insert into purchase_order(id,buyer_id,idempotency_key,product_id,product_name,quantity,unit_price,total_amount,currency,requested_payment,payment_status,status,created_at) values (?,?,?,'10000000-0000-0000-0000-000000000001','Notebook',1,499,499,'INR',?,?,?,'2026-01-01T00:00:00Z')",
        id(n),
        n == 6 ? UUID.randomUUID() : buyer,
        UUID.randomUUID(),
        cancelled ? "DECLINE" : "APPROVE",
        cancelled ? "DECLINED" : "AUTHORIZED",
        cancelled ? "CANCELLED" : "CONFIRMED"
      );
    }
    var page = json.readTree(
      get("/api/orders/page?status=CANCELLED&limit=1").body()
    );
    assertEquals(id(4).toString(), page.get("items").get(0).get("id").asText());
    String cursor = page.get("nextCursor").asText();
    var next = json.readTree(
      get("/api/orders/page?status=CANCELLED&limit=1&cursor=" + cursor).body()
    );
    assertEquals(id(2).toString(), next.get("items").get(0).get("id").asText());
    assertTrue(next.get("nextCursor").isNull());
    assertEquals(
      400,
      get("/api/orders/page?status=CONFIRMED&cursor=" + cursor).statusCode()
    );
    assertEquals(
      400,
      get(
        "/api/orders/page?cursor=" +
          PageCursor.encode("orders-v1-ALL", "invalid", id(1).toString())
      ).statusCode()
    );
  }

  @Test
  void readQueryCountIsConstantAsPageSizeGrows() throws Exception {
    var csv = new StringBuilder(header);
    for (int n = 1; n <= 25; n++) csv.append(row(n, "1.00"));
    upload(csv.toString());
    SELECTS.set(0);
    assertEquals(200, get("/api/catalog/products/page?limit=1").statusCode());
    assertEquals(2, SELECTS.get());
    SELECTS.set(0);
    assertEquals(200, get("/api/catalog/products/page?limit=20").statusCode());
    assertEquals(2, SELECTS.get());
    // A populated history is essential: an empty result could conceal per-row follow-up queries.
    db.execute(
      """
      insert into purchase_order(id,buyer_id,idempotency_key,product_id,product_name,quantity,unit_price,total_amount,currency,requested_payment,payment_status,status,created_at)
      select md5('query-order-'||g)::uuid,'00000000-0000-0000-0000-000000000001',md5('query-key-'||g)::uuid,
      '10000000-0000-0000-0000-000000000001','Notebook',1,499,499,'INR','APPROVE','AUTHORIZED','CONFIRMED',
      '2026-01-01'::timestamptz+g*interval '1 second' from generate_series(1,25) g
      """
    );
    SELECTS.set(0);
    var history = get("/api/orders/page?limit=20");
    assertEquals(200, history.statusCode());
    assertEquals(20, json.readTree(history.body()).get("items").size());
    assertEquals(1, SELECTS.get());
    SELECTS.set(0);
    assertEquals(200, get("/api/orders/page?limit=1").statusCode());
    assertEquals(1, SELECTS.get());
  }

  @Test
  void multipartHttpContractAndLargeBodyHandling() throws Exception {
    String boundary = "ecal-test-boundary";
    String body =
      "--" +
      boundary +
      "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"import.csv\"\r\nContent-Type: text/csv\r\n\r\n" +
      header +
      row(1, "1.00") +
      "\r\n--" +
      boundary +
      "--\r\n";
    var response = http.send(
      HttpRequest.newBuilder(
        URI.create("http://127.0.0.1:" + port + "/api/catalog/import")
      )
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    assertEquals(200, response.statusCode());
    assertEquals(1, json.readTree(response.body()).get("created").asInt());
    String oversized = body.replace(
      header + row(1, "1.00"),
      "x".repeat(262145)
    );
    var tooLarge = http.send(
      HttpRequest.newBuilder(
        URI.create("http://127.0.0.1:" + port + "/api/catalog/import")
      )
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofString(oversized))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    assertEquals(413, tooLarge.statusCode());
    assertEquals(
      "UPLOAD_TOO_LARGE",
      json.readTree(tooLarge.body()).get("code").asText()
    );
  }
}
