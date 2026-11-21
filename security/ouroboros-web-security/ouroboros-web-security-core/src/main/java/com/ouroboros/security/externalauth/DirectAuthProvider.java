package com.ouroboros.security.externalauth;

import io.vavr.control.Either;

/**
 * Synchronous credential verification provider (e.g. LDAP).
 * Returns result directly without redirect.
 */
public interface DirectAuthProvider extends ExternalAuthProvider {

  /**
   * Authenticate with username and password against the external system.
   *
   * @param username the login identifier
   * @param password the user's password
   * @return Right(ExternalIdentity) on success, Left(AuthError) on failure
   */
  Either<AuthError, ExternalIdentity> authenticate(String username, String password);
}
