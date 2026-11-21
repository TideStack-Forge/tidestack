package com.ouroboros.security.exception;

public class InvalidTokenException extends TokenException {
  public InvalidTokenException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public InvalidTokenException(String msg) {
    super(msg);
  }
}
