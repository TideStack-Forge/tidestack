package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class CyclicDetectedException extends FlowDesignException {
  public CyclicDetectedException(FlowModelMeta model) {
    super("流程存在环路", model);
  }
}
