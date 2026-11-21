package com.ouroboros.resource.model;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Resource usage scope.
 */
public enum ResourceScope {

  FRONTEND("frontend"),
  BACKEND("backend");

  private final String value;

  ResourceScope(String value) {
    this.value = value;
  }

  @JsonCreator
  public static ResourceScope fromValue(String value) {
    return Arrays.stream(values())
        .filter(scope -> scope.value.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown resource scope: " + value));
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
