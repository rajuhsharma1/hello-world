package com.ecal.distributed.orders;

import com.ecal.distributed.support.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Versioned wire adapters. No service entity/repository or database credential crosses this boundary. */
@Component
public class RemoteServices {

  private final HttpClient http;
  private final Duration timeout;
  private final String inventory, payment;
  private final JsonMapper json = JsonMapper.builder()
    .disable(
      tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
    )
    .build();

  public RemoteServices(
    @Value("${ecal.inventory-url:http://127.0.0.1:8093}") String inventory,
    @Value("${ecal.payment-url:http://127.0.0.1:8094}") String payment,
    @Value("${ecal.remote-connect-ms:500}") int connect,
    @Value("${ecal.remote-request-ms:2000}") int request
  ) {
    if (
      connect < 50 || connect > 2000 || request < 100 || request > 5000
    ) throw new IllegalArgumentException(
      "HTTP timeouts are outside the lab budget."
    );
    this.inventory = base(inventory);
    this.payment = base(payment);
    timeout = Duration.ofMillis(request);
    http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(connect))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();
  }

  private String base(String value) {
    var uri = URI.create(value);
    if (
      !Set.of("http", "https").contains(uri.getScheme()) ||
      uri.getHost() == null ||
      uri.getUserInfo() != null ||
      uri.getQuery() != null ||
      uri.getFragment() != null
    ) throw new IllegalArgumentException(
      "Use a private service origin without credentials."
    );
    return value.replaceAll("/+$", "");
  }

  public record Reservation(
    UUID orderId,
    UUID productId,
    int quantity,
    String state
  ) {}

  public record Authorization(
    UUID orderId,
    String amount,
    String currency,
    String outcome,
    String status
  ) {}

  private HttpResponse<String> send(String url, String method, Object payload) {
    try {
      var builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(timeout)
        .header("X-Correlation-ID", RequestContext.correlation())
        .header("traceparent", RequestContext.childTraceparent());
      if (method.equals("GET")) builder.GET();
      else builder
        .header("Content-Type", "application/json")
        .PUT(
          HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))
        );
      return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (IOException e) {
      throw unavailable();
    }
  }

  private Fault unavailable() {
    return new Fault(
      503,
      "DEPENDENCY_UNAVAILABLE",
      "A service outcome is uncertain. Keep the original checkout key and retry the identical request."
    );
  }

  public Reservation reserve(OrderStore.Stored order) {
    var v = order.view();
    var response = send(
      inventory + "/internal/v1/reservations/" + v.id(),
      "PUT",
      Map.of("productId", v.productId(), "quantity", v.quantity())
    );
    if (
      response.statusCode() != 200 && response.statusCode() != 409
    ) throw unavailable();
    try {
      var result = json.readValue(response.body(), Reservation.class);
      if (
        !result.orderId().equals(v.id()) ||
        !result.productId().equals(v.productId()) ||
        result.quantity() != v.quantity() ||
        !(response.statusCode() == 200
            ? result.state().equals("RESERVED")
            : result.state().equals("REJECTED"))
      ) throw unavailable();
      return result;
    } catch (Fault e) {
      throw e;
    } catch (RuntimeException e) {
      throw unavailable();
    }
  }

  public void release(OrderStore.Stored order) {
    var v = order.view();
    var response = send(
      inventory + "/internal/v1/reservations/" + v.id() + "/release",
      "PUT",
      Map.of("productId", v.productId(), "quantity", v.quantity())
    );
    if (response.statusCode() != 200) throw unavailable();
    try {
      var r = json.readValue(response.body(), Reservation.class);
      if (
        !r.orderId().equals(v.id()) ||
        !r.productId().equals(v.productId()) ||
        r.quantity() != v.quantity() ||
        !r.state().equals("RELEASED")
      ) throw unavailable();
    } catch (Fault e) {
      throw e;
    } catch (RuntimeException e) {
      throw unavailable();
    }
  }

  public Authorization authorize(OrderStore.Stored order) {
    var v = order.view();
    var response = send(
      payment + "/internal/v1/payments/" + v.id(),
      "PUT",
      Map.of(
        "amount",
        v.totalAmount(),
        "currency",
        v.currency(),
        "outcome",
        order.outcome()
      )
    );
    if (response.statusCode() != 200) throw unavailable();
    try {
      var p = json.readValue(response.body(), Authorization.class);
      String expected = order.outcome().equals("APPROVE")
        ? "AUTHORIZED"
        : "DECLINED";
      if (
        !p.orderId().equals(v.id()) ||
        new BigDecimal(p.amount()).compareTo(new BigDecimal(v.totalAmount())) !=
        0 ||
        !p.currency().equals(v.currency()) ||
        !p.outcome().equals(order.outcome()) ||
        !p.status().equals(expected)
      ) throw unavailable();
      return p;
    } catch (Fault e) {
      throw e;
    } catch (RuntimeException e) {
      throw unavailable();
    }
  }

  public Map<UUID, Integer> availability(List<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    var response = send(
      inventory +
        "/internal/v1/stock?ids=" +
        String.join(",", ids.stream().map(UUID::toString).toList()),
      "GET",
      null
    );
    if (response.statusCode() != 200) throw unavailable();
    try {
      JsonNode body = json.readTree(response.body());
      Map<UUID, Integer> result = new HashMap<>();
      for (var id : ids) {
        var stock = body.get(id.toString());
        if (
          stock == null ||
          !stock.isIntegralNumber() ||
          !stock.canConvertToInt() ||
          stock.asInt() < 0
        ) throw unavailable();
        result.put(id, stock.asInt());
      }
      return result;
    } catch (Fault e) {
      throw e;
    } catch (RuntimeException e) {
      throw unavailable();
    }
  }
}
