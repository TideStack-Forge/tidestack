package com.ouroboros.logicflow.nodes;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.results.FlowOutcome;

public abstract class AbstractTerminalNode extends AbstractNode<Object> {
  protected AbstractTerminalNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  public FlowOutcome.Kind getSuccessfulOutcomeKind() {
    return FlowOutcome.Kind.RETURN;
  }
}
