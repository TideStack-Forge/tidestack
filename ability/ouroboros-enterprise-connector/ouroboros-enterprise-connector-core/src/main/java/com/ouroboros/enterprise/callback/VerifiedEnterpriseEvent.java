package com.ouroboros.enterprise.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Verified provider payload. Business fields and persistent processing state belong to subscribers. */
public record VerifiedEnterpriseEvent(String provider, String connectionId, String eventId, String eventType,
                                      String eventTime, String payloadHash, String payload) {
  public static String hash(String payload) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  @Override public String toString() {
    return "VerifiedEnterpriseEvent[provider=" + provider + ", connectionId=" + connectionId
        + ", eventId=" + eventId + ", eventType=" + eventType + ", payload=****]";
  }
}
