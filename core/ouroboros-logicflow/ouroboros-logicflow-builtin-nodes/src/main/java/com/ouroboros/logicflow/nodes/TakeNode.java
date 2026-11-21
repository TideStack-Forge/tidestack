package com.ouroboros.logicflow.nodes;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class TakeNode extends AbstractNode<Object> {
  int index;

  public TakeNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    index = meta.getExtraProp(Integer.class, "index").orElse(0);
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    if (inputs.isEmpty()) {
      throw new IllegalArgumentException("take节点的输入不能为空");
    }
    if (inputs.size() > 2) {
      throw new IllegalArgumentException("take节点的输入不能超过2个");
    }
    var value = inputs.size() == 1
        ? takeData(inputs.get(0))
        : takeData(inputs.get(0), inputs.get(1));
    return Collections.singletonList(value);
  }

  protected Object takeData(Object input) {
    if (input instanceof Collection<?> inputCollection) {
      return inputCollection.stream().skip(index).findFirst().orElse(null);
    }
    return input;
  }

  protected Object takeData(Object input, Object index) {
    if (!(index instanceof Number indexNum)) {
      throw new IllegalArgumentException("take节点的第二个输入必须为数字类型");
    }
    if (!(input instanceof Collection<?> inputCollection)) {
      throw new IllegalArgumentException("take节点的第一个输入必须为集合类型");
    }
    return inputCollection.stream().skip(indexNum.intValue()).findFirst().orElse(null);
  }

  @Override
  protected Integer maxInputPortCount() {
    return 2;
  }

  public static class Builder implements NodeBuilder<TakeNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "take".equalsIgnoreCase(meta.getType());
    }

    @Override
    public TakeNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new TakeNode(owner, metadata);
    }
  }
}
