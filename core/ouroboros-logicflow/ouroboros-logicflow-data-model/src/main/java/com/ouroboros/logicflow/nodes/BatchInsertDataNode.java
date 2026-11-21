package com.ouroboros.logicflow.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class BatchInsertDataNode extends AbstractDataNode<Object, List<Map<String, Object>>> {
  private final String resultWrapperKey;
  private final Integer batchSize;

  public BatchInsertDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
    this.resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey")
        .map(String::trim)
        .orElse("");
    this.batchSize = meta.getExtraProp(Integer.class, "batchSize")
        .orElse(0);
  }

  @Override
  protected Either<FlowRuntimeException, Object> doExecute(List<Map<String, Object>> input, DataModel dataModel, List<Object> rawInputs) {
    var batchSize = this.batchSize <= 0 ? input.size() : this.batchSize;
    var partition = partition(input, batchSize);

    // 分批插入数据
    var batchInsertResult = partition.stream()
        .map(dataModel::batchInsert)
        .collect(Collectors.toList());

    // 获取错误信息
    var errors = batchInsertResult.stream()
        .filter(Try::isFailure)
        .map(Try::getCause)
        .collect(Collectors.toList());

    // 如果存在错误直接返回错误
    if (!errors.isEmpty()) {
      return Either.left(new FlowRuntimeException("批量插入数据失败", errors.get(0), this, rawInputs));
    }

    // 获取结果
    var result = batchInsertResult.stream()
        .filter(Try::isSuccess)
        .map(Try::get)
        .flatMap(RecordList::stream)
        .collect(Collectors.toList());

    if (resultWrapperKey.isEmpty()) {
      return Either.right(result);
    }

    return Either.right(Collections.singletonMap(resultWrapperKey, result));
  }

  private List<List<Map<String, Object>>> partition(List<Map<String, Object>> input, int batchSize) {
    var result = new ArrayList<List<Map<String, Object>>>();
    for (var start = 0; start < input.size(); start += batchSize) {
      result.add(input.subList(start, Math.min(start + batchSize, input.size())));
    }
    return result;
  }

  public static class Builder implements NodeBuilder<BatchInsertDataNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "batchInsertData".equalsIgnoreCase(meta.getType());
    }

    @Override
    public BatchInsertDataNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new BatchInsertDataNode(owner, metadata, references);
    }
  }
}
