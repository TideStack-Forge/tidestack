package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.vavr.control.Either;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class GetOneDataNode extends AbstractDataNode<Object, Map<String, Object>> {
  private final String resultWrapperKey;

  public GetOneDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
    this.resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey")
        .map(String::trim)
        .orElse("");
  }

  @Override
  protected Either<FlowRuntimeException, Object> doExecute(Map<String, Object> input, DataModel dataModel, List<Object> rawInputs) {
    Either<FlowRuntimeException, RecordList> recordListEither = dataModel.query(input)
        .toEither()
        .mapLeft(e -> new FlowRuntimeException("查询数据失败", e, this, rawInputs));

    if (recordListEither.isLeft()) {
      return Either.left(recordListEither.getLeft());
    }
    RecordList recordList = recordListEither.get();

    if (recordList.size() > 1) {
      return Either.left(new FlowRuntimeException("查询到多条数据", this, rawInputs));
    }

    var result = recordList.isEmpty() ? null : recordList.get(0);

    return Either.right(resultWrapperKey.isEmpty() ? result : Collections.singletonMap(resultWrapperKey, result));
  }

  public static class Builder implements NodeBuilder<GetOneDataNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "getOneData".equalsIgnoreCase(meta.getType());
    }

    @Override
    public GetOneDataNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new GetOneDataNode(owner, metadata, references);
    }
  }
}
