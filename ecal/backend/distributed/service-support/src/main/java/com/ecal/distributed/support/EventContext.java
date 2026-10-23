package com.ecal.distributed.support;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

public final class EventContext {

  private EventContext() {}

  public static void attach(JdbcTemplate db) {
    db.queryForObject(
      "select set_config('ecal.correlation_id',?,true)",
      String.class,
      java.util.Objects.toString(MDC.get("correlationId"), "")
    );
    db.queryForObject(
      "select set_config('ecal.trace_id',?,true)",
      String.class,
      java.util.Objects.toString(MDC.get("traceId"), "")
    );
  }
}
