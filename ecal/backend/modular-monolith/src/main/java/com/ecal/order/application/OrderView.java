package com.ecal.order.application;

import com.ecal.order.domain.Order;
import java.time.Instant;
import java.util.UUID;

/** Decimal strings keep exact monetary values across the Java/JavaScript boundary. */
public record OrderView(
  UUID id,
  UUID productId,
  String productName,
  int quantity,
  String unitPrice,
  String totalAmount,
  String currency,
  String status,
  String paymentStatus,
  Instant createdAt
) {
  public static OrderView of(Order o, String paymentStatus) {
    return new OrderView(
      o.id(),
      o.productId(),
      o.productName(),
      o.quantity(),
      o.unitPrice().toPlainString(),
      o.totalAmount().toPlainString(),
      o.currency(),
      o.status().name(),
      paymentStatus,
      o.createdAt()
    );
  }
}
