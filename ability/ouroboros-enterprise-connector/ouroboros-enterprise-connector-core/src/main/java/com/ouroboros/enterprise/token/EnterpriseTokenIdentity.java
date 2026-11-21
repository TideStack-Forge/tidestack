package com.ouroboros.enterprise.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

/** In-memory cache identity. Neither keys nor diagnostics contain credential text. */
public final class EnterpriseTokenIdentity {
  private EnterpriseTokenIdentity() {}

  public static String key(ResolvedEnterpriseConnection connection, String tokenKind, String... stableSecrets) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, connection.provider());
      update(digest, connection.connectionId());
      update(digest, tokenKind);
      // Managed revisions include the committed credential generation. External references also
      // need the effective stable secret fingerprint because their source has no revision contract.
      update(digest, connection.revision());
      update(digest, String.valueOf(connection.config().get("corpId")));
      for (String value : stableSecrets) {
        update(digest, value);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private static void update(MessageDigest digest, String value) {
    String text = value == null ? "" : value;
    digest.update((text.length() + ":" + text).getBytes(StandardCharsets.UTF_8));
  }
}
