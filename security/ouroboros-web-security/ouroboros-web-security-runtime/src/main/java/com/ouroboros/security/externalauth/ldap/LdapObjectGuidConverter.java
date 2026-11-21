package com.ouroboros.security.externalauth.ldap;

/**
 * Converts Active Directory objectGUID (16-byte binary) to Microsoft GUID string format.
 * Uses little-endian byte-order conversion for the first three groups.
 */
public final class LdapObjectGuidConverter {

  private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

  private LdapObjectGuidConverter() {
  }

  /**
   * Convert 16-byte objectGUID to standard GUID string format.
   * Format: {xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}
   *
   * Byte layout (little-endian for first 3 groups):
   * - Data1 (bytes 0-3): uint32 LE -> reversed
   * - Data2 (bytes 4-5): uint16 LE -> reversed
   * - Data3 (bytes 6-7): uint16 LE -> reversed
   * - Data4 high (bytes 8-9): as-is
   * - Data4 low (bytes 10-15): as-is
   */
  public static String convertToGuidString(byte[] bytes) {
    if (bytes == null || bytes.length != 16) {
      throw new IllegalArgumentException("objectGUID must be exactly 16 bytes, got: "
          + (bytes == null ? "null" : bytes.length));
    }

    StringBuilder sb = new StringBuilder(38);
    sb.append('{');

    // Data1: bytes 0-3 reversed (little-endian uint32)
    appendHex(sb, bytes[3]);
    appendHex(sb, bytes[2]);
    appendHex(sb, bytes[1]);
    appendHex(sb, bytes[0]);
    sb.append('-');

    // Data2: bytes 4-5 reversed (little-endian uint16)
    appendHex(sb, bytes[5]);
    appendHex(sb, bytes[4]);
    sb.append('-');

    // Data3: bytes 6-7 reversed (little-endian uint16)
    appendHex(sb, bytes[7]);
    appendHex(sb, bytes[6]);
    sb.append('-');

    // Data4 high: bytes 8-9 as-is
    appendHex(sb, bytes[8]);
    appendHex(sb, bytes[9]);
    sb.append('-');

    // Data4 low: bytes 10-15 as-is
    for (int i = 10; i < 16; i++) {
      appendHex(sb, bytes[i]);
    }

    sb.append('}');
    return sb.toString();
  }

  private static void appendHex(StringBuilder sb, byte b) {
    sb.append(HEX_CHARS[(b >> 4) & 0x0F]);
    sb.append(HEX_CHARS[b & 0x0F]);
  }
}
