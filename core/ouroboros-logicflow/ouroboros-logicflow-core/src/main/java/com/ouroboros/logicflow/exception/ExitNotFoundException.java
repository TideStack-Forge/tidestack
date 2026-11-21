package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class ExitNotFoundException extends FlowDesignException {
  public ExitNotFoundException(FlowModelMeta model) {
    super("出口节点未找到", model);
  }

}
