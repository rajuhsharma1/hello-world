package com.ecal.distributed.system;

import static org.junit.jupiter.api.Assertions.*;

import com.ecal.distributed.orders.OrderState;
import org.junit.jupiter.api.Test;

class StateTest {

  @Test
  void unknownPaymentCannotBeCalledCancelledOrRejected() {
    assertFalse(OrderState.PAYMENT_UNKNOWN.allows(OrderState.CANCELLED));
    assertFalse(OrderState.PAYMENT_UNKNOWN.allows(OrderState.REJECTED));
    assertTrue(OrderState.PAYMENT_UNKNOWN.allows(OrderState.RELEASE_PENDING));
    assertTrue(OrderState.PAYMENT_UNKNOWN.allows(OrderState.CONFIRMED));
  }

  @Test
  void cancellationRequiresReleaseAndTerminalsCannotRegress() {
    assertFalse(OrderState.RESERVED.allows(OrderState.CANCELLED));
    assertTrue(OrderState.RELEASE_PENDING.allows(OrderState.CANCELLED));
    for (var state : new OrderState[] {
      OrderState.CANCELLED,
      OrderState.CONFIRMED,
      OrderState.REJECTED,
    })
      for (var next : OrderState.values()) assertFalse(state.allows(next));
  }
}
