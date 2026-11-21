package com.ouroboros.logicflow.nodes;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.vavr.control.Either;
import io.vavr.control.Option;

import com.ouroboros.data.normalize.OperatorFactory;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class UpdateDataNode extends AbstractDataNode<Object, Map<String, Object>> {
  private final String resultWrapperKey;
  private final boolean returnUpdatedData;

  public UpdateDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
    this.resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey")
        .map(String::trim)
        .orElse("");
    this.returnUpdatedData = meta.getExtraProp(Boolean.class, "returnUpdatedData")
        .orElse(false);
  }

  // TODO: 支持指定返回值类型，默认是返回更新的行数，允许指定返回更新后的数据
  @SuppressWarnings("unchecked")
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
      return Either.left(new FlowRuntimeException("更新数据失败，输入数据中缺少参数", this, rawInputs));
    }
    if (!(data instanceof Map<?, ?> dataMap)) {
      return Either.left(new FlowRuntimeException("更新数据失败，输入数据中的data参数不是Map类型", this, rawInputs));
    }

    return doUpdate(where,
        (Map<String, Object>) dataMap,
        e -> new FlowRuntimeException("更新数据执行失败", e, this, rawInputs),
        () -> new FlowRuntimeException("更新数据失败，不支持的数据类型", this, rawInputs));
  }

  @SuppressWarnings("unchecked")
  protected Either<FlowRuntimeException, Object> doUpdate(Object where, Map<String, Object> data, Function<Throwable, FlowRuntimeException> exceptionMapper, Supplier<FlowRuntimeException> failureSupplier) {
    Either<? extends Throwable, ?> updateResult;
    if (where instanceof Map<?, ?> whereMap) {
      updateResult = executeUpdateWhere((Map<String, Object>) whereMap, data);
    } else if (where instanceof String || where instanceof Number) {
      updateResult = executeUpdateOne(where, data);
    } else if (where instanceof List<?> ids) {
      updateResult = executeUpdateMany((List<Object>) ids, data);
    } else {
      return Either.left(failureSupplier.get());
    }

    return updateResult
        .mapLeft(exceptionMapper)
        .map(result -> resultWrapperKey.isEmpty() ? result : Collections.singletonMap(resultWrapperKey, result));
  }

  private Either<? extends Throwable, ?> executeUpdateOne(Object id, Map<String, Object> data) {
    var updateResult = dataModel.update(id, data);
    return returnUpdatedData ? updateResult.flatMap(r -> dataModel.get(id)).toEither() : updateResult.toEither();
  }

  private Either<? extends Throwable, ?> executeUpdateMany(List<Object> ids, Map<String, Object> data) {
    var updateResult = dataModel.update(ids, data);
    return returnUpdatedData ? updateResult.flatMap(r -> dataModel.query(ids)).toEither() : updateResult.toEither();
  }

  private Either<? extends Throwable, ?> executeUpdateWhere(Map<String, Object> where, Map<String, Object> data) {
    var isSingleRecord = isSingleRecordCondition(where);
    var updateResult = dataModel.update(where, data);
    return !returnUpdatedData
        ? updateResult.toEither()
        : updateResult.flatMap(r -> {
          var queryUpdated = dataModel
              .query(Collections.singletonMap("WHERE", getUpdatedDataCondition(where, data)));
          return isSingleRecord
              ? queryUpdated.map(result -> result.isEmpty() ? null : result.get(0))
              : queryUpdated;
        })
        .toEither();
  }


  // 根据更新前的where和更新的data取获取更新后的数据的条件
  @SuppressWarnings("unchecked")
  private Map<String, Object> getUpdatedDataCondition(Map<String, Object> where, Map<String, Object> data) {
    var pks = dataModel.getPrimaryKeys();
    Map<String, Object> condition = new LinkedHashMap<>();
    where.forEach((k, v) -> {
      // 如果是主键，则尝试从data中获取值，如果没有则使用where中的值
      var pk = pks.stream().filter(f -> f.getName().equals(k)).findFirst();
      if (pk.isPresent()) {
        condition.put(k, data.getOrDefault(k, v));
        return;
      }

      // 如果不是主键，则先看看该key是不是字段
      // 如果是字段则再看看 data 中有没有该字段
      //   如果有则使用 data 中的值（要先通过字段的类型转换器转换为持久化值，因为字典、用户、组织架构等类型的字段可能会传入实体或者map等值）
      //   如果没有则使用 where 中的值
      var field = dataModel.getField(k);
      if (field.isPresent()) {
        var valueType = field.get().getValueType();
        if (data.containsKey(k)) {
          var dataValue = data.get(k);
          condition.put(k, valueType.toPersistentValue(dataValue));
          return;
        }
        condition.put(k, v);
        return;
      }

      // 否则可能是and、or等操作符
      if (OperatorFactory.getOperator(k).isPresent()) {
        Object nestedCondition;
        if (v instanceof Map<?, ?> nestedConditionMap) {
          nestedCondition = getUpdatedDataCondition((Map<String, Object>) nestedConditionMap, data);
        } else if (v instanceof List<?> nestedConditionList) {
          nestedCondition = ((List<Object>) nestedConditionList).stream()
              .map(e -> {
                if (e instanceof Map<?, ?> map) {
                  return getUpdatedDataCondition((Map<String, Object>) map, data);
                }
                logger.warn("执行逻辑流" + flowModel.getName() + "的" + meta.getName() + "节点，在准备返回数据查询条件时遇到过于复杂的条件，可能会导致查询结果不准确");
                return null;
              })
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
        } else {
          logger.warn("执行逻辑流" + flowModel.getName() + "的" + meta.getName() + "节点，在准备返回数据查询条件时遇到过于复杂的条件，可能会导致查询结果不准确");
          nestedCondition = v;
        }
        if (nestedCondition == null || nestedCondition instanceof Collection<?> coll && coll.isEmpty()) {
          logger.warn("执行逻辑流" + flowModel.getName() + "的" + meta.getName() + "节点，在准备返回数据查询条件时遇到过于复杂的条件，可能会导致查询结果不准确");
        }
        condition.put(k, nestedCondition);
      }
    });
    return condition;
  }


  public static class Builder implements NodeBuilder<UpdateDataNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "updateData".equalsIgnoreCase(meta.getType());
    }

    @Override
    public UpdateDataNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new UpdateDataNode(owner, metadata, references);
    }
  }
}
