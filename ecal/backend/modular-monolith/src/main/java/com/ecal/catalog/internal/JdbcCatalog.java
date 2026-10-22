package com.ecal.catalog.internal;

import com.ecal.catalog.api.Catalog;
import com.ecal.shared.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCatalog implements Catalog {

  private final JdbcTemplate db;

  JdbcCatalog(JdbcTemplate db) {
    this.db = db;
  }

  private static final org.springframework.jdbc.core.RowMapper<Product> ROW = (
    r,
    n
  ) ->
    new Product(
      r.getObject("id", UUID.class),
      r.getString("name"),
      r.getString("description"),
      r.getBigDecimal("price"),
      r.getString("currency")
    );

  @Override
  public Product find(UUID id) {
    return db
      .query("select * from catalog_product where id=?", ROW, id)
      .stream()
      .findFirst()
      .orElseThrow(() ->
        new BusinessException(
          BusinessException.Code.PRODUCT_NOT_FOUND,
          "Product not found."
        )
      );
  }

  @Override
  public List<Product> list() {
    return db.query(
      "select * from catalog_product order by name,id limit 50",
      ROW
    );
  }

  @Override
  public List<Product> page(int fetchSize, String afterName, UUID afterId) {
    if (afterName == null) return db.query(
      "select * from catalog_product order by name,id limit ?",
      ROW,
      fetchSize
    );
    return db.query(
      "select * from catalog_product where (name,id)>(?,?) order by name,id limit ?",
      ROW,
      afterName,
      afterId,
      fetchSize
    );
  }
}
