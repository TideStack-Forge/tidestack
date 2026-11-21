package com.ouroboros.logicflow.nodes;

import java.util.*;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.support.LogicFlowMaps;

public class OmitNode extends AbstractNode<Object> {
  final Set<String> omitFields;

  protected OmitNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    omitFields = meta.getExtraProp(List.class, "omitFields")
        .map(l -> ((List<?>) l).stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.toSet()))
        .orElseGet(() -> Collections.emptySet());
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    if (inputs.size() == 1) {
      return Collections.singletonList(omitData(inputs.get(0)));
    }
    if (inputs.size() == 2) {
      var data = inputs.get(0);
      var omitFields = inputs.get(1);
      if (!(omitFields instanceof Collection<?> coll)) {
        throw new FlowRuntimeException("omit节点的第二个输入必须为集合类型", this, inputs);
      }
      return Collections.singletonList(omitData(data, coll));
    }
    throw new FlowRuntimeException("omit节点的输入必须为1个或2个", this, inputs);
  }

  protected Object omitData(Object input) {
    if (input instanceof Map<?, ?> map) {
      return omitMap(map, omitFields);
    }
    if (input instanceof Collection<?> list) {
      return omitList(list, omitFields);
    }
    return input;
  }

  protected Object omitData(Object input, Collection<?> omitFields) {
    if (input instanceof Map<?, ?> map) {
      return omitMap(map, omitFields);
    }
    if (input instanceof Collection<?> list) {
      return omitList(list, omitFields);
    }
    return input;
  }

  @Override
  protected Integer maxInputPortCount() {
    return 2;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object omitMap(Map<?, ?> map, Collection<?> omitFields) {
    return LogicFlowMaps.omit(map, (Collection) omitFields);
  }

  private Object omitList(Collection<?> list, Collection<?> omitFields) {
    return list.stream()
        .filter(Objects::nonNull)
        .map(item -> {
          if (item instanceof Map<?, ?> map) {
            return omitMap(map, omitFields);
          }
          return item;
        })
        .collect(Collectors.toList());
  }

  public static class Builder implements NodeBuilder<OmitNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "omit".equalsIgnoreCase(meta.getType());
    }

    @Override
    public OmitNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new OmitNode(owner, metadata);
    }
  }
}
