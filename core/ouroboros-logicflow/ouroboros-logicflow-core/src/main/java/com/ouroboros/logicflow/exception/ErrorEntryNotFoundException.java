package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class ErrorEntryNotFoundException extends FlowDesignException {
  public ErrorEntryNotFoundException(FlowModelMeta model) {
    super("流程异常入口节点不存在或未启用", model);
  }
}
