package com.ouroboros.statistics.reporting.runtime.executor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.query.QueryDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.CompiledDynamicFilter;
import com.ouroboros.statistics.reporting.runtime.execution.DynamicFilterCompiler;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultSqlDialectResolver;
import com.ouroboros.statistics.reporting.runtime.execution.PageSpec;
import com.ouroboros.statistics.reporting.runtime.execution.SqlDataSourceContext;
import com.ouroboros.statistics.reporting.runtime.execution.SqlDataSourceProvider;
import com.ouroboros.statistics.reporting.runtime.execution.SqlPaginationRenderer;
import com.ouroboros.statistics.reporting.runtime.execution.SqlExecutionBackend;
import com.ouroboros.statistics.reporting.runtime.result.DatasetDiagnostics;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

public class SqlDatasetExecutor implements DatasetExecutor {
  @Override
  public boolean requiresDataSource() {
    return true;
  }

  private final DefaultSqlDialectResolver sqlDialectResolver;
  private final SqlDataSourceProvider dataSourceProvider;
  private final SqlExecutionBackend executionBackend;
  private final SqlPaginationRenderer paginationRenderer = new SqlPaginationRenderer();

  public SqlDatasetExecutor(DefaultSqlDialectResolver sqlDialectResolver,
                            SqlDataSourceProvider dataSourceProvider,
                            SqlExecutionBackend executionBackend) {
    this.sqlDialectResolver = sqlDialectResolver;
    this.dataSourceProvider = dataSourceProvider;
    this.executionBackend = executionBackend;
  }

  @Override
  public boolean supports(DatasetDefinition dataset) {
    return dataset != null && dataset.getQueryDefinition() instanceof SqlQueryDefinition;
  }

  @Override
  public DatasetResult execute(DatasetDefinition dataset, DatasetExecutionRequest request) {
    if (!supports(dataset)) {
      throw new IllegalArgumentException("dataset is not backed by SQL");
    }
    SqlQueryDefinition queryDefinition = (SqlQueryDefinition) dataset.getQueryDefinition();
    SqlDataSourceContext dataSourceContext = dataSourceProvider.resolve(queryDefinition, request);
    DefaultSqlDialectResolver.ResolvedSql selectedSql = sqlDialectResolver.resolve(queryDefinition, dataSourceContext);
    CompiledDynamicFilter compiledDynamicFilter = getCompiledDynamicFilter(request);
    Map<String, Object> queryParameters = mergeParameters(request, compiledDynamicFilter);
    String dataSql = buildDataSql(selectedSql.getSql(), dataSourceContext, compiledDynamicFilter,
        request == null ? null : request.getPage());
    DatasetResult result = new DatasetResult();
    applyPageInfo(result, dataSourceContext, selectedSql.getSql(), compiledDynamicFilter, request, queryParameters);
    var budget = request == null ? null : request.getExecutionBudget();
    SqlExecutionBackend.SqlQueryResult queryResult = executionBackend.execute(
        dataSourceContext,
        dataSql,
        queryParameters, budget, budget == null ? 0 : budget.rowLimit() == Integer.MAX_VALUE ? Integer.MAX_VALUE : budget.rowLimit() + 1
    );

    if (budget != null) budget.checkRows(queryResult.getRows().size());
    result.setResolvedSchema(dataset.getSchema());
    result.setRows(normalizeRows(queryResult, dataset.getSchema()));
    result.setDiagnostics(buildDiagnostics(selectedSql, dataSourceContext, queryResult, dataset.getSchema(), request));
    result.getExecutionMeta().put("datasource", dataSourceContext == null ? null : dataSourceContext.getDatasource());
    result.getExecutionMeta().put("dialect", dataSourceContext == null ? null : dataSourceContext.getDialect());
    result.getExecutionMeta().put("dialectVersion", dataSourceContext == null ? null : dataSourceContext.getDialectVersion());
    return result;
  }

  private Map<String, Object> mergeParameters(DatasetExecutionRequest request, CompiledDynamicFilter compiledDynamicFilter) {
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    if (request != null && request.getAppliedParameters() != null) {
      parameters.putAll(request.getAppliedParameters());
    }
    if (compiledDynamicFilter != null && compiledDynamicFilter.getParameters() != null) {
      compiledDynamicFilter.getParameters().forEach((key, value) -> {
        if (parameters.containsKey(key)) {
          throw new IllegalArgumentException("dynamic-filter-parameter-conflict: " + key);
        }
        parameters.put(key, value);
      });
    }
    return parameters;
  }

  private String buildDataSql(String resolvedSql,
                              SqlDataSourceContext dataSourceContext,
                              CompiledDynamicFilter compiledDynamicFilter,
                              PageSpec page) {
    if (isBlank(dynamicWhere(compiledDynamicFilter)) && isBlank(dynamicOrderBy(compiledDynamicFilter)) && page == null) {
      return resolvedSql;
    }
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT * FROM ").append(wrappedBaseSql(resolvedSql));
    if (!isBlank(dynamicWhere(compiledDynamicFilter))) {
      sql.append(" WHERE ").append(dynamicWhere(compiledDynamicFilter));
    }
    if (!isBlank(dynamicOrderBy(compiledDynamicFilter))) {
      sql.append(" ORDER BY ").append(dynamicOrderBy(compiledDynamicFilter));
    }
    if (page != null) {
      sql.append(paginationRenderer.render(dataSourceContext, page,
          !isBlank(dynamicOrderBy(compiledDynamicFilter))));
    }
    return sql.toString();
  }

  private String wrappedBaseSql(String resolvedSql) {
    return String.format("(%s) __ouroboros_view__", resolvedSql);
  }

