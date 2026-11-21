package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.List;
import com.ouroboros.statistics.reporting.runtime.service.CompositeViewQueryService;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.definition.*;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.*;
import com.ouroboros.statistics.reporting.runtime.center.DefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.executor.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real JDBC and the complete normalize/authorize/compile/count/data/project pipeline. */
class StatisticsExecutionPipelineIntegrationTest {
  static DatasetDefinition dataset(int pageLimit, int exportLimit) {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.orders");
    dataset.setIsEnabled(true);
    var query = new SqlQueryDefinition();
    query.setSql("select x as id from system_range(1, 4)");
    dataset.setQueryDefinition(query);
    var id = new DatasetFieldDefinition();
    id.setName("id"); id.setDataType("integer"); id.setSemanticRole("dimension");
    id.setNullable(false); id.setFilterable(true); id.setSortable(true);
    var schema = new DatasetSchemaDefinition(); schema.setFields(List.of(id)); dataset.setSchema(schema);
    dataset.setExecutionConstraints(Map.of("maxPageSize", pageLimit, "maxExportRows", exportLimit));
    return dataset;
  }

  static SqlDataSourceContext context() {
    var datasource = new JdbcDataSource();
    datasource.setURL("jdbc:h2:mem:statistics_pipeline;DB_CLOSE_DELAY=-1");
    return new SqlDataSourceContext().setDatasource("default").setDialect("h2").setDataSource(datasource);
  }

  static DefaultDatasetExecutionService service(DatasetDefinition dataset, SqlExecutionBackend backend) {
    var center = new com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter(List.of(() -> List.of(dataset)));
    return new DefaultDatasetExecutionService(center, new DefaultDatasetParameterNormalizer(), new DefaultDatasetPermissionInjector(),
        List.of(new SqlDatasetExecutor(new DefaultSqlDialectResolver(), (query, request) -> context(), backend)),
        request -> Map.of("currentUser", "trusted-user", "departmentId", 7));
  }

  static DatasetExecutionRequest page(int number, int size) {
    var request = new DatasetExecutionRequest();
    request.setDatasetName("demo.orders");
    var page = new PageSpec(); page.setPageNo(number); page.setPageSize(size); request.setPage(page);
    return request;
  }

  @Test
  void returnsPagedRowsAndFullCountAndRejectsAnOversizedPage() {
    var service = service(dataset(2, 4), new JdbcSqlExecutionBackend());
    var result = service.execute(page(2, 2));
    assertEquals(2, result.getRows().size());
    assertEquals(3L, ((Number) result.getRows().getFirst().get("id")).longValue());
    assertEquals(4L, result.getPageInfo().get("total"));
    assertEquals(2, ((DatasetExecutionPolicy) result.getExecutionMeta().get("executionPolicy")).maxPageSize());
    assertTrue(assertThrows(BusinessException.class, () -> service.execute(page(1, 3))).getMessage().contains("page-size-exceeded"));
  }

  @Test
  void preservesTheSpecificationCountAcrossSortedAndFilteredCompositePages() {
    var dataset = dataset(2, 5);
    ((SqlQueryDefinition) dataset.getQueryDefinition()).setSql("""
        select 'S1' as id, 10 as amount, 'open' as status
        union all select 'S2', 20, 'closed'
        union all select 'S3', 30, 'open'
        union all select 'S4', 40, 'closed'
        union all select 'S5', 50, 'open'
        """);
    var id = dataset.getSchema().getFields().getFirst();
    id.setDataType("string");
    var amount = new DatasetFieldDefinition();
    amount.setName("amount"); amount.setDataType("integer"); amount.setSemanticRole("metric"); amount.setSortable(true);
    var status = new DatasetFieldDefinition();
    status.setName("status"); status.setDataType("string"); status.setSemanticRole("dimension"); status.setFilterable(true);
    dataset.getSchema().setFields(List.of(id, amount, status));
    var view = new CompositeViewBusinessModelMeta();
    view.setNamespace("demo"); view.setName("orders"); view.setDatasetName(dataset.getName());
    view.setColumns(List.of("id", "amount", "status").stream().map(name -> {
      var column = new CompositeViewBusinessModelMeta.ColumnDef(); column.setField(name); return column;
    }).toList());
    view.getLayoutConfig().setPageSize(2);
    var order = new CompositeViewBusinessModelMeta.SortDef();
    order.setField("amount"); order.setDirection("asc");
    view.getLayoutConfig().setDefaultSort(List.of(order));
    var filterItem = new CompositeViewBusinessModelMeta.FilterItemDef();
    filterItem.setField("status"); filterItem.setOps(List.of("eq"));
    view.getFilterConfig().setItems(List.of(filterItem));
    var query = new CompositeViewQueryService(service(dataset, new JdbcSqlExecutionBackend()), request -> Map.of());
    var request = page(2, 2);
    var result = query.execute(view, request);
    assertEquals(List.of("S3", "S4"), result.getRows().stream().map(row -> row.get("id")).toList());
    assertEquals(5L, result.getPageInfo().get("total"));
    var predicate = new FilterNode();
    predicate.setField("status"); predicate.setOp("eq"); predicate.setValue("open");
    var filter = new DynamicFilterSpec(); filter.setFilter(predicate);
    request.setDynamicFilter(filter); request.setPage(page(1, 2).getPage());
    result = query.execute(view, request);
    assertEquals(List.of("S1", "S3"), result.getRows().stream().map(row -> row.get("id")).toList());
    assertEquals(3L, result.getPageInfo().get("total"));
    request.setPage(page(2, 2).getPage());
    result = query.execute(view, request);
    assertEquals(List.of("S5"), result.getRows().stream().map(row -> row.get("id")).toList());
    assertEquals(3L, result.getPageInfo().get("total"));
  }

