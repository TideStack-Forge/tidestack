package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class InputPortConflictDetectedException extends FlowDesignException {
  public InputPortConflictDetectedException(FlowModelMeta model) {
    super("存在输入端口冲突", model);
  }
}
