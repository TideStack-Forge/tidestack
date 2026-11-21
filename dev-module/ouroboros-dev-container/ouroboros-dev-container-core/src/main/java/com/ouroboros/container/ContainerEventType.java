package com.ouroboros.container;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author liansz
 **/
public enum ContainerEventType {

  CONTAINER_CREATE("containerCreate"),

  CONTAINER_DELETE("containerDelete"),

  CONTAINER_STATUS_CHANGE("containerStatusChange");

  private final String value;

  ContainerEventType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static ContainerEventType fromValue(String value) {
    return Arrays.stream(ContainerEventType.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
