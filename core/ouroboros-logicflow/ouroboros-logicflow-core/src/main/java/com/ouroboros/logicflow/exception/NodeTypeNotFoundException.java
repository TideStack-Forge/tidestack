package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowNodeMeta;

public class NodeTypeNotFoundException extends NodeDesignException {

  public NodeTypeNotFoundException(String message, FlowNodeMeta nodeMeta) {
    super(message, nodeMeta);
  }
}
