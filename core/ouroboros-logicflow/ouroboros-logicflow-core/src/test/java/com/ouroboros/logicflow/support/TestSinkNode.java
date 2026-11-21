package com.ouroboros.logicflow.support;

import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;

public class TestSinkNode extends AbstractNode<Object> {
  public TestSinkNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected Integer maxOutputPortCount() {
    return 0;
  }

  @Override
  protected Integer minOutputPortCount() {
    return 0;
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    return Collections.emptyList();
  }
}
