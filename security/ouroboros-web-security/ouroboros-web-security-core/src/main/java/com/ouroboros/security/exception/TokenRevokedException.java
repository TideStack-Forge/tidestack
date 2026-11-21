package com.ouroboros.security.exception;

public class TokenRevokedException extends TokenException {

  public TokenRevokedException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public TokenRevokedException(String msg) {
    super(msg);
  }
}
