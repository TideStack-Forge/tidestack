package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class SuccessfulTerminalConflictDetectedException extends FlowDesignException {
  public SuccessfulTerminalConflictDetectedException(FlowModelMeta model) {
    super("流程存在多个可能同时命中的成功终点", model);
  }
}
