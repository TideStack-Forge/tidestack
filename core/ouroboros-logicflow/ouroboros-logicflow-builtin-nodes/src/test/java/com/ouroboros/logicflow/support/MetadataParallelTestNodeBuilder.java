package com.ouroboros.logicflow.support;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;

public class MetadataParallelTestNodeBuilder implements NodeBuilder<MetadataParallelTestNode> {
  @Override
  public Boolean support(FlowNodeMeta meta) {
    return "metadataParallelTestNode".equalsIgnoreCase(meta.getType());
  }

  @Override
  public MetadataParallelTestNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
    return new MetadataParallelTestNode(owner, metadata);
  }
}
