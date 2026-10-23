package com.ecal.distributed.orders;

import com.ecal.distributed.support.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrdersController {

  @org.springframework.beans.factory.annotation.Value(
    "${ecal.events-enabled:false}"
  )
  private boolean eventsEnabled;

  private final Checkout checkout;
  private final OrderStore store;
  private final RemoteServices services;

  public OrdersController(
    Checkout checkout,
    OrderStore store,
    RemoteServices services
  ) {
    this.checkout = checkout;
    this.store = store;
    this.services = services;
  }

  public record Command(
    @NotNull UUID productId,
    @Min(1) @Max(10) int quantity,
    @NotNull @Pattern(regexp = "APPROVE|DECLINE") String paymentOutcome
  ) {}

  public record Product(
    UUID id,
    String name,
    String description,
    String price,
    String currency,
    int availableStock
  ) {}

  @PostMapping({ "/api/v1/orders", "/api/orders" })
  public ResponseEntity<OrderStore.View> checkout(
    @RequestHeader("Idempotency-Key") UUID key,
    @Valid @RequestBody Command c
  ) {
    if (eventsEnabled) {
      var claim = store.claim(
        key,
        new OrderStore.Command(c.productId(), c.quantity(), c.paymentOutcome())
      );
      var view = claim.stored().view();
      boolean terminal = Set.of("CONFIRMED", "CANCELLED", "REJECTED").contains(
        view.status()
      );
      return ResponseEntity.status(terminal ? 200 : 202)
        .location(java.net.URI.create("/api/v1/orders/" + view.id()))
        .body(view);
    }
    var result = checkout.checkout(
      key,
      new OrderStore.Command(c.productId(), c.quantity(), c.paymentOutcome())
    );
    return ResponseEntity.status(result.created() ? 201 : 200).body(
      result.order()
    );
  }

  @GetMapping({ "/api/v1/orders/{id}", "/api/orders/{id}" })
  public OrderStore.View find(@PathVariable UUID id) {
    return store.find(id).view();
  }

  @GetMapping({ "/api/v1/orders", "/api/orders" })
  public List<OrderStore.View> recent() {
    return store.history("ALL", 50, null).items();
  }

  @GetMapping({ "/api/v1/orders/page", "/api/orders/page" })
  public Page<OrderStore.View> history(
    @RequestParam(defaultValue = "ALL") String status,
    @RequestParam(defaultValue = "20") int limit,
    @RequestParam(required = false) String cursor
  ) {
    return store.history(status, limit, cursor);
  }

  @GetMapping({ "/api/v1/catalog/products", "/api/catalog/products" })
  public List<Product> products() {
    return page(50, null).items();
  }

  @GetMapping({ "/api/v1/catalog/products/page", "/api/catalog/products/page" })
  public Page<Product> page(
    @RequestParam(defaultValue = "20") int limit,
    @RequestParam(required = false) String cursor
  ) {
    PageCursor.limit(limit);
    var after = PageCursor.decode(cursor, "catalog-v1", 3);
    var rows = store.products(
      limit + 1,
      after == null ? null : after[1],
      after == null ? null : UUID.fromString(after[2])
    );
    boolean more = rows.size() > limit;
    var products = rows.subList(0, Math.min(limit, rows.size()));
    var stock = services.availability(
      products.stream().map(OrderStore.Product::id).toList()
    );
    var items = products
      .stream()
      .map(p ->
        new Product(
          p.id(),
          p.name(),
          p.description(),
          p.price(),
          p.currency(),
          stock.get(p.id())
        )
      )
      .toList();
    var last = products.isEmpty() ? null : products.getLast();
    return new Page<>(
      items,
      more
        ? PageCursor.encode("catalog-v1", last.name(), last.id().toString())
        : null
    );
  }
}
