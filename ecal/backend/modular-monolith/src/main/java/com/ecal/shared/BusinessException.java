package com.ecal.shared;

/** A framework-independent failure vocabulary; HTTP translation belongs at the web boundary. */
public final class BusinessException extends RuntimeException {

  public enum Code {
    PRODUCT_NOT_FOUND,
    ORDER_NOT_FOUND,
    INSUFFICIENT_STOCK,
    IDEMPOTENCY_CONFLICT,
  }

  private final Code code;

  public BusinessException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
