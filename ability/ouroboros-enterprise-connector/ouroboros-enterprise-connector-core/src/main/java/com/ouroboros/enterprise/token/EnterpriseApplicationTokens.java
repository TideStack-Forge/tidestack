package com.ouroboros.enterprise.token;

import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

/** Provider application tokens; user OAuth tokens remain in the authentication flow. */
public interface EnterpriseApplicationTokens {
  String provider();

  /** Empty by default: installing an unknown token service never opens credential forwarding. */
  default java.util.List<EnterpriseTokenTarget> targets(ResolvedEnterpriseConnection connection) { return java.util.List.of(); }

  String accessToken(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception;

  /** Checks the saved credential with a fresh exchange; does not mutate shared token caches or business state. */
  void verifyConnection(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception;

  /** Evicts only the rejected value, so a late failure cannot invalidate an already refreshed token. */
  boolean invalidate(ResolvedEnterpriseConnection connection, String rejectedToken);

  /** Consumers call once per explicit token rejection; permission to replay stays with the API operation. */
  default String refreshAfterRejection(ResolvedEnterpriseConnection connection, String rejectedToken,
      EnterpriseDeadline deadline) throws Exception {
    invalidate(connection, rejectedToken);
    return accessToken(connection, deadline);
  }
}
