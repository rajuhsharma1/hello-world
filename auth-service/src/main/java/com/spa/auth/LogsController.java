package com.spa.auth;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LogsController {

  private final AuthService auth;
  private final ObjectMapper json;
  private final String loki;
  private final HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(3))
    .build();

  public LogsController(
    AuthService a,
    ObjectMapper j,
    @Value("${ark.loki-url}") String l
  ) {
    auth = a;
    json = j;
    loki = l;
  }

  @GetMapping("/api/auth/logs")
  public Map<String, Object> logs(
    HttpServletRequest request,
    @RequestParam(defaultValue = "ALL") String service,
    @RequestParam(defaultValue = "ALL") String level,
    @RequestParam(defaultValue = "15") int minutes,
    @RequestParam(defaultValue = "100") int limit,
    @RequestParam(defaultValue = "") String search,
    @RequestParam(defaultValue = "") String traceId
  ) {
    auth.require(request, "OWNER", "ADMIN");
    if (
      !Set.of(
        "ALL",
        "booking-service",
        "notification-service",
        "operations-service",
        "auth-service"
      ).contains(service) ||
      !Set.of("ALL", "INFO", "WARN", "ERROR", "DEBUG").contains(level) ||
      minutes < 1 ||
      minutes > 1440 ||
      limit < 1 ||
      limit > 500 ||
      search.length() > 160 ||
      (!traceId.isEmpty() && !traceId.matches("[a-fA-F0-9]{32}"))
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Invalid log filter."
    );
    try {
      String query = "{job=\"spa\"} | json";
      if (!service.equals("ALL")) query +=
        " | service=" + json.writeValueAsString(service);
      if (!level.equals("ALL")) query +=
        " | level=" + json.writeValueAsString(level);
      if (!search.isEmpty()) query += " |= " + json.writeValueAsString(search);
      if (!traceId.isEmpty()) query +=
        " | traceId=" +
        json.writeValueAsString(traceId.toLowerCase(Locale.ROOT));
      String url =
        loki +
        "/loki/api/v1/query_range?query=" +
        URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8) +
        "&limit=" +
        limit +
        "&start=" +
        URLEncoder.encode(
          Instant.now().minusSeconds(minutes * 60L).toString(),
          java.nio.charset.StandardCharsets.UTF_8
        ) +
        "&direction=backward";
      var result = client.send(
        java.net.http.HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(8))
          .GET()
          .build(),
        HttpResponse.BodyHandlers.ofString()
      );
      if (result.statusCode() != 200) throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Log storage is temporarily unavailable."
      );
      List<Map<String, Object>> entries = new ArrayList<>();
      for (var stream : json
        .readTree(result.body())
        .path("data")
        .path("result"))
        for (var value : stream.path("values")) {
          JsonNode line;
          try {
            line = json.readTree(value.get(1).asText());
          } catch (Exception ignored) {
            continue;
          }
          long nanos = Long.parseLong(value.get(0).asText());
          String message = line.path("message").asText("");
          entries.add(
            Map.of(
              "timestamp",
              Instant.ofEpochSecond(
                nanos / 1000000000,
                nanos % 1000000000
              ).toString(),
              "service",
              line.path("service").asText("legacy"),
              "level",
              line.path("level").asText("INFO"),
              "message",
              message.substring(0, Math.min(5000, message.length())),
              "traceId",
              line.path("traceId").asText("")
            )
          );
        }
      entries.sort(
        Comparator.comparing(
          e -> (String) e.get("timestamp"),
          Comparator.reverseOrder()
        )
      );
      return Map.of("entries", entries, "minutes", minutes, "limit", limit);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Log request interrupted."
      );
    } catch (Exception e) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Log storage is temporarily unavailable."
      );
    }
  }
}
