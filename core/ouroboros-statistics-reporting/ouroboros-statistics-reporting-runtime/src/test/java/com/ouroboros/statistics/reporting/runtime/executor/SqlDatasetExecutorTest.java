package com.ouroboros.statistics.reporting.runtime.executor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlDialectDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.runtime.execution.CompiledDynamicFilter;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultSqlDialectResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DynamicFilterCompiler;
import com.ouroboros.statistics.reporting.runtime.execution.JdbcSqlExecutionBackend;
import com.ouroboros.statistics.reporting.runtime.execution.PageSpec;
import com.ouroboros.statistics.reporting.runtime.execution.SqlDataSourceContext;
import com.ouroboros.statistics.reporting.runtime.execution.SqlDataSourceProvider;
import com.ouroboros.statistics.reporting.runtime.execution.SqlExecutionBackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlDatasetExecutorTest {

  @Test
  void shouldReturnFallbackDiagnosticWhenBaseSqlIsUsed() {
    var executor = new SqlDatasetExecutor(
        new DefaultSqlDialectResolver(),
        fixedProvider(new SqlDataSourceContext()
            .setDatasource("analytics-h2")
            .setDialect("h2")
            .setDialectVersion("2.2.224")
            .setDataSource(h2DataSource("sql_executor_fallback"))),
        new JdbcSqlExecutionBackend()
    );

    var result = executor.execute(sqlDataset(), request());

    assertEquals(1, result.getRows().size());
    assertEquals(Integer.valueOf(1), result.getRows().get(0).get("total"));
    assertTrue(result.getDiagnostics().getMessages().stream()
        .anyMatch(message -> message.contains("base SQL")));
  }

  @Test
  void shouldUseDialectAndReportMatchedDialectName() {
    var executor = new SqlDatasetExecutor(
        new DefaultSqlDialectResolver(),
        fixedProvider(new SqlDataSourceContext()
            .setDatasource("reporting-main")
            .setDialect("mysql")
            .setDialectVersion("8.0.36")
            .setDataSource(h2DataSource("sql_executor_dialect"))),
        new JdbcSqlExecutionBackend()
    );

    var result = executor.execute(sqlDataset(), request());

    assertEquals("mysql", result.getDiagnostics().getMatchedSqlDialect());
    assertEquals(Integer.valueOf(2), result.getRows().get(0).get("total"));
  }

  @Test
  void shouldResolveDatasourceFromSqlDefinitionWhenRequestDoesNotOverrideIt() {
    var executor = new SqlDatasetExecutor(
        new DefaultSqlDialectResolver(),
        (queryDefinition, request) -> {
          SqlDataSourceContext context = new SqlDataSourceContext()
              .setDatasource(queryDefinition.getDatasource())
              .setDialect("h2")
              .setDialectVersion("2.2.224")
              .setDataSource(h2DataSource("sql_executor_definition_datasource"));
          return context;
        },
        new JdbcSqlExecutionBackend()
    );

    var result = executor.execute(sqlDataset(), request());

    assertEquals("reporting-main", result.getExecutionMeta().get("datasource"));
  }

  @Test
  void shouldApplyCompiledDynamicFilterSortPageAndCountInExecutor() {
    var executor = new SqlDatasetExecutor(
        new DefaultSqlDialectResolver(),
        fixedProvider(new SqlDataSourceContext()
            .setDatasource("analytics-h2")
            .setDialect("h2")
            .setDialectVersion("2.2.224")
            .setDataSource(h2DataSource("sql_executor_dynamic_filter"))),
        new JdbcSqlExecutionBackend()
    );

    var request = request();
    request.setExecutionContext(Collections.<String, Object>singletonMap(
        DynamicFilterCompiler.CONTEXT_KEY,
        compiledFilter(
            "__ouroboros_view__.region = :__dyn0",
            "__ouroboros_view__.total DESC",
            "__dyn0",
            "east"
        )
    ));
    var page = new PageSpec();
    page.setPageNo(1);
    page.setPageSize(1);
    request.setPage(page);

    var result = executor.execute(dynamicSqlDataset(), request);

    assertEquals(1, result.getRows().size());
    assertEquals("east", result.getRows().get(0).get("region"));
    assertEquals(Integer.valueOf(30), result.getRows().get(0).get("total"));
    assertEquals(1, result.getPageInfo().get("pageNo"));
    assertEquals(1, result.getPageInfo().get("pageSize"));
    assertEquals(2L, result.getPageInfo().get("total"));
  }

  @Test
  void shouldKeepDynamicFilterValuesParameterized() {
    var executor = new SqlDatasetExecutor(
        new DefaultSqlDialectResolver(),
        fixedProvider(new SqlDataSourceContext()
            .setDatasource("analytics-h2")
            .setDialect("h2")
            .setDialectVersion("2.2.224")
            .setDataSource(h2DataSource("sql_executor_dynamic_injection_value"))),
        new JdbcSqlExecutionBackend()
    );

    var request = request();
    request.setExecutionContext(Collections.<String, Object>singletonMap(
        DynamicFilterCompiler.CONTEXT_KEY,
        compiledFilter(
            "__ouroboros_view__.region = :__dyn0",
            null,
            "__dyn0",
            "east' OR 1=1 --"
        )
    ));

    var result = executor.execute(dynamicSqlDataset(), request);

    assertEquals(0, result.getRows().size());
  }

  @Test
  void shouldPreserveOriginalSqlWhenDynamicFilterAndPageAreAbsent() {
    CapturingBackend backend = new CapturingBackend();
    var executor = new SqlDatasetExecutor(
        new DefaultSqlDialectResolver(),
        fixedProvider(new SqlDataSourceContext()
            .setDatasource("analytics-h2")
            .setDialect("h2")
            .setDialectVersion("2.2.224")),
        backend
    );

    executor.execute(sqlDataset(), request());

    assertEquals(1, backend.getSqls().size());
    assertEquals("select 1 as total", backend.getSqls().get(0));
  }

  private static DatasetDefinition sqlDataset() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");

    var parameter = new ParameterDefinition();
    parameter.setName("startDate");
    parameter.setType("date");
    dataset.setParameters(Collections.singletonList(parameter));

    var queryDefinition = new SqlQueryDefinition();
    queryDefinition.setDatasource("reporting-main");
    queryDefinition.setSql("select 1 as total");
    queryDefinition.setDialects(Collections.singletonList(dialect("mysql", "select 2 as total")));
    dataset.setQueryDefinition(queryDefinition);

    dataset.setSchema(schema("total"));
    return dataset;
  }

  private static DatasetDefinition dynamicSqlDataset() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");

    var queryDefinition = new SqlQueryDefinition();
    queryDefinition.setDatasource("reporting-main");
    queryDefinition.setSql(
        "select 'east' as region, 10 as total "
            + "union all select 'west' as region, 20 as total "
            + "union all select 'east' as region, 30 as total"
    );
    dataset.setQueryDefinition(queryDefinition);
    dataset.setSchema(schema("region", "total"));
    return dataset;
  }

  private static DatasetExecutionRequest request() {
    var request = new DatasetExecutionRequest();
    request.setDatasetName("demo.sales");
    request.setAppliedParameters(Collections.<String, Object>singletonMap("startDate", "2026-01-01"));
    return request;
  }

  private static SqlDialectDefinition dialect(String dialectName, String sql) {
    var dialect = new SqlDialectDefinition();
    dialect.setDialectName(dialectName);
    dialect.setSql(sql);
    return dialect;
  }

  private static DatasetSchemaDefinition schema(String... fieldNames) {
    java.util.ArrayList<DatasetFieldDefinition> fields = new java.util.ArrayList<DatasetFieldDefinition>();
    for (String fieldName : fieldNames) {
      fields.add(field(fieldName));
    }
    var schema = new DatasetSchemaDefinition();
    schema.setFields(fields);
    return schema;
  }

  private static DatasetFieldDefinition field(String fieldName) {
    var field = new DatasetFieldDefinition();
    field.setName(fieldName);
    field.setDataType("number");
    field.setSemanticRole("metric");
    return field;
  }

  private static SqlDataSourceProvider fixedProvider(SqlDataSourceContext context) {
    return (queryDefinition, request) -> context;
  }

  private static JdbcDataSource h2DataSource(String name) {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    return dataSource;
  }

  private static CompiledDynamicFilter compiledFilter(String whereClause,
                                                      String orderByClause,
                                                      String parameterName,
                                                      Object parameterValue) {
    CompiledDynamicFilter compiledFilter = new CompiledDynamicFilter();
    compiledFilter.setWhereClause(whereClause);
    compiledFilter.setOrderByClause(orderByClause);
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    parameters.put(parameterName, parameterValue);
    compiledFilter.setParameters(parameters);
    return compiledFilter;
  }

  private static class CapturingBackend implements SqlExecutionBackend {
    private final List<String> sqls = new java.util.ArrayList<String>();

    @Override
    public SqlQueryResult execute(SqlDataSourceContext context, String sql, Map<String, Object> parameters) {
      sqls.add(sql);
      return new SqlQueryResult()
          .setRows(Collections.<Map<String, Object>>singletonList(Collections.<String, Object>singletonMap("total", 1)))
          .setColumnNames(Collections.singletonList("total"));
    }

    private List<String> getSqls() {
      return sqls;
    }
  }
}
