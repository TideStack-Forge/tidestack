package com.ouroboros.logicflow.support;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;

public class MultiInputTestNodeBuilder implements NodeBuilder<MultiInputTestNode> {
  @Override
  public Boolean support(FlowNodeMeta meta) {
    return "multiInputTestNode".equalsIgnoreCase(meta.getType());
  }

  @Override
  public MultiInputTestNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
    return new MultiInputTestNode(owner, metadata);
  }
}
