package com.ecal.distributed.support;

import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A versioned wire contract, not a shared business entity. Payloads are additive full snapshots. */
public record EventEnvelope(
  UUID eventId,
  String source,
  UUID aggregateId,
  long aggregateVersion,
  int schemaVersion,
  String type,
  Instant occurredAt,
  String correlationId,
  String traceId,
  JsonNode payload
) {
  public static final JsonMapper JSON = JsonMapper.builder()
    .disable(
      tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
    )
    .build();
  private static final Map<String, Set<String>> TYPES = Map.of(
    "orders",
    Set.of("OrderCreated", "OrderProgress", "OrderConfirmed", "OrderCancelled"),
    "inventory",
    Set.of("InventoryReserved", "InventoryRejected", "InventoryReleased"),
    "payment",
    Set.of("PaymentAuthorized", "PaymentFailed")
  );

  public static EventEnvelope parse(String raw) {
    if (raw == null || raw.length() > 65536) throw new IllegalArgumentException(
      "Envelope size"
    );
    var e = JSON.readValue(raw, EventEnvelope.class);
    if (
      e.eventId == null ||
      e.aggregateId == null ||
      e.aggregateVersion < 1 ||
      e.schemaVersion != 1 ||
      e.occurredAt == null ||
      !TYPES.getOrDefault(e.source == null ? "" : e.source, Set.of()).contains(
        e.type
      ) ||
      e.payload == null ||
      !e.payload.isObject()
    ) throw new IllegalArgumentException("Envelope contract");
    if (e.source.equals("orders")) {
      var p = e.payload;
      if (
        !e.aggregateId.toString().equals(p.path("id").asText()) ||
        !Set.of(
          "PENDING",
          "RESERVED",
          "PAYMENT_UNKNOWN",
          "RELEASE_PENDING",
          "CONFIRMED",
          "CANCELLED",
          "REJECTED"
        ).contains(p.path("status").asText()) ||
        !"INR".equals(p.path("currency").asText()) ||
        !p.path("totalAmount").asText().matches("[0-9]+\\.[0-9]{2}")
      ) throw new IllegalArgumentException("Order snapshot");
      Instant.parse(p.path("createdAt").asText());
      UUID.fromString(p.path("productId").asText());
      if (
        !p.path("quantity").isInt() ||
        p.path("quantity").asInt() < 1 ||
        p.path("quantity").asInt() > 10 ||
        new java.math.BigDecimal(p.path("totalAmount").asText()).signum() <=
        0 ||
        new java.math.BigDecimal(p.path("totalAmount").asText()).precision() >
        14
      ) throw new IllegalArgumentException("Order snapshot bounds");
      if (
        (e.type.equals("OrderCreated") &&
          !p.path("status").asText().equals("PENDING")) ||
        (e.type.equals("OrderConfirmed") &&
          !p.path("status").asText().equals("CONFIRMED")) ||
        (e.type.equals("OrderCancelled") &&
          !Set.of("CANCELLED", "REJECTED").contains(p.path("status").asText()))
      ) throw new IllegalArgumentException("Event type and state mismatch");
    }
    return e;
  }
}
