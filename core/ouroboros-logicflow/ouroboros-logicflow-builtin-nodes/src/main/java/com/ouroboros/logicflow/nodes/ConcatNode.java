package com.ouroboros.logicflow.nodes;

import static com.ouroboros.logicflow.util.NodeUtil.buildIsEqualPredicate;
import static com.ouroboros.logicflow.util.NodeUtil.distinctBy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.*;

public class ConcatNode extends AbstractNode<List<?>> {
  final List<FlowNodePort> outputPorts = Collections.singletonList(new DefaultFlowNodeOutPort(this, DataTypeEnum.ARRAY));
  final boolean distinct;
  final String isEqualExpr;
  final BiPredicate<Object, Object> isEqualPredicate;

  protected ConcatNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    distinct = meta.getExtraProp(Boolean.class, "distinct").orElse(false);
    isEqualExpr = meta.getExtraProp(String.class, "isEqualExpr")
        .map(String::trim)
        .orElse("");
    isEqualPredicate = buildIsEqualPredicate(isEqualExpr);
  }

  @Override
  protected List<List<?>> doExecute(List<Object> inputs) {
    var valueStream = inputs.stream()
        .flatMap(input -> input instanceof List<?> coll ? coll.stream() : Stream.of(input))
        .filter(Objects::nonNull);
    try {
      var value = ((distinct && isEqualExpr.isEmpty())
          ? valueStream.distinct()
          : valueStream.filter(distinctBy(isEqualPredicate))
      ).collect(Collectors.toList());
      return Collections.singletonList(value);
    } catch (Throwable e) {
      throw new FlowRuntimeException("进行去重对比时发生错误", e, this, inputs);
    }
  }

  @Override
  public List<FlowNodePort> getOutputPorts() {
    return outputPorts;
  }

  public static class Builder implements NodeBuilder<ConcatNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "concat".equalsIgnoreCase(meta.getType());
    }

    @Override
    public ConcatNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new ConcatNode(owner, metadata);
    }
  }
}
