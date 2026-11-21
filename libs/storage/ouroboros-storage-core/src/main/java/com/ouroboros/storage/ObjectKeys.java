package com.ouroboros.storage;

import com.ouroboros.exception.ValidationException;

/** Logical keys are independent of filesystem paths and URL encoding. */
public final class ObjectKeys {
  public static final String TECHNICAL_PREFIX = ".ouroboros-storage/";
  private ObjectKeys() { }

  public static String validate(String key) {
    validatePrefix(key);
    if (key.isEmpty() || key.endsWith("/")) { throw new ValidationException("Object key must identify content"); }
    return key;
  }

  public static String validatePrefix(String prefix) {
    if (prefix == null || prefix.startsWith("/") || prefix.contains("\\")
        || prefix.codePoints().anyMatch(Character::isISOControl)
        || prefix.equals(".ouroboros-storage") || prefix.startsWith(TECHNICAL_PREFIX)) {
      throw new ValidationException("Invalid object key or prefix");
    }
    for (String segment : prefix.split("/", -1)) {
      if (segment.equals(".") || segment.equals("..") || (segment.isEmpty() && !prefix.isEmpty() && !prefix.endsWith("/"))) {
        throw new ValidationException("Object key contains an invalid path segment");
      }
    }
    if (prefix.contains("//") || prefix.matches("^[A-Za-z]:.*")) {
      throw new ValidationException("Object key must be relative");
    }
    return prefix;
  }
}
