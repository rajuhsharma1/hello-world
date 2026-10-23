package com.ecal.distributed.orders;

import com.ecal.distributed.support.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Each method is a short local transaction; the coordinator makes HTTP calls only after it returns. */
@Service
public class OrderStore {

  public static final UUID BUYER = UUID.fromString(
    "00000000-0000-0000-0000-000000000001"
  );
  private final JdbcTemplate db;

  public OrderStore(JdbcTemplate db) {
    this.db = db;
  }

  public record Product(
    UUID id,
    String name,
    String description,
    String price,
    String currency
  ) {}

  public record Command(UUID productId, int quantity, String paymentOutcome) {}

  public record View(
    UUID id,
    UUID productId,
    String productName,
    int quantity,
    String unitPrice,
    String totalAmount,
    String currency,
    String status,
    String paymentStatus,
    Instant createdAt
  ) {}

  public record Stored(View view, String outcome, UUID key) {}

  public record Claim(Stored stored, boolean created) {}

  public List<Product> products(int fetch, String name, UUID id) {
    String sql =
      "select id,name,description,price,currency from catalog_product" +
      (name == null ? "" : " where (name,id)>(?,?)") +
      " order by name,id limit ?";
    Object[] args = name == null
      ? new Object[] { fetch }
      : new Object[] { name, id, fetch };
    return db.query(
      sql,
      (r, n) ->
        new Product(
          r.getObject(1, UUID.class),
          r.getString(2),
          r.getString(3),
          r.getBigDecimal(4).toPlainString(),
          r.getString(5)
        ),
      args
    );
  }

  private Stored map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    return new Stored(
      new View(
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
      r.getString("requested_payment"),
      r.getObject("idempotency_key", UUID.class)
    );
  }

  public Stored find(UUID id) {
    var rows = db.query(
      "select * from purchase_order where buyer_id=? and id=?",
      this::map,
      BUYER,
      id
    );
    if (rows.isEmpty()) throw new Fault(
      404,
      "ORDER_NOT_FOUND",
      "Order not found."
    );
    return rows.getFirst();
  }

  @Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
  public Claim claim(UUID key, Command command) {
    var prior = db.query(
      "select * from purchase_order where buyer_id=? and idempotency_key=?",
      this::map,
      BUYER,
      key
    );
    if (!prior.isEmpty()) return checked(prior.getFirst(), command, false);
    var product = db.query(
      "select id,name,description,price,currency from catalog_product where id=?",
      (r, n) ->
        new Product(
          r.getObject(1, UUID.class),
          r.getString(2),
          r.getString(3),
          r.getBigDecimal(4).toPlainString(),
          r.getString(5)
        ),
      command.productId()
    );
    if (product.isEmpty()) throw new Fault(
      404,
      "PRODUCT_NOT_FOUND",
      "Product not found."
    );
    var p = product.getFirst();
    UUID id = UUID.randomUUID();
    BigDecimal price = new BigDecimal(p.price());
    int created = db.update(
      "insert into purchase_order(id,buyer_id,idempotency_key,product_id,product_name,quantity,unit_price,total_amount,currency,requested_payment,status) values (?,?,?,?,?,?,?,?,?,?,'PENDING') on conflict(buyer_id,idempotency_key) do nothing",
      id,
      BUYER,
      key,
      p.id(),
      p.name(),
      command.quantity(),
      price,
      price.multiply(BigDecimal.valueOf(command.quantity())),
      p.currency(),
      command.paymentOutcome()
    );
    var winner = db.queryForObject(
      "select * from purchase_order where buyer_id=? and idempotency_key=?",
      this::map,
      BUYER,
      key
    );
    return checked(winner, command, created == 1);
  }

  private Claim checked(Stored prior, Command c, boolean created) {
    if (
      !prior.view().productId().equals(c.productId()) ||
      prior.view().quantity() != c.quantity() ||
      !prior.outcome().equals(c.paymentOutcome())
    ) throw new Fault(
      409,
      "IDEMPOTENCY_CONFLICT",
      "This key belongs to a different checkout payload."
    );
    return new Claim(prior, created);
  }

  @Transactional(timeout = 5)
  public boolean lease(UUID id, UUID token) {
    // A bounded lease avoids holding a JDBC connection or row lock across network calls.
    // Remote commands remain idempotent, and every local state write is fenced by this token.
    return (
      db.update(
        "update purchase_order set lease_token=?,lease_until=now()+interval '30 seconds' where id=? and (lease_until is null or lease_until<now())",
        token,
        id
      ) ==
      1
    );
  }

  @Transactional(timeout = 5)
  public Stored advance(
    UUID id,
    UUID token,
    String expected,
    String next,
    String payment
  ) {
    if (
      !OrderState.valueOf(expected).allows(OrderState.valueOf(next))
    ) throw new IllegalStateException("Illegal workflow transition.");
    if (
      db.update(
        "update purchase_order set status=?,payment_status=? where id=? and lease_token=? and status=?",
        next,
        payment,
        id,
        token,
        expected
      ) !=
      1
    ) throw new Fault(
      503,
      "CHECKOUT_IN_PROGRESS",
      "Another attempt owns recovery. Retry the same checkout identity."
    );
    if (Set.of("CONFIRMED", "CANCELLED", "REJECTED").contains(next)) db.update(
      "insert into notification_record(order_id,message) values (?,?) on conflict(order_id) do nothing",
      id,
      next
    );
    return find(id);
  }

  @Transactional(timeout = 5)
  public void releaseLease(UUID id, UUID token) {
    db.update(
      "update purchase_order set lease_token=null,lease_until=null where id=? and lease_token=?",
      id,
      token
    );
  }

  public Page<View> history(String status, int limit, String cursor) {
    PageCursor.limit(limit);
    if (
      !Set.of(
        "ALL",
        "CONFIRMED",
        "CANCELLED",
        "REJECTED",
        "PENDING",
        "RESERVED",
        "PAYMENT_UNKNOWN",
        "RELEASE_PENDING"
      ).contains(status)
    ) throw new IllegalArgumentException("Invalid status.");
    String scope = "orders-v1-" + status;
    String[] after = PageCursor.decode(cursor, scope, 3);
    List<Object> args = new ArrayList<>();
    args.add(BUYER);
    String sql = "select * from purchase_order where buyer_id=?";
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
    var rows = db.query(sql, this::map, args.toArray());
    boolean more = rows.size() > limit;
    var items = rows
      .subList(0, Math.min(limit, rows.size()))
      .stream()
      .map(Stored::view)
      .toList();
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
