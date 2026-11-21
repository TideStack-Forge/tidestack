package com.ouroboros.logicflow.support;

import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;

public class MultiInputTestNode extends AbstractNode<Object> {
  public MultiInputTestNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected Integer minInputPortCount() {
    return 2;
  }

  @Override
  protected Integer maxInputPortCount() {
    return 2;
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    return Collections.singletonList(inputs.isEmpty() ? null : inputs.get(0));
  }
}
