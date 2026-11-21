package com.ouroboros.dev.projectgeneration.catalog;

public final class ProjectTemplateIdentifierContracts {

  private static final String TEMPLATE_ID = "^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$";
  private static final String TEMPLATE_VERSION = "^[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z][0-9A-Za-z.-]*)?$";
  private static final String ROW_ID = "^[A-Za-z0-9][A-Za-z0-9._-]{0,158}[A-Za-z0-9]$|^[A-Za-z0-9]$";
  private static final String REVISION = "^[A-Za-z0-9_-]{1,96}$";
  private static final String PATH_SEGMENT = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$";

  private ProjectTemplateIdentifierContracts() {
  }

  public static String requireTemplateId(String value, String fieldName) {
    return requireAsciiMatch(value, fieldName, TEMPLATE_ID, 3, 128);
  }

  public static String requireTemplateVersion(String value, String fieldName) {
    return requireAsciiMatch(value, fieldName, TEMPLATE_VERSION, 1, 64);
  }

  public static String requireStableTemplateVersion(String value, String fieldName) {
    requireTemplateVersion(value, fieldName);
    if (value.contains("-")) {
      throw new IllegalArgumentException(fieldName + " must not be prerelease for stable");
    }
    return value;
  }

  public static String requireRowId(String value, String fieldName) {
    return requireAsciiMatch(value, fieldName, ROW_ID, 1, 160);
  }

  public static String requireRevision(String value, String fieldName) {
    return requireAsciiMatch(value, fieldName, REVISION, 1, 96);
  }

  public static String requirePathSegment(String value, String fieldName) {
    requireAsciiMatch(value, fieldName, PATH_SEGMENT, 1, 64);
    if (isWindowsReservedName(value)) {
      throw new IllegalArgumentException(fieldName + " must not be a Windows reserved name");
    }
    return value;
  }

  public static String requireRelativePathParameter(String value, String fieldName) {
    if (value == null || value.isBlank() || value.startsWith("/") || value.startsWith("//") || value.contains("\\")) {
      throw new IllegalArgumentException(fieldName + " must be a relative path");
    }
    for (String segment : value.split("/")) {
      if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException(fieldName + " contains unsafe segment");
      }
      requirePathSegment(segment, fieldName);
    }
    return value;
  }

  public static String requireDisplayText(String value, String fieldName, int maxCodePoints) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    if (value.codePointCount(0, value.length()) > maxCodePoints) {
      throw new IllegalArgumentException(fieldName + " is too long");
    }
    requireNoControlCharacters(value, fieldName);
    return value;
  }

  public static void requireNoControlCharacters(String value, String fieldName) {
    for (int index = 0; index < value.length(); index++) {
      if (Character.isISOControl(value.charAt(index))) {
        throw new IllegalArgumentException(fieldName + " must not contain control characters");
      }
    }
  }

  private static String requireAsciiMatch(
      String value,
      String fieldName,
      String pattern,
      int minLength,
      int maxLength) {
    if (value == null || value.length() < minLength || value.length() > maxLength) {
      throw new IllegalArgumentException(fieldName + " length is out of range");
    }
    if (!value.chars().allMatch(ch -> ch >= 0x21 && ch <= 0x7e)) {
      throw new IllegalArgumentException(fieldName + " must be printable ASCII");
    }
    if (value.contains("/") || value.contains("\\")) {
      throw new IllegalArgumentException(fieldName + " must not contain path separators");
    }
    if (!value.matches(pattern)) {
      throw new IllegalArgumentException(fieldName + " format is invalid");
    }
    return value;
  }

  private static boolean isWindowsReservedName(String value) {
    String upper = value.toUpperCase(java.util.Locale.ROOT);
    return upper.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");
  }
}
