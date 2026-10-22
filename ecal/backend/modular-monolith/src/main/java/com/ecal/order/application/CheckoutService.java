package com.ecal.order.application;

import com.ecal.catalog.api.Catalog;
import com.ecal.inventory.api.Inventory;
import com.ecal.notification.api.Notifications;
import com.ecal.order.domain.Order;
import com.ecal.payment.api.Payments;
import com.ecal.shared.BusinessException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates a LOCAL transaction. This is not a distributed Saga; see ADR-006 before extraction. */
@Service
public class CheckoutService {

  // An explicit teaching shortcut, not an authentication mechanism. No buyer ID is accepted from the browser.
  public static final UUID DEMO_BUYER = UUID.fromString(
    "00000000-0000-0000-0000-000000000001"
  );

  public record Command(
    UUID productId,
    int quantity,
    Payments.Outcome paymentOutcome
  ) {}

  public record Result(OrderView order, boolean replayed) {}

  private final Catalog catalog;
  private final Inventory inventory;
  private final Payments payments;
  private final Notifications notifications;
  private final OrderStore orders;
  private final Clock clock;

  public CheckoutService(
    Catalog c,
    Inventory i,
    Payments p,
    Notifications n,
    OrderStore o,
    Clock clock
  ) {
    catalog = c;
    inventory = i;
    payments = p;
    notifications = n;
    orders = o;
    this.clock = clock;
  }

  /** Same key + same business payload returns the original committed result without reserving or paying again. */
  @Transactional(
    timeout = 10,
    isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED
  )
  public Result checkout(UUID key, Command command) {
    if (
      key == null ||
      command == null ||
      command.productId() == null ||
      command.paymentOutcome() == null ||
      command.quantity() < 1 ||
      command.quantity() > 10
    ) throw new IllegalArgumentException("Invalid checkout command.");
    var existing = orders.byKey(DEMO_BUYER, key);
    if (existing.isPresent()) return replay(existing.get(), command);
    var product = catalog.find(command.productId());
    var order = new Order(
      UUID.randomUUID(),
      DEMO_BUYER,
      product.id(),
      product.name(),
      command.quantity(),
      product.price(),
      product.currency(),
      clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
      Order.Status.PENDING
    );
    if (!orders.claim(order, key, command.paymentOutcome())) {
      // Under PostgreSQL READ COMMITTED, this new statement sees the winner's committed row.
      // We never catch a unique-constraint error and continue in an already-aborted transaction.
      return replay(
        orders
          .byKey(DEMO_BUYER, key)
          .orElseThrow(() ->
            new IllegalStateException("Claimed order missing.")
          ),
        command
      );
    }
    inventory.reserve(product.id(), command.quantity());
    order.reserved();
    var payment = payments.authorize(
      order.id(),
      order.totalAmount(),
      command.paymentOutcome()
    );
    if (payment == Payments.Status.AUTHORIZED) order.confirmed();
    else {
      inventory.release(product.id(), command.quantity());
      order.cancelled();
    }
    orders.finish(order, payment);
    // The stub is a SQL record inside this transaction, not external email. A failed insert rolls
    // back stock, payment and order together; Phase 4 replaces external delivery with an outbox.
    notifications.record(order.id(), order.status().name());
    return new Result(OrderView.of(order, payment.name()), false);
  }

  private Result replay(OrderStore.Stored stored, Command command) {
    var o = stored.order();
    if (
      !o.productId().equals(command.productId()) ||
      o.quantity() != command.quantity() ||
      stored.requestedPayment() != command.paymentOutcome()
    ) throw new BusinessException(
      BusinessException.Code.IDEMPOTENCY_CONFLICT,
      "This idempotency key belongs to a different checkout payload."
    );
    return new Result(OrderView.of(o, stored.paymentStatus()), true);
  }

  public OrderView find(UUID id) {
    var stored = orders
      .byId(DEMO_BUYER, id)
      .orElseThrow(() ->
        new BusinessException(
          BusinessException.Code.ORDER_NOT_FOUND,
          "Order not found."
        )
      );
    return OrderView.of(stored.order(), stored.paymentStatus());
  }

  public List<OrderView> recent() {
    return orders
      .recent(DEMO_BUYER)
      .stream()
      .map(s -> OrderView.of(s.order(), s.paymentStatus()))
      .toList();
  }
}
