package com.ouroboros.security.exception;

public class SessionException extends SecurityException {

  public SessionException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public SessionException(String msg) {
    super(msg);
  }
}
