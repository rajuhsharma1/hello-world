package com.ecal.distributed.support;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Correlates HTTP hops with validated IDs. This is W3C context propagation, not an exported tracing system. */
public class RequestContext extends OncePerRequestFilter {

  private static final Pattern TRACE = Pattern.compile(
    "00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"
  );

  @Override
  protected void doFilterInternal(
    HttpServletRequest req,
    HttpServletResponse res,
    FilterChain chain
  ) throws ServletException, IOException {
    String correlation = req.getHeader("X-Correlation-ID");
    try {
      correlation = UUID.fromString(correlation).toString();
    } catch (Exception e) {
      correlation = UUID.randomUUID().toString();
    }
    String parent = req.getHeader("traceparent");
    String trace = parent != null &&
      TRACE.matcher(parent).matches() &&
      !parent.substring(3, 35).equals("0".repeat(32)) &&
      !parent.substring(36, 52).equals("0".repeat(16))
      ? parent.substring(3, 35)
      : UUID.randomUUID().toString().replace("-", "");
    long start = System.nanoTime();
    res.setHeader("X-Correlation-ID", correlation);
    res.setHeader("X-Trace-ID", trace);
    try (
      var c = MDC.putCloseable("correlationId", correlation);
      var t = MDC.putCloseable("traceId", trace);
      var f = MDC.putCloseable(
        "traceFlags",
        parent != null && TRACE.matcher(parent).matches()
          ? parent.substring(53)
          : "00"
      )
    ) {
      try {
        chain.doFilter(req, res);
      } finally {
        LoggerFactory.getLogger(getClass()).info(
          "http_request method={} path={} status={} elapsedMs={}",
          req.getMethod(),
          req.getRequestURI(),
          res.getStatus(),
          (System.nanoTime() - start) / 1000000
        );
      }
    }
  }

  public static String correlation() {
    String value = MDC.get("correlationId");
    return value == null ? UUID.randomUUID().toString() : value;
  }

  public static String childTraceparent() {
    String trace = MDC.get("traceId");
    if (trace == null) trace = UUID.randomUUID().toString().replace("-", "");
    return (
      "00-" +
      trace +
      "-" +
      UUID.randomUUID().toString().replace("-", "").substring(0, 16) +
      "-" +
      (MDC.get("traceFlags") == null ? "00" : MDC.get("traceFlags"))
    );
  }
}
