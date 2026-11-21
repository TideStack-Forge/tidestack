package com.ouroboros.ability.auth.oidc.registration;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** Protocol-safe failure: no submitted metadata, credentials, or storage exception text. */
public final class DcrFailure extends OAuth2AuthenticationException {
  private final int status;
  private final long retryAfter;
  public DcrFailure(int status, String code) { this(status, code, 0); }
  public DcrFailure(int status, String code, long retryAfter) {
    super(new OAuth2Error(code), code); this.status = status; this.retryAfter = retryAfter;
  }
  public int status() { return status; }
  public long retryAfter() { return retryAfter; }
  public static DcrFailure unavailable() { return new DcrFailure(503, "temporarily_unavailable"); }
}
