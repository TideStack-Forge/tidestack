package com.ouroboros.logicflow.exception;

import java.util.List;

import com.ouroboros.logicflow.model.FlowNode;

public class ValidationFailedException extends FlowRuntimeException {
  public ValidationFailedException(String message, FlowNode node, List<Object> inputs) {
    super(message, node, inputs);
  }
}
