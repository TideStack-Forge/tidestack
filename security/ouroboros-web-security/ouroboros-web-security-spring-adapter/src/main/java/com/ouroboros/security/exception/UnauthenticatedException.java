package com.ouroboros.security.exception;

import org.springframework.security.core.AuthenticationException;

public class UnauthenticatedException extends AuthenticationException {

  public UnauthenticatedException() {
    super("身份未经认证");
  }

  public UnauthenticatedException(String explanation) {
    super(explanation);
  }
}
