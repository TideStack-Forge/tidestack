package com.ouroboros.logicflow.exception;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class InvalidLeafNodeDetectedException extends FlowDesignException {
  private final List<String> nodeNames;

  public InvalidLeafNodeDetectedException(FlowModelMeta model, Collection<String> nodeNames) {
    super("存在非法叶子节点，这些节点可达、已启用且具有输出能力，但没有任何下游连线: "
        + String.join(", ", nodeNames), model);
    this.nodeNames = nodeNames == null ? new ArrayList<>() : new ArrayList<>(nodeNames);
  }

  public List<String> getNodeNames() {
    return Collections.unmodifiableList(nodeNames);
  }
}
