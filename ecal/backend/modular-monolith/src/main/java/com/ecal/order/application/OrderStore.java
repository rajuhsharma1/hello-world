package com.ecal.order.application;

import com.ecal.order.domain.Order;
import com.ecal.payment.api.Payments;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Orders own their persistence contract; the SQL implementation is replaceable without changing checkout. */
public interface OrderStore {
  record Stored(
    Order order,
    UUID idempotencyKey,
    Payments.Outcome requestedPayment,
    String paymentStatus
  ) {}

  boolean claim(Order order, UUID key, Payments.Outcome requestedPayment);
  Optional<Stored> byKey(UUID buyer, UUID key);
  Optional<Stored> byId(UUID buyer, UUID id);
  List<Stored> recent(UUID buyer);
  void finish(Order order, Payments.Status paymentStatus);
}
