package com.ouroboros.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Configuration revision tokens. A metadata default has no database row, so its
 * revision is represented by a deterministic content token until the first write.
 */
public final class ConfigurationRevision {

  private ConfigurationRevision() {
  }

  public static String defaultRevision(String key, String rawDefaultValue) {
    return "default-" + digest(key, rawDefaultValue);
  }

  public static String legacyStoredRevision(String key, String rawValue) {
    return "stored-" + digest(key, rawValue);
  }

  /** A row incarnation prevents stale tokens matching after delete and recreate. */
  public static String storedRevision(String key, String rawValue, Long revision, String generation) {
    String version = revision == null ? legacyStoredRevision(key, rawValue) : String.valueOf(revision);
    return generation == null ? version : generation + ":" + version;
  }

  private static String digest(String key, String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest((String.valueOf(key) + "\u0000" + String.valueOf(value))
          .getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(bytes.length * 2);
      for (byte item : bytes) {
        result.append(String.format("%02x", item));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("无法计算配置 revision", ex);
    }
  }
}
