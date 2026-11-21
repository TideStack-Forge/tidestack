package com.ouroboros.container;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author liansz
 **/
public enum ContainerStatus {

  NOT_FOUND("notFound"),

  STOPPED("stopped"),

  LAUNCHING("launching"),

  RUNNING("running"),

  PAUSED("paused"),

  UNKNOWN("unknown");

  private final String value;

  ContainerStatus(String value) {
    this.value = value;
  }

  @JsonCreator
  public static ContainerStatus fromValue(String value) {
    return Arrays.stream(ContainerStatus.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(UNKNOWN);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
