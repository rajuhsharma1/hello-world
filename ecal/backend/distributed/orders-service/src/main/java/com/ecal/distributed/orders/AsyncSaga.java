package com.ecal.distributed.orders;

import com.ecal.distributed.support.*;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Events activate durable work; existing fenced HTTP commands execute the Saga and its compensation.
 * HTTP remains the command channel in this slice. Owner outcome events are audit/reporting facts. */
@Component
@ConditionalOnProperty(name = "ecal.events-enabled", havingValue = "true")
public class AsyncSaga {

  private final EventInbox inbox;
  private final JdbcTemplate db;
  private final OrderStore store;
  private final Checkout checkout;

  public AsyncSaga(
    EventInbox inbox,
    JdbcTemplate db,
    OrderStore store,
    Checkout checkout
  ) {
    this.inbox = inbox;
    this.db = db;
    this.store = store;
    this.checkout = checkout;
  }

  @KafkaListener(topics = EventPlatform.TOPIC, groupId = "orders-saga-v1")
  public void event(ConsumerRecord<String, String> record, Acknowledgment ack) {
    inbox.receive(record.key(), record.value(), e -> {
      if (e.type().equals("OrderCreated")) db.update(
        "insert into saga_work(order_id,correlation_id,trace_id) values (?,?,?) on conflict do nothing",
        e.aggregateId(),
        e.correlationId(),
        e.traceId()
      );
    });
    ack.acknowledge();
  }

  @Scheduled(
    fixedDelayString = "${ecal.saga-delay-ms:500}",
    initialDelayString = "${ecal.worker-initial-delay-ms:1000}"
  )
  public void tick() {
    // A bounded scan plus the existing 30s fenced order lease supports restart/multiple workers.
    var ids = db.query(
      "select order_id from saga_work where next_attempt_at<=now() order by next_attempt_at limit 10",
      (r, n) -> r.getObject(1, UUID.class)
    );
    for (UUID id : ids) {
      try (var context = MDC.putCloseable("orderId", id.toString())) {
        var contextRow = db.queryForMap(
          "select correlation_id,trace_id from saga_work where order_id=?",
          id
        );
        MDC.put(
          "correlationId",
          Objects.toString(
            contextRow.get("correlation_id"),
            UUID.randomUUID().toString()
          )
        );
        MDC.put(
          "traceId",
          Objects.toString(
            contextRow.get("trace_id"),
            UUID.randomUUID().toString().replace("-", "")
          )
        );
        var order = store.find(id);
        if (
          !Set.of("CONFIRMED", "CANCELLED", "REJECTED").contains(
            order.view().status()
          )
        ) {
          checkout.checkout(
            order.key(),
            new OrderStore.Command(
              order.view().productId(),
              order.view().quantity(),
              order.outcome()
            )
          );
        }
        db.update("delete from saga_work where order_id=?", id);
      } catch (Exception retry) {
        // Backoff survives process restarts; remote ledgers make repeated commands safe.
        db.update(
          "update saga_work set attempts=attempts+1,next_attempt_at=now()+least(30,attempts+1)*interval '1 second' where order_id=?",
          id
        );
        LoggerFactory.getLogger(getClass()).warn(
          "saga_retry_scheduled orderId={} exceptionType={}",
          id,
          retry.getClass().getSimpleName()
        );
      } finally {
        MDC.remove("correlationId");
        MDC.remove("traceId");
      }
    }
  }
}
