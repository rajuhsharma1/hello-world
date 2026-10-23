package com.ecal.distributed.orders;

/** Pure workflow rules: uncertainty is durable state, never an invented payment decline. */
public enum OrderState {
  PENDING,
  RESERVED,
  PAYMENT_UNKNOWN,
  RELEASE_PENDING,
  CONFIRMED,
  CANCELLED,
  REJECTED;

  public boolean allows(OrderState next) {
    return switch (this) {
      case PENDING -> next == RESERVED || next == REJECTED;
      case RESERVED -> next == PAYMENT_UNKNOWN;
      case PAYMENT_UNKNOWN -> next == CONFIRMED || next == RELEASE_PENDING;
      case RELEASE_PENDING -> next == CANCELLED;
      default -> false;
    };
  }
}
