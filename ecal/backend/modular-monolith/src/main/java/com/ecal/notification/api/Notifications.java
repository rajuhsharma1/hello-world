package com.ecal.notification.api;

import java.util.UUID;

/** A local notification record, never an actual email or a Kafka delivery guarantee. */
public interface Notifications {
  void record(UUID orderId, String finalStatus);
}
