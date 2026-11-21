package com.ouroboros.security.exception;

public abstract class SecurityException extends RuntimeException {
  public SecurityException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public SecurityException(String msg) {
    super(msg);
  }
}
