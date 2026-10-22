package com.ecal.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Deliberately local and fake: an exception is not used to model a real payment timeout. */
public interface Payments {
  enum Outcome {
    APPROVE,
    DECLINE,
  }

  enum Status {
    AUTHORIZED,
    DECLINED,
  }

  Status authorize(UUID orderId, BigDecimal amount, Outcome outcome);
}
