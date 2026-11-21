package com.ouroboros.security.exception;

public abstract class TokenException extends SecurityException {
  public TokenException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public TokenException(String msg) {
    super(msg);
  }

  public Boolean isInvalidToken() {
    return true;
  }
}
