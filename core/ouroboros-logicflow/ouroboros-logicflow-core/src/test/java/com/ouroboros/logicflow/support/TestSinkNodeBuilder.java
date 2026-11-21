package com.ouroboros.logicflow.support;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;

public class TestSinkNodeBuilder implements NodeBuilder<TestSinkNode> {
  @Override
  public Boolean support(FlowNodeMeta meta) {
    return "testSink".equalsIgnoreCase(meta.getType());
  }

  @Override
  public TestSinkNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
    return new TestSinkNode(owner, metadata);
  }
}
