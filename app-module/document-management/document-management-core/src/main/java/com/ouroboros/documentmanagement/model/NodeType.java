package com.ouroboros.documentmanagement.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 节点类型枚举
 */
public enum NodeType {
  DIRECTORY("DIRECTORY"),
  DOCUMENT("DOCUMENT");
  final String code;
  NodeType(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  @JsonCreator
  public static NodeType fromCode(String code) {
    for (NodeType value : values()) {
      if (value.code.equalsIgnoreCase(code)) {
        return value;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "NodeType{" +
        "code='" + code + '\'' +
        '}';
  }
}
