package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.support.LogicFlowMaps;

public class MergeNode extends AbstractNode<Object> {
  boolean deepMerge;

  protected MergeNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    deepMerge = meta.getExtraProp(Boolean.class, "deepMerge").orElse(false);
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    if (inputs.stream().anyMatch(input -> !(input instanceof Map<?, ?>))) {
      throw new FlowRuntimeException("合并节点的输入必须都为Map类型", null, this, inputs);
    }
    var filtered = inputs.stream()
        .filter(Objects::nonNull)
        .filter(Map.class::isInstance)
        .filter(map -> !((Map<?, ?>) map).isEmpty())
        .collect(Collectors.toList());
    var merged = filtered.isEmpty()
        ? Collections.emptyMap()
        : filtered.size() == 1
        ? filtered.get(0)
        : deepMerge
        ? LogicFlowMaps.deepMerge(inputs.toArray(new Map[0]))
        : LogicFlowMaps.merge(inputs.toArray(new Map[0]));
    return Collections.singletonList(merged);
  }

  public static class Builder implements NodeBuilder<MergeNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "merge".equalsIgnoreCase(meta.getType());
    }

    @Override
    public MergeNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new MergeNode(owner, metadata);
    }
  }

}
