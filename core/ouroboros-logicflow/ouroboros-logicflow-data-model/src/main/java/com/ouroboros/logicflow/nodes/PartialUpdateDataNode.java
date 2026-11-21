package com.ouroboros.logicflow.nodes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vavr.control.Either;
import io.vavr.control.Option;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class PartialUpdateDataNode extends UpdateDataNode {
  public PartialUpdateDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
  }

  // TODO: 支持指定返回值类型，默认是返回更新的行数，允许指定返回更新后的数据
  @Override
  protected Either<FlowRuntimeException, Object> doExecute(Map<String, Object> input, DataModel dataModel, List<Object> rawInputs) {
    var where = Option.of(input.get("where"))
        .orElse(() -> Option.of(input.get("WHERE")))
        .orElse(() -> Option.of(input.get("Where")))
        .getOrElse((Object) null);
    var data = Option.of(input.get("data"))
        .orElse(() -> Option.of(input.get("DATA")))
        .orElse(() -> Option.of(input.get("Data")))
        .orElse(() -> Option.of(input.get("set")))
        .orElse(() -> Option.of(input.get("SET")))
        .orElse(() -> Option.of(input.get("Set")))
        .orElse(() -> Option.of(input.get("value")))
        .orElse(() -> Option.of(input.get("VALUE")))
        .orElse(() -> Option.of(input.get("Value")))
        .getOrElse((Object) null);
    if (where == null || data == null) {
      return Either.left(new FlowRuntimeException("更新数据失败，输入数据中缺少条件参数", this, rawInputs));
    }
    if (!(data instanceof Map<?, ?> dataMap)) {
      return Either.left(new FlowRuntimeException("更新数据失败，输入数据中的data参数不是Map类型", this, rawInputs));
    }
    var partialData = dataMap.entrySet().stream()
        .filter(e -> e.getKey() != null && e.getValue() != null)
        .collect(Collectors.toMap(
            entry -> entry.getKey().toString(),
            entry -> (Object) entry.getValue(),
            (v1, v2) -> v2));
    return super.doUpdate(where,
        partialData,
        e -> new FlowRuntimeException("部分更新数据执行失败", e, this, rawInputs),
        () -> new FlowRuntimeException("部分更新数据失败，不支持的数据类型", this, rawInputs));
  }

  public static class Builder implements NodeBuilder<PartialUpdateDataNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "partialUpdateData".equalsIgnoreCase(meta.getType());
    }

    @Override
    public PartialUpdateDataNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new PartialUpdateDataNode(owner, metadata, references);
    }
  }
}
