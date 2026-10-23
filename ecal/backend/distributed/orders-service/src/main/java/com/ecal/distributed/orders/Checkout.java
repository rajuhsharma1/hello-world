package com.ecal.distributed.orders;

import com.ecal.distributed.support.Fault;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;

/** Durable synchronous orchestration with compensation; HTTP is never inside a local DB transaction.
 * This already has Saga-like semantics. Phase 4 adds event-driven execution/outbox rather than inventing atomic HTTP. */
@Service
public class Checkout {

  private final OrderStore store;
  private final RemoteServices services;

  public Checkout(OrderStore store, RemoteServices services) {
    this.store = store;
    this.services = services;
  }

  public record Result(OrderStore.View order, boolean created) {}

  public Result checkout(UUID key, OrderStore.Command command) {
    var claim = store.claim(key, command);
    var order = claim.stored();
    UUID id = order.view().id(),
      token = UUID.randomUUID();
    if (terminal(order)) return result(order, false);
    if (!store.lease(id, token)) throw new Fault(
      503,
      "CHECKOUT_IN_PROGRESS",
      "This checkout is already progressing. Retry the same identity shortly."
    );
    try (var log = MDC.putCloseable("orderId", id.toString())) {
      order = store.find(id);
      if (order.view().status().equals("PENDING")) {
        var reservation = services.reserve(order);
        order = store.advance(
          id,
          token,
          "PENDING",
          reservation.state().equals("REJECTED") ? "REJECTED" : "RESERVED",
          null
        );
      }
      if (
        order.view().status().equals("RESERVED") ||
        order.view().status().equals("PAYMENT_UNKNOWN")
      ) {
        // Persist uncertainty BEFORE asking a remote payment owner. A crash cannot leave a false decline.
        if (order.view().status().equals("RESERVED")) order = store.advance(
          id,
          token,
          "RESERVED",
          "PAYMENT_UNKNOWN",
          null
        );
        var payment = services.authorize(order);
        order = store.advance(
          id,
          token,
          "PAYMENT_UNKNOWN",
          payment.status().equals("AUTHORIZED")
            ? "CONFIRMED"
            : "RELEASE_PENDING",
          payment.status()
        );
      }
      if (order.view().status().equals("RELEASE_PENDING")) {
        services.release(order);
        order = store.advance(
          id,
          token,
          "RELEASE_PENDING",
          "CANCELLED",
          "DECLINED"
        );
      }
      LoggerFactory.getLogger(getClass()).info(
        "checkout_progress status={}",
        order.view().status()
      );
      return result(order, claim.created());
    } catch (RuntimeException failure) {
      LoggerFactory.getLogger(getClass()).warn(
        "checkout_unacknowledged orderId={} exceptionType={}",
        id,
        failure.getClass().getSimpleName()
      );
      throw failure;
    } finally {
      // Token match prevents an expired coordinator from releasing a newer attempt's lease.
      store.releaseLease(id, token);
    }
  }

  private boolean terminal(OrderStore.Stored order) {
    return Set.of("CONFIRMED", "CANCELLED", "REJECTED").contains(
      order.view().status()
    );
  }

  private Result result(OrderStore.Stored order, boolean created) {
    if (order.view().status().equals("REJECTED")) throw new Fault(
      409,
      "INSUFFICIENT_STOCK",
      "This attempt was rejected for stock. Its durable result will not change on replay."
    );
    return new Result(order.view(), created);
  }
}
