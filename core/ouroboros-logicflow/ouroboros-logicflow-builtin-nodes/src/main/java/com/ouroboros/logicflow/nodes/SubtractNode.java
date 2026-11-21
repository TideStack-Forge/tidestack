package com.ouroboros.logicflow.nodes;

import static com.ouroboros.logicflow.util.NodeUtil.buildIsEqualPredicate;

import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class SubtractNode extends AbstractNode<List<?>> {
  final BiPredicate<Object, Object> isEqualPredicate;
  final private String isEqualExpr;

  protected SubtractNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    isEqualExpr = meta.getExtraProp(String.class, "isEqualExpr")
        .map(String::trim)
        .orElse("");
    isEqualPredicate = buildIsEqualPredicate(isEqualExpr);
  }

  @Override
  protected List<List<?>> doExecute(List<Object> inputs) {
    if (inputs.size() != 2) {
      throw new FlowRuntimeException("差集节点输入端口数量必须为2", null, this, inputs);
    }
    var input1 = inputs.get(0);
    var input2 = inputs.get(1);
    if (input1 == null) {
      input1 = Collections.emptyList();
    }
    if (input2 == null) {
      input2 = Collections.emptyList();
    }

    if (!(input1 instanceof List<?> coll1) || !(input2 instanceof List<?> coll2)) {
      throw new FlowRuntimeException("差集节点输入必须为集合", null, this, inputs);
    }
    if (isEqualExpr.isEmpty()) {
      return Collections.singletonList(coll1.stream()
          .filter(item1 -> !coll2.contains(item1))
          .collect(Collectors.toList()));
    } else {
      try {
        // 通过compareFunction比较取差集
        return Collections.singletonList(coll1.stream()
            .filter(item1 -> coll2.stream().noneMatch(item2 -> isEqualPredicate.test(item1, item2)))
            .collect(Collectors.toList()));
      } catch (Exception e) {
        throw new FlowRuntimeException("进行差集对比时发生错误", e.getCause(), this, inputs);
      }
    }
  }

  @Override
  protected Integer maxInputPortCount() {
    return 2;
  }

  @Override
  protected Integer minInputPortCount() {
    return 2;
  }

  public static class Builder implements NodeBuilder<SubtractNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "subtract".equalsIgnoreCase(meta.getType());
    }

    @Override
    public SubtractNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new SubtractNode(owner, metadata);
    }
  }
}
