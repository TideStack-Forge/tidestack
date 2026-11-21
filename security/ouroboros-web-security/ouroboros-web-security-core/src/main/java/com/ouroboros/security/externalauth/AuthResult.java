package com.ouroboros.security.externalauth;

import com.ouroboros.security.session.UserSession;

/**
 * Result of external auth processing by ExternalAuthService.
 */
public class AuthResult {

  public enum Type {
    LOGGED_IN,
    BINDING_REQUIRED
  }

  private final Type type;
  private final UserSession session;
  private final String bindingToken;
  private final String userIdentity;

  private AuthResult(Type type, UserSession session, String bindingToken, String userIdentity) {
    this.type = type;
    this.session = session;
    this.bindingToken = bindingToken;
    this.userIdentity = userIdentity;
  }

  public static AuthResult loggedIn(UserSession session, String userIdentity) {
    return new AuthResult(Type.LOGGED_IN, session, null, userIdentity);
  }

  public static AuthResult bindingRequired(String bindingToken) {
    return new AuthResult(Type.BINDING_REQUIRED, null, bindingToken, null);
  }

  public Type getType() { return type; }
  public UserSession getSession() { return session; }
  public String getBindingToken() { return bindingToken; }
  public String getUserIdentity() { return userIdentity; }
}
