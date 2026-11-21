package com.ouroboros.security.exception;

public class TokenAlreadyUsedException extends TokenException {

  public TokenAlreadyUsedException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public TokenAlreadyUsedException(String msg) {
    super(msg);
  }
}
