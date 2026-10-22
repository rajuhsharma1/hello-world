package com.ecal.inventory.internal;

import com.ecal.inventory.api.Inventory;
import com.ecal.shared.BusinessException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInventory implements Inventory {

  private final JdbcTemplate db;

  JdbcInventory(JdbcTemplate db) {
    this.db = db;
  }

  @org.springframework.transaction.annotation.Transactional(
    propagation = org.springframework.transaction.annotation.Propagation.MANDATORY
  )
  @Override
  public void reserve(UUID productId, int quantity) {
    if (quantity < 1) throw new IllegalArgumentException(
      "Reservation must be positive."
    );
    // PostgreSQL takes a row lock and rechecks this predicate after a concurrent writer commits.
    // A read-then-write in Java would let both buyers purchase the same last unit (ADR-009).
    if (
      db.update(
        "update inventory_stock set available=available-? where product_id=? and available>=?",
        quantity,
        productId,
        quantity
      ) !=
      1
    ) {
      throw new BusinessException(
        BusinessException.Code.INSUFFICIENT_STOCK,
        "Not enough stock. Refresh the catalog and choose a smaller quantity."
      );
    }
  }

  @org.springframework.transaction.annotation.Transactional(
    propagation = org.springframework.transaction.annotation.Propagation.MANDATORY
  )
  @Override
  public void release(UUID productId, int quantity) {
    if (quantity < 1) throw new IllegalArgumentException(
      "Release must be positive."
    );
    if (
      db.update(
        "update inventory_stock set available=available+? where product_id=?",
        quantity,
        productId
      ) !=
      1
    ) throw new IllegalStateException("Missing reserved inventory row.");
  }

  @org.springframework.transaction.annotation.Transactional(
    propagation = org.springframework.transaction.annotation.Propagation.MANDATORY
  )
  @Override
  public void initializeStock(UUID productId, int stock) {
    if (stock < 0 || stock > 1000000) throw new IllegalArgumentException(
      "Stock must be 0–1000000."
    );
    db.update(
      "insert into inventory_stock(product_id,available) values (?,?)",
      productId,
      stock
    );
  }

  @Override
  public Map<UUID, Integer> availability(java.util.List<UUID> productIds) {
    Map<UUID, Integer> result = new HashMap<>();
    if (productIds.isEmpty()) return result;
    String placeholders = String.join(
      ",",
      java.util.Collections.nCopies(productIds.size(), "?")
    );
    db.query(
      "select product_id,available from inventory_stock where product_id in (" +
        placeholders +
        ")",
      (org.springframework.jdbc.core.RowCallbackHandler) r ->
        result.put(r.getObject(1, UUID.class), r.getInt(2)),
      productIds.toArray()
    );
    return result;
  }
}
