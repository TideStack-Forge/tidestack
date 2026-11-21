package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class EntryNotFoundException extends FlowDesignException {
  public EntryNotFoundException(FlowModelMeta model) {
    super("入口节点未找到", model);
  }
}
