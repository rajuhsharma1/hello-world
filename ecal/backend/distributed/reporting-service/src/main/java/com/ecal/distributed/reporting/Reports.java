package com.ecal.distributed.reporting;

import com.ecal.distributed.support.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.web.bind.annotation.*;

@RestController
public class Reports {

  private final EventInbox inbox;
  private final JdbcTemplate db;

  public Reports(EventInbox inbox, JdbcTemplate db) {
    this.inbox = inbox;
    this.db = db;
  }

  @KafkaListener(topics = EventPlatform.TOPIC, groupId = "reporting-v1")
  public void event(ConsumerRecord<String, String> record, Acknowledgment ack) {
    inbox.receive(record.key(), record.value(), this::apply);
    ack.acknowledge();
  }

  /** A full snapshot plus monotonic source version prevents regressions and tolerates version gaps.
   * This is valid for a state projection, never a recipe for applying out-of-order financial deltas. */
  public void apply(EventEnvelope e) {
    if (!e.source().equals("orders")) return;
    var p = e.payload();
    db.update(
      "insert into order_projection(order_id,aggregate_version,status,total_amount,currency,created_at,event_at) values (?,?,?,?,?,?,?) on conflict(order_id) do update set aggregate_version=excluded.aggregate_version,status=excluded.status,total_amount=excluded.total_amount,currency=excluded.currency,event_at=excluded.event_at,projected_at=clock_timestamp() where order_projection.aggregate_version<excluded.aggregate_version",
      e.aggregateId(),
      e.aggregateVersion(),
      p.path("status").asText(),
      new BigDecimal(p.path("totalAmount").asText()),
      p.path("currency").asText(),
      Timestamp.from(Instant.parse(p.path("createdAt").asText())),
      Timestamp.from(e.occurredAt())
    );
  }

  @GetMapping("/api/v1/reports/summary")
  public Map<String, Object> summary() {
    var result = new LinkedHashMap<String, Object>();
    result.put("consistency", "EVENTUAL");
    result.put("observedAt", Instant.now().toString());
    result.put(
      "totals",
      db.queryForList(
        "select status,count(*) as orders,coalesce(sum(total_amount),0)::text as amount from order_projection group by status order by status"
      )
    );
    result.put(
      "monthlyRevenue",
      db.queryForList(
        "select to_char(created_at at time zone 'Asia/Kolkata','YYYY-MM') as month,sum(total_amount)::text as revenue from order_projection where status='CONFIRMED' group by 1 order by 1 desc limit 24"
      )
    );
    result.put(
      "lastProjectedAt",
      db.queryForObject(
        "select max(projected_at)::text from order_projection",
        String.class
      )
    );
    result.put(
      "quarantinedEvents",
      db.queryForObject("select count(*) from event_quarantine", Long.class)
    );
    // Freshness is exposed honestly: an idle stream cannot distinguish no writes from an outage.
    result.put(
      "freshnessNote",
      "Last projection time is not broker lag. Refresh after checkout; reporting may be behind."
    );
    return result;
  }
}
