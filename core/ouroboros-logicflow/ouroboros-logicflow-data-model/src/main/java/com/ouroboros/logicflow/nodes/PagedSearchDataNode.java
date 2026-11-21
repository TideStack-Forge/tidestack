package com.ouroboros.logicflow.nodes;

import java.util.*;

import io.vavr.control.Either;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class PagedSearchDataNode extends AbstractDataNode<Object, Map<String, Object>> {
  private final String itemsKey;
  private final String totalKey;

  public PagedSearchDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta, references);
    this.itemsKey = meta.getExtraProp(String.class, "itemsKey")
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .orElse("items");
    this.totalKey = meta.getExtraProp(String.class, "totalKey")
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .orElse("total");
  }

  @Override
  protected Either<FlowRuntimeException, Object> doExecute(Map<String, Object> input, DataModel dataModel, List<Object> rawInputs) {
    var raw = new TreeMap<>(String::compareToIgnoreCase);
    raw.putAll(input);
    var where = Optional.ofNullable(raw.get("where"))
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .orElseGet(Collections::emptyMap);
    var total = dataModel.count((Map<String, Object>) where)
        .toEither()
        .mapLeft(e -> new FlowRuntimeException("统计数据失败", e, this, rawInputs));
    return total.flatMap(count -> {
      if (count == 0) {
        var result = new HashMap<String, Object>();
        result.put(itemsKey, Collections.emptyList());
        result.put(totalKey, count);
        return Either.right(result);
      }
      return dataModel.query(input)
          .toEither()
          .mapLeft(e -> new FlowRuntimeException("查询数据失败", e, this, rawInputs))
          .map(recordList -> {
            var result = new HashMap<String, Object>();
            result.put(itemsKey, recordList);
            result.put(totalKey, count);
            return result;
          });
    });
  }

  public static class Builder implements NodeBuilder<PagedSearchDataNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "pagedSearchData".equalsIgnoreCase(meta.getType());
    }

    @Override
    public PagedSearchDataNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new PagedSearchDataNode(owner, metadata, references);
    }
  }
}
