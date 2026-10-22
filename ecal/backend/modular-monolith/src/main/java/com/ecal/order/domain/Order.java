package com.ecal.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The order aggregate owns legal transitions and an immutable price snapshot, independently of Spring. */
public final class Order {

  public enum Status {
    PENDING,
    RESERVED,
    CONFIRMED,
    CANCELLED,
  }

  private final UUID id;
  private final UUID buyerId;
  private final UUID productId;
  private final String productName;
  private final int quantity;
  private final BigDecimal unitPrice;
  private final String currency;
  private final Instant createdAt;
  private Status status;

  public Order(
    UUID id,
    UUID buyerId,
    UUID productId,
    String name,
    int quantity,
    BigDecimal price,
    String currency,
    Instant at,
    Status status
  ) {
    this.id = Objects.requireNonNull(id);
    this.buyerId = Objects.requireNonNull(buyerId);
    this.productId = Objects.requireNonNull(productId);
    if (
      quantity < 1 || quantity > 10 || price == null || price.signum() <= 0
    ) throw new IllegalArgumentException("Invalid order quantity or price.");
    this.productName = Objects.requireNonNull(name);
    this.quantity = quantity;
    this.unitPrice = price;
    this.currency = Objects.requireNonNull(currency);
    this.createdAt = Objects.requireNonNull(at);
    this.status = Objects.requireNonNull(status);
  }

  public void reserved() {
    transition(Status.PENDING, Status.RESERVED);
  }

  public void confirmed() {
    transition(Status.RESERVED, Status.CONFIRMED);
  }

  public void cancelled() {
    transition(Status.RESERVED, Status.CANCELLED);
  }

  private void transition(Status expected, Status next) {
    if (status != expected) throw new IllegalStateException(
      "Illegal order transition: " + status + " -> " + next
    );
    status = next;
  }

  public UUID id() {
    return id;
  }

  public UUID buyerId() {
    return buyerId;
  }

  public UUID productId() {
    return productId;
  }

  public String productName() {
    return productName;
  }

  public int quantity() {
    return quantity;
  }

  public BigDecimal unitPrice() {
    return unitPrice;
  }

  public BigDecimal totalAmount() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }

  public String currency() {
    return currency;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Status status() {
    return status;
  }
}
