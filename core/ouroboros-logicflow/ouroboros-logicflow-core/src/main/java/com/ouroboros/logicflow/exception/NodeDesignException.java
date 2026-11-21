package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowNodeMeta;

public class NodeDesignException extends DesignException {
  private final FlowNodeMeta node;
  private final String fieldPath;
  private final String portKind;
  private final Integer portIndex;

  public NodeDesignException(String message, FlowNodeMeta node) {
    super(message);
    this.node = node;
    this.fieldPath = null;
    this.portKind = null;
    this.portIndex = null;
  }

  public NodeDesignException(String message, FlowNodeMeta node, String fieldPath) {
    this(message, node, fieldPath, null, null);
  }

  public NodeDesignException(String message, FlowNodeMeta node, String fieldPath, String portKind, Integer portIndex) {
    super(message);
    this.node = node;
    this.fieldPath = fieldPath;
    this.portKind = portKind;
    this.portIndex = portIndex;
  }

  public FlowNodeMeta getNode() {
    return node;
  }

  public String getFieldPath() {
    return fieldPath;
  }

  public String getPortKind() {
    return portKind;
  }

  public Integer getPortIndex() {
    return portIndex;
  }
}
