package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class ReturnNode extends AbstractTerminalNode {
  public ReturnNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    return Collections.singletonList(inputs.isEmpty() ? null : inputs.get(0));
  }

  @Override
  protected Integer minInputPortCount() {
    return 1;
  }

  @Override
  protected Integer maxInputPortCount() {
    return 1;
  }

  @Override
  protected Integer minOutputPortCount() {
    return 0;
  }

  @Override
  protected Integer maxOutputPortCount() {
    return 0;
  }

  public static class Builder implements NodeBuilder<ReturnNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "return".equalsIgnoreCase(meta.getType());
    }

    @Override
    public ReturnNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new ReturnNode(owner, metadata);
    }
  }
}
