package com.ouroboros.workflow.approval.external.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable route-key encoding for external-integration uniqueness facts. */
public final class ExternalRouteKeys {

  private static final String VERSION = "v1:";

  private ExternalRouteKeys() {
  }

  public static String of(String... parts) {
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      String value = part == null ? "\u0000" : part;
      builder.append(value.length()).append(':').append(value).append('|');
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return VERSION + HexFormat.of().formatHex(digest.digest(
          builder.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest is unavailable", ex);
    }
  }
}
