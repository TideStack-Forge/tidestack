package com.ouroboros.logicflow.exception;

import java.util.List;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class ProblematicNodesDetectedException extends FlowDesignException {
  private final List<NodeDesignException> nodeDesignExceptionList;

  public ProblematicNodesDetectedException(FlowModelMeta model, List<NodeDesignException> nodeDesignExceptionList) {
    super("存在有问题的节点", model);
    this.nodeDesignExceptionList = nodeDesignExceptionList;
  }

  public List<NodeDesignException> getNodeDesignExceptionList() {
    return nodeDesignExceptionList;
  }
}
