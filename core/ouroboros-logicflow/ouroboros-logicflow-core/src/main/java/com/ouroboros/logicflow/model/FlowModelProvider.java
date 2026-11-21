package com.ouroboros.logicflow.model;

import java.util.Map;
import java.util.Optional;

public interface FlowModelProvider {
  Optional<FlowModel> getFlowModel(String flowName);

  Map<String, FlowModel> getFlowModelMap();
  default Optional<FlowModelPublication> publication() {
    return Optional.empty();
  }

  default Optional<FlowModelRefreshSupport> refreshSupport() {
    return Optional.empty();
  }
}
