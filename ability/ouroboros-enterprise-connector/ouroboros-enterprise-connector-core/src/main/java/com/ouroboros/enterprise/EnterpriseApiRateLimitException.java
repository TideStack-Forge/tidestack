package com.ouroboros.enterprise;

import com.ouroboros.exception.BusinessException;

/** Admission was refused before sending; a domain may schedule a later attempt safely. */
public final class EnterpriseApiRateLimitException extends BusinessException {
  public EnterpriseApiRateLimitException(String message) { super(429, message); }
}
