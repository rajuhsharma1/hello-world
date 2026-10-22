package com.ecal.order.read;

import com.ecal.order.application.OrderView;
import com.ecal.shared.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** A read projection avoids restoring mutable aggregates for a read-only history list. It is not asynchronous CQRS. */
@Repository
public class OrderHistoryQuery {

  private final JdbcTemplate db;

  public OrderHistoryQuery(JdbcTemplate db) {
    this.db = db;
  }

  private static final String COLUMNS =
    "id,product_id,product_name,quantity,unit_price,total_amount,currency,status,payment_status,created_at";

  public Page<OrderView> page(
    UUID buyer,
    String status,
    int limit,
    String cursor
  ) {
    PageCursor.limit(limit);
    if (
      !Set.of("ALL", "CONFIRMED", "CANCELLED").contains(status)
    ) throw new IllegalArgumentException("Invalid order status.");
    String scope = "orders-v1-" + status;
    var after = PageCursor.decode(cursor, scope, 3);
    List<Object> args = new ArrayList<>();
    args.add(buyer);
    String sql = "select " + COLUMNS + " from purchase_order where buyer_id=?";
    // Separate fixed SQL shapes let PostgreSQL use the status index; '(?=ALL or status=?)' can obscure selectivity.
    if (!status.equals("ALL")) {
      sql += " and status=?";
      args.add(status);
    }
    if (after != null) {
      sql += " and (created_at,id)<(?,?)";
      args.add(Timestamp.from(Instant.parse(after[1])));
      args.add(UUID.fromString(after[2]));
    }
    sql += " order by created_at desc,id desc limit ?";
    args.add(limit + 1);
    var rows = db.query(
      sql,
      (r, n) ->
        new OrderView(
          r.getObject("id", UUID.class),
          r.getObject("product_id", UUID.class),
          r.getString("product_name"),
          r.getInt("quantity"),
          r.getBigDecimal("unit_price").toPlainString(),
          r.getBigDecimal("total_amount").toPlainString(),
          r.getString("currency"),
          r.getString("status"),
          r.getString("payment_status"),
          r.getTimestamp("created_at").toInstant()
        ),
      args.toArray()
    );
    boolean more = rows.size() > limit;
    var items = rows.subList(0, Math.min(limit, rows.size()));
    var last = items.isEmpty() ? null : items.getLast();
    return new Page<>(
      items,
      more
        ? PageCursor.encode(
          scope,
          last.createdAt().toString(),
          last.id().toString()
        )
        : null
    );
  }
}
