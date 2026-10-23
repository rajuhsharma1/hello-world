package com.ecal.distributed.support;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableKafka
@EnableScheduling
@ConditionalOnProperty(name = "ecal.events-enabled", havingValue = "true")
public class EventPlatform {

  public static final String TOPIC = "ecal.events.v1";

  @Bean
  org.springframework.kafka.listener.DefaultErrorHandler eventErrorHandler() {
    return new org.springframework.kafka.listener.DefaultErrorHandler(
      new org.springframework.util.backoff.FixedBackOff(1000, Long.MAX_VALUE)
    );
  }

  @Bean
  NewTopic eventsTopic() {
    return new NewTopic(TOPIC, 4, (short) 1);
  }

  @Bean
  EventInbox eventInbox(JdbcTemplate db, PlatformTransactionManager manager) {
    return new EventInbox(db, manager);
  }

  @Bean
  Relay eventRelay(
    JdbcTemplate db,
    PlatformTransactionManager manager,
    KafkaTemplate<String, String> kafka
  ) {
    return new Relay(db, manager, kafka);
  }

  public static class Relay {

    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;

    public Relay(
      JdbcTemplate db,
      PlatformTransactionManager manager,
      KafkaTemplate<String, String> kafka
    ) {
      this.db = db;
      this.tx = new TransactionTemplate(manager);
      tx.setTimeout(12);
      this.kafka = kafka;
    }

    @Scheduled(
      fixedDelayString = "${ecal.relay-delay-ms:100}",
      initialDelayString = "${ecal.worker-initial-delay-ms:1000}"
    )
    public void tick() {
      try {
        publishOne(false);
      } catch (RuntimeException failure) {
        LoggerFactory.getLogger(getClass()).warn(
          "outbox_delivery_deferred exceptionType={}",
          failure.getClass().getSimpleName()
        );
      }
    }

    /** Crash window is deliberately testable: broker accepted, local published marker not committed. */
    public boolean publishOne(boolean crashAfterSend) {
      return Boolean.TRUE.equals(
        tx.execute(status -> {
          // One transaction-scoped advisory lock serializes owner relays across replicas. Do NOT
          // SKIP LOCKED per row: a second relay could publish version 2 before version 1.
          if (
            !Boolean.TRUE.equals(
              db.queryForObject(
                "select pg_try_advisory_xact_lock(427042)",
                Boolean.class
              )
            )
          ) return false;
          var rows = db.query(
            "select * from event_outbox where published_at is null order by sequence limit 1 for update",
            (r, n) ->
              new EventEnvelope(
                r.getObject("event_id", UUID.class),
                r.getString("source"),
                r.getObject("aggregate_id", UUID.class),
                r.getLong("aggregate_version"),
                1,
                r.getString("event_type"),
                r.getTimestamp("occurred_at").toInstant(),
                r.getString("correlation_id"),
                r.getString("trace_id"),
                EventEnvelope.JSON.readTree(r.getString("payload"))
              )
          );
          if (rows.isEmpty()) return false;
          var e = rows.getFirst();
          try {
            kafka
              .send(
                TOPIC,
                e.aggregateId().toString(),
                EventEnvelope.JSON.writeValueAsString(e)
              )
              .get(6, TimeUnit.SECONDS);
          } catch (Exception failure) {
            if (
              failure instanceof InterruptedException
            ) Thread.currentThread().interrupt();
            throw new IllegalStateException(
              "Broker acknowledgement unavailable",
              failure
            );
          }
          if (crashAfterSend) throw new IllegalStateException(
            "Simulated relay crash after broker acknowledgement"
          );
          db.update(
            "update event_outbox set published_at=? where event_id=?",
            Instant.now().atOffset(java.time.ZoneOffset.UTC),
            e.eventId()
          );
          return true;
        })
      );
    }
  }
}
