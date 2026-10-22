package com.ecal.order.internal;

import com.ecal.order.application.OrderStore;
import com.ecal.order.domain.Order;
import com.ecal.payment.api.Payments;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrderStore implements OrderStore {

  private final JdbcTemplate db;

  JdbcOrderStore(JdbcTemplate db) {
    this.db = db;
  }

  private static final org.springframework.jdbc.core.RowMapper<Stored> ROW = (
    r,
    n
  ) ->
    new Stored(
      new Order(
        r.getObject("id", UUID.class),
        r.getObject("buyer_id", UUID.class),
        r.getObject("product_id", UUID.class),
        r.getString("product_name"),
        r.getInt("quantity"),
        r.getBigDecimal("unit_price"),
        r.getString("currency"),
        r.getTimestamp("created_at").toInstant(),
        Order.Status.valueOf(r.getString("status"))
      ),
      r.getObject("idempotency_key", UUID.class),
      Payments.Outcome.valueOf(r.getString("requested_payment")),
      r.getString("payment_status")
    );

  @Override
  public boolean claim(Order o, UUID key, Payments.Outcome requested) {
    // A unique constraint arbitrates racing retries across every application instance.
    // ON CONFLICT waits for the first transaction, unlike a JVM-local synchronized block.
    return (
      db.update(
        "insert into purchase_order(id,buyer_id,idempotency_key,product_id,product_name,quantity,unit_price,total_amount,currency,requested_payment,status,created_at) values (?,?,?,?,?,?,?,?,?,?,?,?) on conflict(buyer_id,idempotency_key) do nothing",
        o.id(),
        o.buyerId(),
        key,
        o.productId(),
        o.productName(),
        o.quantity(),
        o.unitPrice(),
        o.totalAmount(),
        o.currency(),
        requested.name(),
        o.status().name(),
        java.sql.Timestamp.from(o.createdAt())
      ) ==
      1
    );
  }

  @Override
  public Optional<Stored> byKey(UUID buyer, UUID key) {
    return db
      .query(
        "select * from purchase_order where buyer_id=? and idempotency_key=?",
        ROW,
        buyer,
        key
      )
      .stream()
      .findFirst();
  }

  @Override
  public Optional<Stored> byId(UUID buyer, UUID id) {
    return db
      .query(
        "select * from purchase_order where buyer_id=? and id=?",
        ROW,
        buyer,
        id
      )
      .stream()
      .findFirst();
  }

  @Override
  public List<Stored> recent(UUID buyer) {
    return db.query(
      "select * from purchase_order where buyer_id=? order by created_at desc,id desc limit 50",
      ROW,
      buyer
    );
  }

  @Override
  public void finish(Order o, Payments.Status status) {
    if (
      db.update(
        "update purchase_order set status=?,payment_status=? where id=? and status='PENDING'",
        o.status().name(),
        status.name(),
        o.id()
      ) !=
      1
    ) throw new IllegalStateException("Order was finalized more than once.");
  }
}
