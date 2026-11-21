package com.ouroboros.logicflow.exception;

import java.util.List;

import com.ouroboros.logicflow.model.FlowNode;

public class FlowRuntimeException extends RuntimeException {
  private final FlowNode node;
  private final List<Object> inputs;

  public FlowRuntimeException(String message, Throwable cause, FlowNode node, List<Object> inputs) {
    super(message, cause);
    this.node = node;
    this.inputs = inputs;
  }

  public FlowRuntimeException(String message, FlowNode node, List<Object> inputs) {
    super(message);
    this.node = node;
    this.inputs = inputs;
  }

  public FlowNode getNode() {
    return node;
  }

  public List<Object> getInputs() {
    return inputs;
  }
}
