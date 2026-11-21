package com.ouroboros.logicflow.nodes;

import java.util.*;
import java.util.stream.Collectors;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.data.normalize.OperatorFactory;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

// TODO: 需要跟 NormalizeWhereDataModelPlugin 逻辑合并
public class NormalizeWhereNode extends AbstractDataNode<Map<String, Object>, Map<String, Object>> {
  private final Set<String> fields;
  private final Boolean wrapWithWhere;

  public NormalizeWhereNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
    fields = Optional.ofNullable(dataModel)
        .map(DataModel::getFields)
        .orElseGet(Collections::emptyList).stream()
        .filter(f -> f.getValueType().isPhysical())
        .map(DataModelField::getName)
        .collect(Collectors.toSet());
    wrapWithWhere = meta.getExtraProp(Boolean.class, "wrapWithWhere").orElse(true);
  }

  @SuppressWarnings("unchecked")
  private Object normalizeHelper(Object value) {
    if (value instanceof Map) {
      return normalize((Map<String, Object>) value);
    }
    if (value instanceof Collection) {
      return ((Collection<?>) value).stream()
          .map(this::normalizeHelper)
          .collect(Collectors.toList());
    }
    return value;
  }

  private Map<String, Object> normalize(Map<String, Object> raw) {
    var normalized = new HashMap<String, Object>();
    raw.forEach((k, v) -> {
      var operator = OperatorFactory.getOperator(k);
      if (operator.isPresent()) {
        normalized.put(k, normalizeHelper(v));
        return;
      }
      if (fields.contains(k)) {
        normalized.put(k, normalizeHelper(v));
      }
    });
    return normalized;
  }

  @Override
  protected Either<FlowRuntimeException, Map<String, Object>> doExecute(Map<String, Object> input, DataModel dataModel, List<Object> rawInputs) {
    return Try.of(() -> normalize(input))
        .map(map -> wrapWithWhere ? Collections.singletonMap("where", (Object) map) : map)
        .toEither()
        .mapLeft(e -> new FlowRuntimeException("规范化查询条件失败", e, this, rawInputs));
  }

  public static class Builder implements NodeBuilder<NormalizeWhereNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "normalizeWhere".equalsIgnoreCase(meta.getType());
    }

    @Override
    public NormalizeWhereNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new NormalizeWhereNode(owner, metadata, references);
    }
  }
}
