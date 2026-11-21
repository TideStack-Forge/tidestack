package com.ouroboros.logicflow.model;

import com.ouroboros.logicflow.nodes.NodeReferenceKind;

public final class FlowModelReferences {
  public static final NodeReferenceKind<FlowModel> KIND =
      new NodeReferenceKind<>("logic-flow", FlowModel.class);

  private FlowModelReferences() {
  }
}
