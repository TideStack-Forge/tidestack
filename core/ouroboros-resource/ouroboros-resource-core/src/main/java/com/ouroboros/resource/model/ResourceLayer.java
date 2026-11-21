package com.ouroboros.resource.model;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Resource resolution layer.
 */
public enum ResourceLayer {

  APP_DEFAULT("app_default"),
  RUNTIME_OVERRIDE("runtime_override");

  private final String value;

  ResourceLayer(String value) {
    this.value = value;
  }

  @JsonCreator
  public static ResourceLayer fromValue(String value) {
    return Arrays.stream(values())
        .filter(layer -> layer.value.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown resource layer: " + value));
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
