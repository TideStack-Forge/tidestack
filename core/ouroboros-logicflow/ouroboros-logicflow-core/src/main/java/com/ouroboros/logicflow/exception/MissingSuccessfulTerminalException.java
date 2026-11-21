package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class MissingSuccessfulTerminalException extends FlowDesignException {
  public MissingSuccessfulTerminalException(FlowModelMeta model) {
    super("流程缺少可达的成功终点，必须存在可达的 exitNode 或 ReturnNode", model);
  }
}
