package com.ecal.distributed.support;

/** Safe application failure: message and stable code may cross HTTP; SQL/cause details may not. */
public class Fault extends RuntimeException {

  public final int status;
  public final String code;

  public Fault(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}
