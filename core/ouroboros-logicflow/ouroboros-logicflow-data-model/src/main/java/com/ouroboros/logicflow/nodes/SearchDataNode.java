package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.vavr.control.Either;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class SearchDataNode extends AbstractDataNode<Object, Map<String, Object>> {
  private final String resultWrapperKey;

  public SearchDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
    this.resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey")
        .map(String::trim)
        .orElse("");
  }

  @Override
  protected Either<FlowRuntimeException, Object> doExecute(Map<String, Object> input, DataModel dataModel, List<Object> rawInputs) {
    return dataModel.query(input)
        .toEither()
        .mapLeft(e -> new FlowRuntimeException("查询数据失败", e, this, rawInputs))
        .map(result -> resultWrapperKey.isEmpty() ? result : Collections.singletonMap(resultWrapperKey, result));
  }

  public static class Builder implements NodeBuilder<SearchDataNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "searchData".equalsIgnoreCase(meta.getType());
    }

    @Override
    public SearchDataNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new SearchDataNode(owner, metadata, references);
    }
  }
}
