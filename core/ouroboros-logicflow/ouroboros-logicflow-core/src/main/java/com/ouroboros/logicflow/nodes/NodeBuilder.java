package com.ouroboros.logicflow.nodes;

import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public interface NodeBuilder<T extends FlowNode> {
  Boolean support(FlowNodeMeta meta);

  T build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references);
}
