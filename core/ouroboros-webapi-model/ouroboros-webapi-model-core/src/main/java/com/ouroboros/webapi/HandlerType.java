package com.ouroboros.webapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HandlerType {
  REQUEST_MAPPING("RequestMapping"),
  ROUTER_FUNCTION("RouterFunction"),
  LOGIC_FLOW("LogicFlow"),
  BUSINESS_ACTION("BusinessAction");
  final String value;

  HandlerType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static HandlerType fromValue(String value) {
    for (HandlerType type : HandlerType.values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    return null;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
