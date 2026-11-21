package com.ouroboros.logicflow.nodes;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelReferences;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class CallFlowNode extends AbstractNode<Object> {
  final FlowModel flowModel;
  final Boolean executeForEachItem;
  final String resultWrapperKey;

  public CallFlowNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta);
    var flowName = meta.getExtraProp(String.class, "flowName")
        .map(String::trim)
        .orElse("");
    if (flowName.isEmpty()) {
      throw new NodeDesignException("流程调用节点的流程名未配置", meta, "flowName");
    }
    this.resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey").orElse("");
    var currentFlowName = flowModel == null || flowModel.getName() == null ? "" : flowModel.getName().trim();
    if (!currentFlowName.isEmpty() && currentFlowName.equalsIgnoreCase(flowName)) {
      addError(new NodeDesignException("流程调用节点不能引用当前流程自身: " + flowName, meta, "flowName"));
      this.flowModel = null;
    } else {
      this.flowModel = references.resolve(FlowModelReferences.KIND, flowName).fold(
          resolved -> resolved,
          () -> {
            addError(new NodeDesignException("流程调用节点引用的流程不存在或未启用: " + flowName, meta, "flowName"));
            return null;
          },
          reason -> {
            addError(new NodeDesignException(
                "流程引用目录不可用（" + reason + "），无法确认流程: " + flowName,
                meta,
                "flowName"
            ));
            return null;
          }
      );
    }
    this.executeForEachItem = meta.getExtraProp(Boolean.class, "executeForEachItem")
        .orElse(false);
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    // 将结果包装返回
    return Collections.singletonList(resultWrapperKey.isEmpty()
        ? doExecuteFlow(inputs)
        : Collections.singletonMap(resultWrapperKey, doExecuteFlow(inputs)));
  }

  private Object doExecuteFlow(List<Object> inputs) {
    if (executeForEachItem) {
      return doExecuteForEach(inputs);
    }

    // 否则，直接执行流程
    var result = flowModel.execute(inputs.toArray())
        .getOrElseThrow(e -> new FlowRuntimeException("流程调用节点执行失败", e, this, inputs));

    // 否则，直接返回结果
    return result;
  }

  private List<Object> doExecuteForEach(List<Object> inputs) {
    if (inputs.isEmpty()) {
      throw new FlowRuntimeException("流程调用节点逐项执行时缺少集合输入", this, inputs);
    }
    var collectionInput = inputs.get(0);
    if (collectionInput == null) {
      throw new FlowRuntimeException("流程调用节点逐项执行时输入不能为null", this, inputs);
    }
    if (!(collectionInput instanceof Collection<?> coll)) {
      throw new FlowRuntimeException("流程调用节点逐项执行时输入必须为集合类型", this, inputs);
    }
    return coll.stream()
        .map(item -> flowModel.execute(item)
            .getOrElseThrow(e -> new FlowRuntimeException("流程调用节点执行失败", e, this, inputs)))
        .collect(Collectors.toList());
  }

  public static class Builder implements NodeBuilder<CallFlowNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "callFlow".equalsIgnoreCase(meta.getType());
    }

    @Override
    public CallFlowNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new CallFlowNode(owner, metadata, references);
    }
  }
}
