package com.ouroboros.configuration.metadata;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author: liansz
 */
public enum ConfigScope {

  /**
   * 前端
   */
  FRONTEND("frontend"),

  /**
   * 后端
   */
  BACKEND("backend");

  /**
   * 范围
   */
  private final String scope;

  ConfigScope(String scope) {
    this.scope = scope;
  }

  @JsonValue
  public String getValue() {
    return scope;
  }

  @JsonCreator
  public ConfigScope forValue(String scope) {
    return Arrays.stream(values())
        .filter(configScope -> configScope.name().equalsIgnoreCase(scope))
        .findFirst()
        .orElse(BACKEND);
  }
}
