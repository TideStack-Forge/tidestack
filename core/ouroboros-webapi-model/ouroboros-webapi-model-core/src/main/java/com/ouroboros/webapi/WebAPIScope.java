package com.ouroboros.webapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WebAPIScope {
  PUBLIC("public", "/public-api"),
  OPEN("open", "/open-api"),
  INTERNAL("internal", "/api");
  final String value;
  final String prefix;

  WebAPIScope(String value, String prefix) {
    this.value = value;
    this.prefix = prefix;
  }

  @JsonCreator
  public static WebAPIScope fromValue(String value) {
    for (WebAPIScope type : WebAPIScope.values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    return null;
  }

  public static WebAPIScope fromPrefix(String prefix) {
    for (WebAPIScope type : WebAPIScope.values()) {
      if (type.prefix.equals(prefix)) {
        return type;
      }
    }
    return null;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public String getPrefix() {
    return prefix;
  }
}
