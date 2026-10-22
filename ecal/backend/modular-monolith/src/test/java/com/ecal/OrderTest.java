package com.ecal;

import static org.junit.jupiter.api.Assertions.*;

import com.ecal.order.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

  private Order order(int quantity, String amount) {
    return new Order(
      UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      "Desk lamp",
      quantity,
      new BigDecimal(amount),
      "INR",
      Instant.parse("2026-01-01T00:00:00Z"),
      Order.Status.PENDING
    );
  }

  @Test
  void confirmedOrderRequiresReservationAndCannotBeCancelledLater() {
    var o = order(2, "2499.50");
    assertThrows(IllegalStateException.class, o::confirmed);
    o.reserved();
    o.confirmed();
    assertEquals(Order.Status.CONFIRMED, o.status());
    assertThrows(IllegalStateException.class, o::cancelled);
    assertThrows(IllegalStateException.class, o::confirmed);
  }

  @Test
  void declinedPaymentHasExplicitCancelledTerminalState() {
    var o = order(1, "10.00");
    o.reserved();
    o.cancelled();
    assertEquals(Order.Status.CANCELLED, o.status());
    assertThrows(IllegalStateException.class, o::reserved);
  }

  @Test
  void monetaryTotalIsExactAndIndependentOfBinaryFloatingPoint() {
    assertEquals(new BigDecimal("7498.50"), order(3, "2499.50").totalAmount());
  }

  @Test
  void invalidQuantitiesAndPricesCannotEnterTheAggregate() {
    assertThrows(IllegalArgumentException.class, () -> order(0, "1.00"));
    assertThrows(IllegalArgumentException.class, () -> order(11, "1.00"));
    assertThrows(IllegalArgumentException.class, () -> order(1, "0.00"));
  }
}
