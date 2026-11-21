package com.ouroboros.logicflow.support;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;

public class BrokenSelectiveTestNodeBuilder implements NodeBuilder<BrokenSelectiveTestNode> {
  @Override
  public Boolean support(FlowNodeMeta meta) {
    return "brokenSelectiveTestNode".equalsIgnoreCase(meta.getType());
  }

  @Override
  public BrokenSelectiveTestNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
    return new BrokenSelectiveTestNode(owner, metadata);
  }
}
