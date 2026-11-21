package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class FlowDesignException extends DesignException {
  private final FlowModelMeta model;

  public FlowDesignException(String message, FlowModelMeta model) {
    super(message);
    this.model = model;
  }

  public FlowModelMeta getModel() {
    return model;
  }
}
