package com.ouroboros.logicflow.dev.vo;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class LogicFlowModelReadEnvelope {
  private final FlowModelMeta flowModel;
  private final LogicFlowValidationSnapshot validationSnapshot;

  public LogicFlowModelReadEnvelope(FlowModelMeta flowModel, LogicFlowValidationSnapshot validationSnapshot) {
    this.flowModel = flowModel;
    this.validationSnapshot = validationSnapshot;
  }

  public FlowModelMeta getFlowModel() {
    return flowModel;
  }

  public LogicFlowValidationSnapshot getValidationSnapshot() {
    return validationSnapshot;
  }
}
