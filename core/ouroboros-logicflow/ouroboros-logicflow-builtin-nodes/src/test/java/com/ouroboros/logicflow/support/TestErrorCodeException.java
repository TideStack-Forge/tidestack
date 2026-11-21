package com.ouroboros.logicflow.support;

public class TestErrorCodeException extends RuntimeException {
  private final Integer errorCode;

  public TestErrorCodeException(Integer errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public Integer getErrorCode() {
    return errorCode;
  }
}
