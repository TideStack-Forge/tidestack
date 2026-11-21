package com.ouroboros.webapi.openapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * OpenAPI 3.0 参数位置
 */
public enum ParameterLocation {
  /**
   * 路径参数
   */
  PATH("path"),

  /**
   * 查询参数
   */
  QUERY("query"),

  /**
   * 请求头参数
   */
  HEADER("header"),

  /**
   * Cookie 参数
   */
  COOKIE("cookie");

  private final String value;

  ParameterLocation(String value) {
    this.value = value;
  }

  @JsonCreator
  public static ParameterLocation fromValue(String value) {
    for (ParameterLocation location : ParameterLocation.values()) {
      if (location.value.equals(value)) {
        return location;
      }
    }
    return null;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
