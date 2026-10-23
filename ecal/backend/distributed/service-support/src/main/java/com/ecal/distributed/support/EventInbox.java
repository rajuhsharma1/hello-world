package com.ecal.distributed.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB commit precedes Kafka acknowledgement. Re-delivery after that gap is harmless. */
public class EventInbox {

  private final JdbcTemplate db;
  private final TransactionTemplate tx;

  public EventInbox(JdbcTemplate db, PlatformTransactionManager manager) {
    this.db = db;
    this.tx = new TransactionTemplate(manager);
    this.tx.setTimeout(5);
  }

  public void receive(String key, String raw, Consumer<EventEnvelope> handler) {
    String hash = hash(raw);
    EventEnvelope event;
    try {
      event = EventEnvelope.parse(raw);
      if (
        !event.aggregateId().toString().equals(key)
      ) throw new IllegalArgumentException("Partition key mismatch");
    } catch (Exception invalid) {
      quarantine(hash, raw, "Invalid envelope, schema or partition key");
      return;
    }
    tx.executeWithoutResult(status -> {
      int inserted = db.update(
        "insert into event_inbox(event_id,fingerprint) values (?,?) on conflict do nothing",
        event.eventId(),
        hash
      );
      if (inserted == 0) {
        String prior = db.queryForObject(
          "select fingerprint from event_inbox where event_id=?",
          String.class,
          event.eventId()
        );
        if (!hash.equals(prior)) quarantine(
          hash,
          raw,
          "Event identity reused with different content"
        );
        return;
      }
      // Never quarantine database/handler outages: roll back and let Kafka redeliver.
      handler.accept(event);
    });
  }

  private void quarantine(String hash, String raw, String reason) {
    tx.executeWithoutResult(s ->
      db.update(
        "insert into event_quarantine(fingerprint,raw_event,reason) values (?,?,?) on conflict do nothing",
        hash,
        raw == null ? "<Kafka tombstone>" : raw,
        raw == null ? "Kafka tombstone: no envelope" : reason
      )
    );
  }

  private static String hash(String raw) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          (raw == null ? "<null>" : raw).getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
