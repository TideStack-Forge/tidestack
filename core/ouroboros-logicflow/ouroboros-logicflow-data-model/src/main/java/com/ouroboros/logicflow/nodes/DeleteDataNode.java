package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.vavr.control.Either;
import io.vavr.control.Option;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class DeleteDataNode extends AbstractDataNode<Object, Object> {
  private final String resultWrapperKey;
  private final boolean returnDeletedData;

  public DeleteDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
    this.resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey")
        .map(String::trim)
        .orElse("");
    this.returnDeletedData = meta.getExtraProp(Boolean.class, "returnDeletedData")
        .orElse(false);
  }

  // TODO: 支持指定返回值，默认是返回删除的行数，允许指定返回被删除的数据
  @Override
  @SuppressWarnings({"unchecked"})
  protected Either<FlowRuntimeException, Object> doExecute(Object input, DataModel dataModel, List<Object> rawInputs) {
    Function<Throwable, FlowRuntimeException> exceptionMapper = e -> new FlowRuntimeException("删除数据执行失败", e, this, rawInputs);
    // 如果是字符串或者数字，根据主键删除
    Either<? extends Throwable, ?> executeResult;
    if (input instanceof CharSequence || input instanceof Number) {
      executeResult = executeDeleteOne(input);
    } else if (input instanceof List<?> list) {
      executeResult = executeDeleteMany(list);
    } else if (input instanceof Map<?, ?> map) {
      executeResult = executeDeleteWhere((Map<String, Object>) map);
    } else {
      return Either.left(new FlowRuntimeException("删除数据失败，不支持的数据类型", this, rawInputs));
    }
    return executeResult
        .mapLeft(exceptionMapper)
        .map(result -> resultWrapperKey.isEmpty() ? result : Collections.singletonMap(resultWrapperKey, result));
  }

  private Either<? extends Throwable, ?> executeDeleteOne(Object id) {
    var willBeDeleted = returnDeletedData ? dataModel.get(id) : null;
    var deleteResult = dataModel.delete(id);
    return returnDeletedData ? deleteResult.flatMap(r -> willBeDeleted).toEither() : deleteResult.toEither();
  }

  @SuppressWarnings("unchecked")
  private Either<? extends Throwable, ?> executeDeleteMany(List<?> ids) {
    var willBeDeleted = returnDeletedData ? dataModel.query(ids) : null;
    var deleteResult = dataModel.delete(ids);
    return returnDeletedData ? deleteResult.flatMap(r -> willBeDeleted).toEither() : deleteResult.toEither();
  }

  @SuppressWarnings({"unchecked"})
  private Either<? extends Throwable, ?> executeDeleteWhere(Map<String, Object> map) {
    var where = (Map<String, Object>) Option.of(map.get("where"))
        .orElse(() -> Option.of(map.get("WHERE")))
        .orElse(() -> Option.of(map.get("Where")))
        .filter(Map.class::isInstance)
        .getOrElse(map);
    var isSingleRecord = isSingleRecordCondition(where);
    var willBeDeleted = returnDeletedData
        ? dataModel.query(Collections.singletonMap("WHERE", where))
        .map(result -> {
          if (!isSingleRecord) {
            return result;
          }
          return result.isEmpty() ? null : result.get(0);
        })
        : null;
    var deleteResult = dataModel.delete(where);
    return returnDeletedData ? deleteResult.flatMap(r -> willBeDeleted).toEither() : deleteResult.toEither();
  }

  public static class Builder implements NodeBuilder<DeleteDataNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "deleteData".equalsIgnoreCase(meta.getType());
    }

    @Override
    public DeleteDataNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new DeleteDataNode(owner, metadata, references);
    }
  }
}
