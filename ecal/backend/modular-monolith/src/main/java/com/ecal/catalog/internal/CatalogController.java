package com.ecal.catalog.internal;

import com.ecal.catalog.api.Catalog;
import com.ecal.inventory.api.Inventory;
import com.ecal.shared.*;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class CatalogController {

  private final Catalog catalog;
  private final Inventory inventory;

  CatalogController(Catalog catalog, Inventory inventory) {
    this.catalog = catalog;
    this.inventory = inventory;
  }

  record ProductResponse(
    UUID id,
    String name,
    String description,
    String price,
    String currency,
    int availableStock
  ) {}

  @GetMapping("/api/catalog/products")
  List<ProductResponse> list() {
    var products = catalog.list();
    var stock = inventory.availability(
      products.stream().map(Catalog.Product::id).toList()
    );
    // Two bounded queries avoid one inventory query per product. Availability is advisory;
    // only the conditional UPDATE during checkout can promise a reservation.
    return products
      .stream()
      .map(p ->
        new ProductResponse(
          p.id(),
          p.name(),
          p.description(),
          p.price().toPlainString(),
          p.currency(),
          stock.getOrDefault(p.id(), 0)
        )
      )
      .toList();
  }

  @GetMapping("/api/catalog/products/page")
  Page<ProductResponse> page(
    @RequestParam(defaultValue = "20") int limit,
    @RequestParam(required = false) String cursor
  ) {
    PageCursor.limit(limit);
    var after = PageCursor.decode(cursor, "catalog-v1", 3);
    var rows = catalog.page(
      limit + 1,
      after == null ? null : after[1],
      after == null ? null : UUID.fromString(after[2])
    );
    boolean more = rows.size() > limit;
    var products = rows.subList(0, Math.min(limit, rows.size()));
    var stock = inventory.availability(
      products.stream().map(Catalog.Product::id).toList()
    );
    var items = products
      .stream()
      .map(p ->
        new ProductResponse(
          p.id(),
          p.name(),
          p.description(),
          p.price().toPlainString(),
          p.currency(),
          stock.getOrDefault(p.id(), 0)
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
