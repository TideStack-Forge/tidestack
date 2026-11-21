package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class NodeNotFoundException extends FlowDesignException {

  public NodeNotFoundException(String message, FlowModelMeta model) {
    super(message, model);
  }
}
