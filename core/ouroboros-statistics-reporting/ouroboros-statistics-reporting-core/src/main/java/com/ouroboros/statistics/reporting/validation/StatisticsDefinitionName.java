package com.ouroboros.statistics.reporting.validation;

import java.util.regex.Pattern;

/** Identity shared by file resources, fixed routes and projected authorities. */
final class StatisticsDefinitionName {
  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*");

  private StatisticsDefinitionName() {
  }

  static void validate(String name, String type) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException(type + " name is required");
    }
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(type
          + " name must use letters, digits, underscores or hyphens in dot-separated segments: " + name);
    }
  }
}