  @Test
  void exportsAllFilteredRowsAndRejectsMaxPlusOneBeforeReturningAPayload() {
    var request = page(1, 1);
    request.setExport(true);
    assertEquals(4, service(dataset(1, 4), new JdbcSqlExecutionBackend()).execute(request).getRows().size());
    var count = new AtomicInteger();
    SqlExecutionBackend backend = (context, sql, params) -> {
      count.incrementAndGet();
      return new JdbcSqlExecutionBackend().execute(context, sql, params);
    };
    var failure = assertThrows(BusinessException.class, () -> service(dataset(1, 3), backend).execute(request));
    assertTrue(failure.getMessage().contains("export-row-limit-exceeded"));
    assertEquals(1, count.get());
    assertEquals(1, request.getPage().getPageSize());
  }

  @Test
  void checksTheExtraExportRowWhenCountAndRowsChange() {
    var calls = new AtomicInteger();
    SqlExecutionBackend backend = (context, sql, params) -> calls.incrementAndGet() == 1
        ? new SqlExecutionBackend.SqlQueryResult().setRows(List.of(Map.of("total", 2)))
        : new SqlExecutionBackend.SqlQueryResult().setRows(List.of(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3)));
    var request = new DatasetExecutionRequest(); request.setDatasetName("demo.orders"); request.setExport(true);
    assertTrue(assertThrows(BusinessException.class, () -> service(dataset(2, 2), backend).execute(request))
        .getMessage().contains("export-row-limit-exceeded"));
    assertEquals(2, calls.get());
  }

  @Test
  void sharesOneTimeoutAcrossCountAndDataAndReleasesConnections() {
    var dataset = dataset(2, 4);
    dataset.setExecutionConstraints(Map.of("queryTimeoutSeconds", 1));
    var calls = new AtomicInteger();
    SqlExecutionBackend backend = (context, sql, params) -> {
      calls.incrementAndGet();
      try { Thread.sleep(600); }
      catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
      return new JdbcSqlExecutionBackend().execute(context, sql, params);
    };
    assertTrue(assertThrows(BusinessException.class, () -> service(dataset, backend).execute(page(1, 2)))
        .getMessage().contains("query-timeout"));
    assertEquals(2, calls.get());
    assertEquals(2, service(dataset, new JdbcSqlExecutionBackend()).execute(page(1, 2)).getRows().size());
  }

  @Test
  void cancelsExpensiveJdbcStatementsAtTheDriverTimeoutAndCanQueryAgain() {
    var backend = new JdbcSqlExecutionBackend();
    var budget = new DatasetExecutionBudget(new DatasetExecutionPolicy(1, 10, 10, "jdbc-timeout"), false);
    long start = System.nanoTime();
    var error = assertThrows(BusinessException.class, () -> backend.execute(context(),
        "select sum(sin(x)) from system_range(1, 1000000000)", Map.of(), budget, 2));
    assertTrue(error.getMessage().contains("query-timeout"));
    assertTrue(System.nanoTime() - start < java.util.concurrent.TimeUnit.SECONDS.toNanos(8));
    assertEquals(1, backend.execute(context(), "select 1 as id", Map.of()).getRows().size());
  }

  @Test
  void executesPrivateQueriesWithTrustedParametersAndProjectsOnlyConfiguredColumns() {
    var view = new CompositeViewBusinessModelMeta();
    view.setNamespace("demo"); view.setName("privateOrders"); view.setIsEnabled(true);
    view.setEmbeddedQuery(Map.of("queryDefinition", Map.of("type", "sql", "sql", "select cast(${departmentId} as integer) as id, 'hidden' as secret"),
        "parameters", List.of(Map.of("name", "departmentId", "type", "number", "valueSource", "systemContext", "systemContextBinding", "departmentId", "required", true)),
        "requiredContext", List.of("currentUser", "departmentId"), "schema", Map.of("fields", List.of(
            Map.of("name", "id", "dataType", "integer", "semanticRole", "dimension", "nullable", false),
            Map.of("name", "secret", "dataType", "string", "semanticRole", "dimension")))));
    var column = new CompositeViewBusinessModelMeta.ColumnDef(); column.setField("id"); column.setTitle("编号"); view.setColumns(List.of(column));
    view.getLayoutConfig().setPageSize(20);
    var service = service(dataset(20, 100), new JdbcSqlExecutionBackend());
    var query = new CompositeViewQueryService(service, request -> Map.of("currentUser", "trusted-user", "departmentId", 7));
    var request = new DatasetExecutionRequest(); request.setRuntimeParameters(Map.of("departmentId", 999));
    request.setExecutionContext(Map.of("departmentId", 999, "currentUser", "attacker"));
    var result = query.execute(view, request);
    assertEquals(7, ((Number) result.getRows().getFirst().get("id")).intValue());
    assertFalse(result.getRows().getFirst().containsKey("secret"));
    assertEquals(1L, result.getPageInfo().get("total"));
    request.setRuntimeParameters(Map.of("undeclared", "value"));
    assertThrows(BusinessException.class, () -> query.execute(view, request));
    assertTrue(result.getExecutionMeta().get("datasetName").toString().endsWith(".private-query"));
    var missing = new CompositeViewQueryService(service, value -> Map.of("currentUser", "user"));
    assertThrows(BusinessException.class, () -> missing.execute(view, request));
  }
}
