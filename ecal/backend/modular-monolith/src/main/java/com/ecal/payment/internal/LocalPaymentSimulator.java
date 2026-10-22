package com.ecal.payment.internal;

import com.ecal.payment.api.Payments;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class LocalPaymentSimulator implements Payments {

  private final JdbcTemplate db;

  LocalPaymentSimulator(JdbcTemplate db) {
    this.db = db;
  }

  @org.springframework.transaction.annotation.Transactional(
    propagation = org.springframework.transaction.annotation.Propagation.MANDATORY
  )
  @Override
  public Status authorize(UUID orderId, BigDecimal amount, Outcome outcome) {
    var status = outcome == Outcome.APPROVE
      ? Status.AUTHORIZED
      : Status.DECLINED;
    // A unique order_id is a second line of defense against duplicate simulated effects.
    // Real remote payments cannot join this SQL transaction; extraction requires reconciliation.
    db.update(
      "insert into simulated_payment(order_id,amount,status) values (?,?,?)",
      orderId,
      amount,
      status.name()
    );
    return status;
  }
}
