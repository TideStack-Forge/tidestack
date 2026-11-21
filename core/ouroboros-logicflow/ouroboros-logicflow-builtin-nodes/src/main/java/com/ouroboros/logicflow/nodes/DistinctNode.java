package com.ouroboros.logicflow.nodes;

import static com.ouroboros.logicflow.util.NodeUtil.buildIsEqualPredicate;
import static com.ouroboros.logicflow.util.NodeUtil.distinctBy;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class DistinctNode extends AbstractSimpleNode<List<?>> {
  final BiPredicate<Object, Object> isEqualPredicate;
  final private String isEqualExpr;

  protected DistinctNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    isEqualExpr = meta.getExtraProp(String.class, "isEqualExpr")
        .map(String::trim)
        .orElse("");
    isEqualPredicate = buildIsEqualPredicate(isEqualExpr);
  }

  @Override
  protected List<?> doExecute(Object input) {
    if (!(input instanceof Collection<?> coll)) {
      throw new FlowRuntimeException("去重节点的输入必须是集合", null, this, Collections.singletonList(input));
    }
    if (isEqualExpr.isEmpty()) {
      return coll.stream()
          .distinct()
          .collect(Collectors.toList());
    } else {
      try {
        // 通过compareFunction比较去重
        return coll.stream()
            .filter(distinctBy(isEqualPredicate))
            .collect(Collectors.toList());
      } catch (RuntimeException e) {
        throw new FlowRuntimeException("进行去重对比时发生错误", e.getCause(), this, Collections.singletonList(input));
      }
    }
  }

  public static class Builder implements NodeBuilder<DistinctNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "distinct".equalsIgnoreCase(meta.getType());
    }

    @Override
    public DistinctNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new DistinctNode(owner, metadata);
    }
  }
}
