package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class UnconnectedInputPortException extends FlowDesignException {
  public UnconnectedInputPortException(FlowModelMeta model) {
    super("存在未连接的输入端口", model);
  }
}
