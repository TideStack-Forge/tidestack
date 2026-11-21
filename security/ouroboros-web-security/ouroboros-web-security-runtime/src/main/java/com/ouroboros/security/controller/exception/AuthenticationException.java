package com.ouroboros.security.controller.exception;

import com.ouroboros.exception.OuroborosException;

/**
 * @author liansz
 */
public class AuthenticationException extends OuroborosException {

  public AuthenticationException(String message) {
    super(message);
  }

  public AuthenticationException(Throwable cause) {
    super(cause.getMessage(), cause);
  }

  public AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
