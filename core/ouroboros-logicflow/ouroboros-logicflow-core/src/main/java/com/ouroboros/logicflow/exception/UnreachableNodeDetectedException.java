package com.ouroboros.logicflow.exception;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class UnreachableNodeDetectedException extends FlowDesignException {
  private final List<String> nodeNames;

  public UnreachableNodeDetectedException(FlowModelMeta model, Collection<String> nodeNames) {
    super("存在从入口节点不可达的已启用节点: " + String.join(", ", nodeNames), model);
    this.nodeNames = nodeNames == null ? new ArrayList<>() : new ArrayList<>(nodeNames);
  }

  public List<String> getNodeNames() {
    return Collections.unmodifiableList(nodeNames);
  }
}
