package com.ouroboros.logicflow.exception;

public abstract class DesignException extends RuntimeException {
  public DesignException(String message, Throwable cause) {
    super(message, cause);
  }

  public DesignException(String message) {
    super(message);
  }
}
