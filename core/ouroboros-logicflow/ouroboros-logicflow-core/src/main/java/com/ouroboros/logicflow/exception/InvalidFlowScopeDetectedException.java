package com.ouroboros.logicflow.exception;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class InvalidFlowScopeDetectedException extends FlowDesignException {
  public InvalidFlowScopeDetectedException(String message, FlowModelMeta model) {
    this(message, model, Collections.emptyList());
  }

  private final List<String> nodeNames;

  public InvalidFlowScopeDetectedException(String message, FlowModelMeta model, Collection<String> nodeNames) {
    super(message, model);
    this.nodeNames = nodeNames == null ? new ArrayList<>() : new ArrayList<>(nodeNames);
  }

  public List<String> getNodeNames() {
    return Collections.unmodifiableList(nodeNames);
  }
}
