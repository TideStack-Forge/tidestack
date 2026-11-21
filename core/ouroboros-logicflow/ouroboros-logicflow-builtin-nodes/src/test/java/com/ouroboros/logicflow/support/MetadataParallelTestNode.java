package com.ouroboros.logicflow.support;

import java.util.Arrays;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;

public class MetadataParallelTestNode extends AbstractNode<Object> {
  public MetadataParallelTestNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected Integer minInputPortCount() {
    return 0;
  }

  @Override
  protected Integer minOutputPortCount() {
    return 2;
  }

  @Override
  protected Integer maxOutputPortCount() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    return Arrays.asList("left", "right");
  }
}
