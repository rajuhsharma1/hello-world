package com.ecal.distributed.inventory;

import com.ecal.distributed.support.Fault;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Only this service changes stock. Its ledger makes retries safe across JVM restarts. */
@Service
public class InventoryStore {

  private final JdbcTemplate db;

  public InventoryStore(JdbcTemplate db) {
    this.db = db;
  }

  public record Reservation(
    UUID orderId,
    UUID productId,
    int quantity,
    String state
  ) {}

  private Reservation find(UUID id) {
    return db.queryForObject(
      "select order_id,product_id,quantity,state from reservation where order_id=?",
      (r, n) ->
        new Reservation(
          r.getObject(1, UUID.class),
          r.getObject(2, UUID.class),
          r.getInt(3),
          r.getString(4)
        ),
      id
    );
  }

  private void matches(Reservation prior, UUID product, int quantity) {
    if (
      !prior.productId().equals(product) || prior.quantity() != quantity
    ) throw new Fault(
      409,
      "RESERVATION_IDENTITY_CONFLICT",
      "Order identity belongs to another reservation payload."
    );
  }

  @Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
  public Reservation reserve(UUID order, UUID product, int quantity) {
    com.ecal.distributed.support.EventContext.attach(db);
    if (
      db.queryForObject(
        "select count(*) from stock where product_id=?",
        Integer.class,
        product
      ) ==
      0
    ) throw new Fault(404, "PRODUCT_NOT_FOUND", "Stock product not found.");
    int inserted = db.update(
      "insert into reservation(order_id,product_id,quantity,state) values (?,?,?,'PENDING') on conflict(order_id) do nothing",
      order,
      product,
      quantity
    );
    // ON CONFLICT waits for a concurrent owner. This fresh READ COMMITTED statement sees its durable decision.
    var existing = find(order);
    matches(existing, product, quantity);
    if (inserted == 0) return existing;
    int reserved = db.update(
      "update stock set available=available-? where product_id=? and available>=?",
      quantity,
      product,
      quantity
    );
    db.update(
      "update reservation set state=? where order_id=?",
      reserved == 1 ? "RESERVED" : "REJECTED",
      order
    );
    // Return rejection normally so its ledger commits. Throwing here would roll back the identity too.
    return find(order);
  }

  @Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
  public Reservation release(UUID order, UUID product, int quantity) {
    com.ecal.distributed.support.EventContext.attach(db);
    int inserted = db.update(
      "insert into reservation(order_id,product_id,quantity,state) values (?,?,?,'RELEASED') on conflict(order_id) do nothing",
      order,
      product,
      quantity
    );
    // A release-before-reserve is a tombstone: a delayed reserve must never consume stock later.
    var existing = find(order);
    matches(existing, product, quantity);
    if (inserted == 1 || !existing.state().equals("RESERVED")) return existing;
    if (
      db.update(
        "update reservation set state='RELEASED' where order_id=? and state='RESERVED'",
        order
      ) ==
      1
    ) db.update(
      "update stock set available=available+? where product_id=?",
      quantity,
      product
    );
    return find(order);
  }

  public Map<UUID, Integer> availability(List<UUID> ids) {
    if (ids.isEmpty() || ids.size() > 50) throw new IllegalArgumentException(
      "Choose 1–50 products."
    );
    var result = new HashMap<UUID, Integer>();
    String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
    db.query(
      "select product_id,available from stock where product_id in (" +
        marks +
        ")",
      (org.springframework.jdbc.core.RowCallbackHandler) r ->
        result.put(r.getObject(1, UUID.class), r.getInt(2)),
      ids.toArray()
    );
    return result;
  }
}
