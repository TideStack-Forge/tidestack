package com.ouroboros.security.exception;

import com.ouroboros.exception.BusinessException;

/** An explicit denied decision, including constraints that cannot be safely executed. */
public class AuthorizationDeniedException extends BusinessException {
  public AuthorizationDeniedException(String reason) {
    super(403, "[AUTHORIZATION_DENIED] " + reason);
  }

  public AuthorizationDeniedException(String reason, Throwable cause) {
    super(403, "[AUTHORIZATION_DENIED] " + reason, cause);
  }
}
