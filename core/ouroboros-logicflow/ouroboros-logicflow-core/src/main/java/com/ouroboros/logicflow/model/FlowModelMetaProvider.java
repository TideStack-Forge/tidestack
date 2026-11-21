package com.ouroboros.logicflow.model;

import java.util.Map;
import java.util.Optional;

public interface FlowModelMetaProvider {
  Optional<FlowModelMeta> getFlowModelMeta(String flowName);

  Map<String, FlowModelMeta> getFlowModelMetas();

  /** Unadapted sources remain observable, but cannot authorize runtime editing. */
  default FlowModelSource observeSource() {
    var priority = getClass().getAnnotation(jakarta.annotation.Priority.class);
    return new FlowModelSource(getClass().getName(), priority == null ? 0 : priority.value(),
        false, false, true, java.util.List.of("来源未声明，运行时只读"),
        FlowModelDefinitions.freeze(getFlowModelMetas()), "");
  }
}
