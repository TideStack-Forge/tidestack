package com.ouroboros.logicflow.nodes;

import java.util.Objects;

public record NodeReferenceKind<T>(String name, Class<T> valueType) {
  public NodeReferenceKind {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("节点引用类型名称不能为空");
    }
    name = name.trim();
    Objects.requireNonNull(valueType, "valueType");
  }
}
