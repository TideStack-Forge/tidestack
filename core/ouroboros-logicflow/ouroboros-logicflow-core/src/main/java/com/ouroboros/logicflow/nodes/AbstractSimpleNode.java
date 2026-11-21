package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public abstract class AbstractSimpleNode<T> extends AbstractNode<T> {
  protected AbstractSimpleNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected List<T> doExecute(List<Object> inputs) {
    if (inputs.size() == 0) {
      throw new FlowRuntimeException("节点没有输入值", this, inputs);
    }
    var input = inputs.get(0);
    var output = doExecute(input);
    return Collections.singletonList(output);
  }

  protected abstract T doExecute(Object input);

  @Override
  protected Integer maxInputPortCount() {
    return 1;
  }
}
