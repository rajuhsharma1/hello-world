package com.ecal.notification.internal;

import com.ecal.notification.api.Notifications;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class RecordingNotifications implements Notifications {

  private final JdbcTemplate db;

  RecordingNotifications(JdbcTemplate db) {
    this.db = db;
  }

  @org.springframework.transaction.annotation.Transactional(
    propagation = org.springframework.transaction.annotation.Propagation.MANDATORY
  )
  @Override
  public void record(UUID orderId, String status) {
    db.update(
      "insert into notification_record(order_id,message) values (?,?)",
      orderId,
      "Simulated notification: order " +
        status.toLowerCase(java.util.Locale.ROOT) +
        "."
    );
  }
}
