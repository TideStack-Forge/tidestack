package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.vavr.control.Either;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class CountDataNode extends AbstractDataNode<Object, Map<String, Object>> {
  private final String resultWrapperKey;

  public CountDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
    resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey")
        .map(String::trim)
        .orElse("");
  }

  @Override
  protected Either<FlowRuntimeException, Object> doExecute(Map<String, Object> input, DataModel dataModel, List<Object> rawInputs) {
    var raw = new TreeMap<>(String::compareToIgnoreCase);
    raw.putAll(input);
    var where = raw.getOrDefault("where", raw);
    if (!(where instanceof Map<?, ?> whereMap)) {
      return Either.left(new FlowRuntimeException("统计数据失败，输入数据中的where参数不是Map类型", this, rawInputs));
    }
    return dataModel.count((Map<String, Object>) whereMap)
        .toEither()
        .mapLeft(e -> new FlowRuntimeException("统计数据失败", e, this, rawInputs))
        .map(count -> resultWrapperKey.isEmpty() ? count : Collections.singletonMap(resultWrapperKey, count));
  }

  public static class Builder implements NodeBuilder<CountDataNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "countData".equalsIgnoreCase(meta.getType());
    }

    @Override
    public CountDataNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new CountDataNode(owner, metadata, references);
    }
  }
}
