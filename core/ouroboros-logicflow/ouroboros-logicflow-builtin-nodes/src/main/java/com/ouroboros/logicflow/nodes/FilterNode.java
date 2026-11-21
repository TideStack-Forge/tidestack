package com.ouroboros.logicflow.nodes;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.expression.LogicFlowExpression;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.logicflow.util.NodeUtil;
import com.ouroboros.logicflow.util.LogicFlowValues;

public class FilterNode extends AbstractNode<Object> {
  private final String filterExpr;
  private final Predicate<Map<String, Object>> filterPredicate;

  protected FilterNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    filterExpr = meta.getExtraProp(String.class, "filterExpr").orElse("");
    filterPredicate = compilePredicate(filterExpr);
  }

  private Predicate<Map<String, Object>> compilePredicate(String filterExpr) {
    if (filterExpr.trim().isEmpty()) {
      return (context) -> true;
    }
    var wrappedExpr = LogicFlowExpression.wrapExpression(filterExpr);
    return (context) -> LogicFlowExpression.eval(wrappedExpr, context)
        .map(LogicFlowValues::toBoolean)
        .getOrElseThrow(throwable -> new RuntimeException(throwable));
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    var first = inputs.get(0);
    if (!(first instanceof Collection<?> coll)) {
      throw new FlowRuntimeException("节点的输入必须为集合类型", null, this, inputs);
    }

    // 没配置表达式就不过滤了，直接返回原集合
    if (filterExpr.isEmpty()) {
      return Collections.singletonList(coll);
    }

    final var context = NodeUtil.buildContextMap(inputs, getInputPorts().stream()
        .map(FlowNodePort::getName)
        .collect(Collectors.toList()));
    try {
      var filtered = new ArrayList<>();
      var index = 0;
      for (var item : coll) {
        var itemContext = new HashMap<String, Object>();
        if (item instanceof Map map) {
          itemContext.putAll(map);
        }
        itemContext.put("$0", item);
        itemContext.put("$index", index++);
        itemContext.put("$parentContext", context);
        if (filterPredicate.test(itemContext)) {
          filtered.add(item);
        }
      }
      return Collections.singletonList(filtered);
    } catch (Exception e) {
      throw new FlowRuntimeException("执行过滤时发生错误", e.getCause(), this, inputs);
    }
  }

  public static class Builder implements NodeBuilder<FilterNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "filter".equalsIgnoreCase(meta.getType());
    }

    @Override
    public FilterNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new FilterNode(owner, metadata);
    }
  }
}
