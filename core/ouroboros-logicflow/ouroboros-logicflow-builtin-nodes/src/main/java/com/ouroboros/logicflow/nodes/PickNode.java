package com.ouroboros.logicflow.nodes;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.support.LogicFlowMaps;

public class PickNode extends AbstractNode<Object> {
  final String pickKey;
  private final Boolean pickEachItem;

  private final Function<List<Object>, Object> pickFunction;

  protected PickNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    pickKey = meta.getExtraProp(String.class, "key").orElse("");
    pickEachItem = meta.getExtraProp(Boolean.class, "pickEachItem").orElse(false);
    pickFunction = buildPickFunction();
  }

  private Function<List<Object>, Object> buildPickFunction() {
    if (pickEachItem) {
      return inputs -> {
        if (inputs.isEmpty()) {
          throw new IllegalArgumentException("pick节点的输入不能为空");
        }
        var items = inputs.get(0) instanceof Collection<?> coll
            ? coll
            : Collections.singletonList(inputs.get(0));
        var pickKey = inputs.size() > 1 ? inputs.get(1) : this.pickKey;
        return items.stream()
            .map(item -> pickData(item, pickKey))
            .collect(Collectors.toList());
      };
    }
    return inputs -> {
      var pickKey = inputs.size() > 1 ? inputs.get(1) : this.pickKey;
      return pickData(inputs.get(0), pickKey);
    };
  }


  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    if (inputs.isEmpty()) {
      throw new IllegalArgumentException("pick节点的输入不能为空");
    }
    if (inputs.size() > 2) {
      throw new IllegalArgumentException("pick节点的输入不能超过2个");
    }

    var value = pickFunction.apply(inputs);
    return Collections.singletonList(value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected Object pickData(Object data, Object key) {
    if (!(data instanceof Map dataMap)) {
      throw new IllegalArgumentException("pick节点的第一个输入必须为Map类型");
    }
    if (!(key instanceof CharSequence keyStr)) {
      throw new IllegalArgumentException("pick节点的第二个输入必须为字符串类型");
    }
    return LogicFlowMaps.Hierarchical.getValue(dataMap, keyStr.toString()).orElse(null);
  }

  @Override
  protected Integer maxInputPortCount() {
    return 2;
  }

  public static class Builder implements NodeBuilder<PickNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "pick".equalsIgnoreCase(meta.getType());
    }

    @Override
    public PickNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new PickNode(owner, metadata);
    }
  }
}
