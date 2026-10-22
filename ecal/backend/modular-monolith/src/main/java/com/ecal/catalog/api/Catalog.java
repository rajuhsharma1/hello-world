package com.ecal.catalog.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Orders depend on this price-snapshot contract, not catalog's SQL adapter. */
public interface Catalog {
  record Product(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    String currency
  ) {}

  Product find(UUID productId);
  List<Product> list();
  List<Product> page(int fetchSize, String afterName, UUID afterId);
}