  private void applyPageInfo(DatasetResult result,
                             SqlDataSourceContext dataSourceContext,
                             String resolvedSql,
                             CompiledDynamicFilter compiledDynamicFilter,
                             DatasetExecutionRequest request,
                             Map<String, Object> queryParameters) {
    if (request == null || (request.getPage() == null && !request.isExport())) {
      return;
    }
    PageSpec page = request.getPage();
    String countSql = buildCountSql(resolvedSql, compiledDynamicFilter);
    SqlExecutionBackend.SqlQueryResult countResult = executionBackend.execute(dataSourceContext, countSql, queryParameters,
        request.getExecutionBudget(), 1);
    if (request.isExport()) {
      if (request.getExecutionBudget() != null) request.getExecutionBudget().checkRows(extractTotal(countResult));
      return;
    }
    Map<String, Object> pageInfo = new LinkedHashMap<String, Object>();
    pageInfo.put("pageNo", page.getPageNo());
    pageInfo.put("pageSize", page.getPageSize());
    pageInfo.put("total", extractTotal(countResult));
    result.setPageInfo(pageInfo);
  }

  private String buildCountSql(String resolvedSql, CompiledDynamicFilter compiledDynamicFilter) {
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT COUNT(*) AS total FROM ").append(wrappedBaseSql(resolvedSql));
    if (!isBlank(dynamicWhere(compiledDynamicFilter))) {
      sql.append(" WHERE ").append(dynamicWhere(compiledDynamicFilter));
    }
    return sql.toString();
  }

  private String dynamicWhere(CompiledDynamicFilter compiledDynamicFilter) {
    return compiledDynamicFilter == null ? null : compiledDynamicFilter.getWhereClause();
  }

  private String dynamicOrderBy(CompiledDynamicFilter compiledDynamicFilter) {
    return compiledDynamicFilter == null ? null : compiledDynamicFilter.getOrderByClause();
  }

  private long extractTotal(SqlExecutionBackend.SqlQueryResult countResult) {
    if (countResult == null || countResult.getRows() == null || countResult.getRows().isEmpty()) {
      return 0L;
    }
    Object value = countResult.getRows().get(0).get("TOTAL");
    if (value == null) {
      value = countResult.getRows().get(0).get("total");
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return value == null ? 0L : Long.parseLong(String.valueOf(value));
  }

  private CompiledDynamicFilter getCompiledDynamicFilter(DatasetExecutionRequest request) {
    if (request == null || request.getExecutionContext() == null) {
      return null;
    }
    Object value = request.getExecutionContext().get(DynamicFilterCompiler.CONTEXT_KEY);
    return value instanceof CompiledDynamicFilter ? (CompiledDynamicFilter) value : null;
  }

  private List<Map<String, Object>> normalizeRows(SqlExecutionBackend.SqlQueryResult queryResult,
                                                  DatasetSchemaDefinition schema) {
    if (queryResult == null || queryResult.getRows() == null) {
      return new ArrayList<Map<String, Object>>();
    }
    Map<String, String> schemaNameMap = new LinkedHashMap<String, String>();
    if (schema != null && schema.getFields() != null) {
      for (DatasetFieldDefinition field : schema.getFields()) {
        if (field != null && field.getName() != null) {
          schemaNameMap.put(field.getName().toLowerCase(Locale.ROOT), field.getName());
        }
      }
    }

    List<Map<String, Object>> normalized = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : queryResult.getRows()) {
      Map<String, Object> normalizedRow = new LinkedHashMap<String, Object>();
      row.forEach((key, value) -> {
        String normalizedKey = key == null ? null : schemaNameMap.getOrDefault(key.toLowerCase(Locale.ROOT), key);
        normalizedRow.put(normalizedKey, value);
      });
      normalized.add(normalizedRow);
    }
    return normalized;
  }

  private DatasetDiagnostics buildDiagnostics(DefaultSqlDialectResolver.ResolvedSql resolvedSql,
                                              SqlDataSourceContext context,
                                              SqlExecutionBackend.SqlQueryResult queryResult,
                                              DatasetSchemaDefinition schema,
                                              DatasetExecutionRequest request) {
    DatasetDiagnostics diagnostics = new DatasetDiagnostics();
    diagnostics.setQueryType("sql");
    diagnostics.getAttributes().put("datasource", context == null ? null : context.getDatasource());
    diagnostics.getAttributes().put("normalizedParameters",
        request == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(request.getAppliedParameters()));
    if (resolvedSql.getMatchedDialect() != null) {
      diagnostics.setMatchedSqlDialect(resolvedSql.getMatchedDialect().getDialectName());
    }
    if (resolvedSql.isFallbackToSql()) {
      diagnostics.getMessages().add("Using base SQL because no dialect-specific SQL matched.");
    }
    if (hasSchemaDrift(queryResult, schema)) {
      diagnostics.getMessages().add("SQL result columns drift from declared dataset schema.");
    }
    return diagnostics;
  }

  private boolean hasSchemaDrift(SqlExecutionBackend.SqlQueryResult queryResult, DatasetSchemaDefinition schema) {
    if (schema == null || schema.getFields() == null || schema.getFields().isEmpty()) {
      return false;
    }
    List<String> actualColumns = queryResult == null || queryResult.getColumnNames() == null
        ? new ArrayList<String>()
        : queryResult.getColumnNames().stream()
            .map(name -> name == null ? null : name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toList());
    List<String> schemaColumns = schema.getFields().stream()
        .map(DatasetFieldDefinition::getName)
        .map(name -> name == null ? null : name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toList());
    return !actualColumns.equals(schemaColumns);
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
