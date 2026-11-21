package com.ouroboros.security.externalauth;

import io.vavr.control.Either;

/**
 * Redirect-based authentication provider (OAuth2, CAS).
 * Supports authorize URL generation, callback handling, and exchange handling.
 */
public interface RedirectAuthProvider extends ExternalAuthProvider {

  /**
   * Build the authorization URL to redirect the user to the external IdP.
   */
  AuthorizeUrlResult buildAuthorizeUrl(String returnUrl);

  /**
   * Handle the callback from the external IdP after user authentication.
   */
  Either<AuthError, ExternalIdentity> handleCallback(CallbackParams params);

  /**
   * Handle the exchange flow where the frontend passes code or idToken to the backend.
   */
  Either<AuthError, ExternalIdentity> handleExchange(ExchangeParams params);
}
