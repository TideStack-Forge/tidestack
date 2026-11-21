package com.ouroboros.logicflow.nodes;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class MutexNode extends AbstractSimpleNode<Object> {
  public MutexNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected Object doExecute(Object input) {
    return input;
  }

  public static class Builder implements NodeBuilder<MutexNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "mutex".equalsIgnoreCase(meta.getType());
    }

    @Override
    public MutexNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new MutexNode(owner, metadata);
    }
  }
}
