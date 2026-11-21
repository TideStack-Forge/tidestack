package com.ouroboros.security.externalauth;

/**
 * Error model for external auth operations. Used as Either left value.
 */
public class AuthError {

  private final int code;
  private final String message;

  public AuthError(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public static AuthError invalidCredentials(String message) {
    return new AuthError(40101, message);
  }

  public static AuthError invalidState(String message) {
    return new AuthError(40102, message);
  }

  public static AuthError invalidBindingToken(String message) {
    return new AuthError(40103, message);
  }

  public static AuthError invalidPlatformCredentials(String message) {
    return new AuthError(40104, message);
  }

  public static AuthError invalidLoginCode(String message) {
    return new AuthError(40105, message);
  }

  public static AuthError userUnavailable(String message) {
    return new AuthError(40106, message);
  }

  public static AuthError bindingRequired(String message) {
    return new AuthError(40901, message);
  }

  public static AuthError bindingConflict(String message) {
    return new AuthError(40902, message);
  }

  public static AuthError rateLimited(String message) {
    return new AuthError(42901, message);
  }

  public static AuthError providerUnavailable(String message) {
    return new AuthError(50201, message);
  }

  public int getCode() { return code; }
  public String getMessage() { return message; }
}
