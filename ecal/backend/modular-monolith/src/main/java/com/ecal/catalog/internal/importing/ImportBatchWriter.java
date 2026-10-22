package com.ecal.catalog.internal.importing;

import com.ecal.inventory.api.Inventory;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** A separate proxied bean gives every worker its own transaction; request thread transactions do not cross threads. */
@Service
public class ImportBatchWriter {

  private final JdbcTemplate db;
  private final Inventory inventory;

  public ImportBatchWriter(JdbcTemplate db, Inventory inventory) {
    this.db = db;
    this.inventory = inventory;
  }

  @Transactional(
    propagation = Propagation.REQUIRES_NEW,
    isolation = Isolation.READ_COMMITTED,
    timeout = 10
  )
  public Counts write(List<ImportRow> rows) {
    int created = 0,
      skipped = 0;
    // Stable lock order reduces deadlock risk if another writer is introduced later.
    for (var row : rows
      .stream()
      .sorted(Comparator.comparing(ImportRow::id))
      .toList()) {
      row.validate();
      String fingerprint = row.fingerprint();
      int inserted = db.update(
        "insert into catalog_product(id,name,description,price,currency) values (?,?,?,?,?) on conflict(id) do nothing",
        row.id(),
        row.name(),
        row.description(),
        row.amount(),
        row.currency()
      );
      if (inserted == 1) {
        inventory.initializeStock(row.id(), row.initialStock());
        db.update(
          "insert into catalog_import_product(product_id,fingerprint) values (?,?)",
          row.id(),
          fingerprint
        );
        created++;
      } else {
        var prior = db.queryForList(
          "select fingerprint from catalog_import_product where product_id=?",
          String.class,
          row.id()
        );
        if (
          prior.size() != 1 || !fingerprint.equals(prior.getFirst())
        ) throw new IllegalArgumentException(
          "Existing product has a different import identity."
        );
        skipped++;
      }
    }
    return new Counts(created, skipped);
  }

  public record Counts(int created, int skipped) {}
}
