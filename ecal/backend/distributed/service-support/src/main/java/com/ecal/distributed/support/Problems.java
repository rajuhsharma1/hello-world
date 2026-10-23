package com.ecal.distributed.support;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Shared HTTP plumbing only: services do not share entities, business interfaces, or database access. */
@RestControllerAdvice
public class Problems {

  @ExceptionHandler(Fault.class)
  ResponseEntity<ProblemDetail> business(Fault f, HttpServletRequest r) {
    return problem(f.status, f.code, f.getMessage(), r);
  }

  @ExceptionHandler(
    {
      IllegalArgumentException.class,
      java.time.DateTimeException.class,
      org.springframework.web.bind.MethodArgumentNotValidException.class,
      org.springframework.http.converter.HttpMessageNotReadableException.class,
      org.springframework.web.method.annotation
        .MethodArgumentTypeMismatchException.class,
      org.springframework.web.bind.MissingRequestHeaderException.class,
    }
  )
  ResponseEntity<ProblemDetail> invalid(Exception e, HttpServletRequest r) {
    return problem(
      400,
      "INVALID_REQUEST",
      "Check UUIDs, quantities (1–10), amounts, cursor/limit and simulator outcome.",
      r
    );
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> failed(Exception e, HttpServletRequest r) {
    if (
      e instanceof org.springframework.web.ErrorResponse error
    ) return problem(
      error.getStatusCode().value(),
      "HTTP_ERROR",
      "Resource, method or media type is not supported.",
      r
    );
    org.slf4j.LoggerFactory.getLogger(getClass()).error(
      "request_failed exceptionType={}",
      e.getClass().getSimpleName()
    );
    return problem(
      503,
      "SERVICE_UNAVAILABLE",
      "The outcome could not be verified. Retry the original operation with its original identity.",
      r
    );
  }

  private ResponseEntity<ProblemDetail> problem(
    int status,
    String code,
    String message,
    HttpServletRequest r
  ) {
    var p = ProblemDetail.forStatusAndDetail(
      HttpStatusCode.valueOf(status),
      message
    );
    p.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
    p.setInstance(URI.create(r.getRequestURI()));
    p.setProperty("code", code);
    p.setProperty("correlationId", org.slf4j.MDC.get("correlationId"));
    return ResponseEntity.status(status)
      .contentType(MediaType.APPLICATION_PROBLEM_JSON)
      .body(p);
  }
}
