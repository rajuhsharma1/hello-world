package com.ecal.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** A stable error contract exposes business recovery information, never SQL statements or stack traces. */
@RestControllerAdvice
public class ApiErrors {

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> business(
    BusinessException e,
    HttpServletRequest r
  ) {
    int status = switch (e.code()) {
      case PRODUCT_NOT_FOUND, ORDER_NOT_FOUND -> 404;
      default -> 409;
    };
    return problem(status, e.getMessage(), e.code().name(), r);
  }

  @ExceptionHandler(
    {
      MethodArgumentNotValidException.class,
      MissingRequestHeaderException.class,
      MethodArgumentTypeMismatchException.class,
      HttpMessageNotReadableException.class,
      IllegalArgumentException.class,
    }
  )
  ResponseEntity<ProblemDetail> invalid(Exception e, HttpServletRequest r) {
    return problem(
      400,
      "Use a UUID idempotency key, a product ID, quantity 1–10 and APPROVE or DECLINE. No client price is accepted.",
      "INVALID_REQUEST",
      r
    );
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ProblemDetail> storage(
    DataAccessException e,
    HttpServletRequest r
  ) {
    LoggerFactory.getLogger(getClass()).warn(
      "Database operation failed; exceptionType={}",
      e.getClass().getSimpleName()
    );
    return problem(
      503,
      "Storage is temporarily unavailable. Retry the exact checkout with the same idempotency key.",
      "STORAGE_UNAVAILABLE",
      r
    );
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest r) {
    // Framework 404/405/415 errors are client failures, not an internal checkout outage.
    if (
      e instanceof org.springframework.web.ErrorResponse error
    ) return problem(
      error.getStatusCode().value(),
      "The requested resource, method or media type is not supported.",
      "HTTP_ERROR",
      r
    );
    LoggerFactory.getLogger(getClass()).error(
      "Unexpected request failure; exceptionType={}",
      e.getClass().getSimpleName()
    );
    return problem(
      500,
      "Checkout could not complete. Retry the exact request with the same idempotency key.",
      "INTERNAL_ERROR",
      r
    );
  }

  private ResponseEntity<ProblemDetail> problem(
    int status,
    String message,
    String code,
    HttpServletRequest request
  ) {
    var p = ProblemDetail.forStatusAndDetail(
      HttpStatusCode.valueOf(status),
      message
    );
    p.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
    p.setInstance(URI.create(request.getRequestURI()));
    p.setProperty("code", code);
    return ResponseEntity.status(status)
      .contentType(MediaType.APPLICATION_PROBLEM_JSON)
      .body(p);
  }
}
