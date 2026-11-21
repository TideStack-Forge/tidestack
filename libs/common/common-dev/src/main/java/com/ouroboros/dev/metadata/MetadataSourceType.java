package com.ouroboros.dev.metadata;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author liansz
 **/
public enum MetadataSourceType {

  LOW_CODE("low-code"),
  PRO_CODE("pro-code"),
  UNKNOWN("unknown");

  private final String value;

  MetadataSourceType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static MetadataSourceType fromValue(String value) {
    return Arrays.stream(MetadataSourceType.values()).filter(e ->
        e.getValue().equals(value)
    ).findFirst().orElse(null);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
