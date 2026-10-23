package com.ecal.distributed.payment;

import com.ecal.distributed.support.Fault;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PaymentStore {

  private final JdbcTemplate db;

  public PaymentStore(JdbcTemplate db) {
    this.db = db;
  }

  public record Payment(
    UUID orderId,
    String amount,
    String currency,
    String outcome,
    String status
  ) {}

  @Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
  public Payment authorize(
    UUID order,
    BigDecimal amount,
    String currency,
    String outcome
  ) {
    com.ecal.distributed.support.EventContext.attach(db);
    var exact = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    if (
      exact.signum() <= 0 ||
      exact.precision() > 14 ||
      !currency.equals("INR") ||
      !java.util.Set.of("APPROVE", "DECLINE").contains(outcome)
    ) throw new IllegalArgumentException("Invalid payment simulation.");
    // The order ID is the stable provider operation identity, not a new UUID on every network retry.
    db.update(
      "insert into payment(order_id,amount,currency,outcome,status) values (?,?,?,?,?) on conflict(order_id) do nothing",
      order,
      exact,
      currency,
      outcome,
      outcome.equals("APPROVE") ? "AUTHORIZED" : "DECLINED"
    );
    var result = find(order);
    if (
      new BigDecimal(result.amount()).compareTo(exact) != 0 ||
      !result.currency().equals(currency) ||
      !result.outcome().equals(outcome)
    ) throw new Fault(
      409,
      "PAYMENT_IDENTITY_CONFLICT",
      "Order identity belongs to a different payment payload."
    );
    return result;
  }

  public Payment find(UUID id) {
    var rows = db.query(
      "select order_id,amount,currency,outcome,status from payment where order_id=?",
      (r, n) ->
        new Payment(
          r.getObject(1, UUID.class),
          r.getBigDecimal(2).toPlainString(),
          r.getString(3),
          r.getString(4),
          r.getString(5)
        ),
      id
    );
    if (rows.isEmpty()) throw new Fault(
      404,
      "PAYMENT_NOT_FOUND",
      "No recorded payment for this order."
    );
    return rows.getFirst();
  }
}
