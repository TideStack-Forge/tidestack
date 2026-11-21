package com.ouroboros.statistics.reporting.runtime.service;

import java.util.LinkedHashMap;
import com.ouroboros.statistics.reporting.runtime.execution.*;
import java.util.Map;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

/** Canonical query semantics shared by published execution and development preview. */
public final class CompositeViewQueryService {
  private final DatasetExecutionService datasetExecutionService;
  private final DatasetExecutionContextResolver executionContextResolver;

  public CompositeViewQueryService(DatasetExecutionService service, DatasetExecutionContextResolver resolver) {
    datasetExecutionService = service;
    executionContextResolver = resolver;
  }

  public DatasetResult execute(CompositeViewBusinessModelMeta compositeView, DatasetExecutionRequest request) {
      DatasetExecutionRequest resolvedRequest = resolveRequest(compositeView, request);
      DatasetResult result;
      if (isBlank(compositeView.getDatasetName())) {
        var query = com.ouroboros.statistics.reporting.definition.EmbeddedQueryDefinition.resolve(
            compositeView.getEmbeddedQuery(), compositeView.getFullName(), false);
        query.requireContext(resolvedRequest.getExecutionContext());
        var declared = query.getParameters().stream().map(parameter -> parameter.getName()).toList();
        if (!declared.containsAll(resolvedRequest.getRuntimeParameters().keySet())) {
          throw new com.ouroboros.exception.BusinessException("embeddedQuery.parameters: 请求包含未声明的参数");
        }
        result = datasetExecutionService.execute(query, resolvedRequest);
      } else {
        result = datasetExecutionService.execute(resolvedRequest);
      }
      return projectColumns(compositeView, result);
  }

  private DatasetExecutionRequest resolveRequest(CompositeViewBusinessModelMeta compositeView,
                                                 DatasetExecutionRequest request) {
    request = request == null ? new DatasetExecutionRequest() : request;
    boolean datasetSource = !isBlank(compositeView.getDatasetName());
    if (datasetSource == (compositeView.getEmbeddedQuery() != null)) {
      throw new com.ouroboros.exception.BusinessException(datasetSource
          ? "composite-view.source-conflict" : "composite-view.source-missing");
    }
    DatasetExecutionRequest resolved = request.copy();
    if (resolved.getPage() == null) {
      var page = new com.ouroboros.statistics.reporting.runtime.execution.PageSpec();
      page.setPageNo(1);
      page.setPageSize(compositeView.getLayoutConfig().getPageSize());
      resolved.setPage(page);
    }
    var filter = resolved.getDynamicFilter() == null
        ? new com.ouroboros.statistics.reporting.runtime.execution.DynamicFilterSpec() : resolved.getDynamicFilter().copy();
    validateFilter(compositeView, filter.getFilter());
    if (filter.getSort() == null || filter.getSort().isEmpty()) {
      var defaults = compositeView.getLayoutConfig().getDefaultSort();
      filter.setSort((defaults == null ? java.util.List.<CompositeViewBusinessModelMeta.SortDef>of() : defaults).stream().map(order -> {
        var sort = new com.ouroboros.statistics.reporting.runtime.execution.SortSpec();
        sort.setField(order.getField());
        sort.setDirection(order.getDirection());
        return sort;
      }).toList());
    }
    for (var order : filter.getSort()) {
      if (order == null || compositeView.getColumns().stream().noneMatch(column -> column.getField().equalsIgnoreCase(order.getField()))) {
        throw new com.ouroboros.exception.BusinessException("dataset.field-not-allowed: composite view sort");
      }
    }
    resolved.setDynamicFilter(filter);
    resolved.setDatasetName(compositeView.getDatasetName());
    resolved.setCompositeViewName(compositeView.getFullName());
    if (resolved.getRuntimeParameters() == null) {
      resolved.setRuntimeParameters(new LinkedHashMap<String, Object>());
    }
    if (resolved.getExecutionContext() == null) {
      resolved.setExecutionContext(new LinkedHashMap<String, Object>());
    }
    if (resolved.getAppliedParameters() == null) {
      resolved.setAppliedParameters(new LinkedHashMap<String, Object>());
    }
    Map<String, Object> executionContext = executionContextResolver.resolve(resolved);
    return TrustedDatasetExecutionRequest.trust(resolved, executionContext);
  }

  private void validateFilter(CompositeViewBusinessModelMeta view,
      com.ouroboros.statistics.reporting.runtime.execution.FilterNode node) {
    if (node == null) {
      return;
    }
    if ("and".equalsIgnoreCase(node.getOp()) || "or".equalsIgnoreCase(node.getOp())) {
      if (node.getChildren() != null) {
        node.getChildren().forEach(child -> validateFilter(view, child));
      }
      return;
    }
    var item = view.getFilterConfig() != null && view.getFilterConfig().getItems() != null
        ? view.getFilterConfig().getItems().stream().filter(candidate ->
        candidate.getField().equalsIgnoreCase(node.getField())).findFirst().orElse(null) : null;
    if (item == null) {
      throw new com.ouroboros.exception.BusinessException("dataset.field-not-allowed: composite view filter");
    }
    if (item.getOps() == null || !item.getOps().contains(node.getOp())) {
      throw new com.ouroboros.exception.BusinessException("dataset.filter-op-not-allowed: composite view filter");
    }
  }

  private DatasetResult projectColumns(CompositeViewBusinessModelMeta view, DatasetResult result) {
    result.setRows(result.getRows().stream().map(row -> {
      Map<String, Object> projected = new LinkedHashMap<>();
      for (var column : view.getColumns()) {
        row.entrySet().stream().filter(entry -> column.getField().equalsIgnoreCase(entry.getKey())).findFirst()
            .ifPresent(entry -> projected.put(column.getField(), entry.getValue()));
      }
      return projected;
    }).toList());
    if (result.getResolvedSchema() != null) {
      var schema = new com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition();
      schema.setFields(view.getColumns().stream().flatMap(column -> result.getResolvedSchema().getFields().stream()
          .filter(field -> column.getField().equalsIgnoreCase(field.getName()))).toList());
      result.setResolvedSchema(schema);
    }
    return result;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
