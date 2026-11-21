package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.executor.SqlDatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDatasetExecutionServiceTest {

  @Test
  void shouldExposeOnlyFullDependencyConstructor() {
    var constructors = DefaultDatasetExecutionService.class.getDeclaredConstructors();

    assertEquals(1, constructors.length);
    assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
    assertEquals(Arrays.asList(
            com.ouroboros.statistics.reporting.runtime.center.DefinitionCenter.class,
            DatasetParameterNormalizer.class,
            DatasetPermissionInjector.class,
            List.class,
            DatasetExecutionContextResolver.class
        ),
        Arrays.asList(constructors[0].getParameterTypes()));
  }

  @Test
  void shouldRejectNullDependenciesWhenConstructed() {
    var datasetDefinitionCenter = new DefaultDatasetDefinitionCenter(Collections.emptyList());
    var parameterNormalizer = new DefaultDatasetParameterNormalizer();
    DatasetPermissionInjector datasetPermissionInjector = (dataset, request) -> request;
    List<com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor> datasetExecutors =
        Collections.emptyList();
    DatasetExecutionContextResolver executionContextResolver =
        request -> Collections.<String, Object>emptyMap();

    assertEquals("datasetDefinitionCenter is required", assertThrows(NullPointerException.class,
        () -> new DefaultDatasetExecutionService(
            null,
            parameterNormalizer,
            datasetPermissionInjector,
            datasetExecutors,
            executionContextResolver
        )).getMessage());
    assertEquals("parameterNormalizer is required", assertThrows(NullPointerException.class,
        () -> new DefaultDatasetExecutionService(
            datasetDefinitionCenter,
            null,
            datasetPermissionInjector,
            datasetExecutors,
            executionContextResolver
        )).getMessage());
    assertEquals("datasetPermissionInjector is required", assertThrows(NullPointerException.class,
        () -> new DefaultDatasetExecutionService(
            datasetDefinitionCenter,
            parameterNormalizer,
            null,
            datasetExecutors,
            executionContextResolver
        )).getMessage());
    assertEquals("datasetExecutors is required", assertThrows(NullPointerException.class,
        () -> new DefaultDatasetExecutionService(
            datasetDefinitionCenter,
            parameterNormalizer,
            datasetPermissionInjector,
            null,
            executionContextResolver
        )).getMessage());
    assertEquals("executionContextResolver is required", assertThrows(NullPointerException.class,
        () -> new DefaultDatasetExecutionService(
            datasetDefinitionCenter,
            parameterNormalizer,
            datasetPermissionInjector,
            datasetExecutors,
            null
        )).getMessage());
  }

  @Test
  void shouldRunLoadNormalizeAuthorizeExecuteProjectInOrder() {
    var service = new SpyDatasetExecutionService();

    service.execute(minimalRequest());

    assertEquals(Arrays.asList("load", "normalize", "authorize", "compile", "execute", "project"),
        service.getInvocationOrder());
  }

  @Test
  void shouldLoadEnabledDatasetFromCenterBeforeExecuting() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);

    var keyword = new ParameterDefinition();
    keyword.setName("keyword");
    keyword.setRequired(true);
    dataset.setParameters(Collections.<ParameterDefinition>singletonList(keyword));

    var service = new DefaultDatasetExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(() ->
            Collections.<DatasetDefinition>singletonList(dataset))),
        new DefaultDatasetParameterNormalizer(),
        (loadedDataset, request) -> request,
        Collections.emptyList(),
        request -> request == null ? Collections.<String, Object>emptyMap() : request.getExecutionContext()) {
      @Override
      protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest request) {
        var result = new DatasetResult();
        result.setAppliedParameters(request.getAppliedParameters());
        return result;
      }
    };

    var request = minimalRequest();
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("keyword", "phone"));

    var result = service.execute(request);

    assertEquals("phone", result.getAppliedParameters().get("keyword"));
  }

  @Test
  void shouldDispatchSqlDatasetToSupportingExecutor() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);
    dataset.setSchema(schema(field("total", "metric")));

    var queryDefinition = new SqlQueryDefinition();
    queryDefinition.setSql("select 1 as total");
    dataset.setQueryDefinition(queryDefinition);

    var service = new DefaultDatasetExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(() ->
            Collections.<DatasetDefinition>singletonList(dataset))),
        new DefaultDatasetParameterNormalizer(),
        (loadedDataset, request) -> request,
        Collections.singletonList(new SqlDatasetExecutor(
            new DefaultSqlDialectResolver(),
            (sqlQueryDefinition, request) -> new SqlDataSourceContext()
                .setDatasource("analytics-h2")
                .setDialect("h2")
                .setDialectVersion("2.2.224"),
            (context, sql, parameters) -> new SqlExecutionBackend.SqlQueryResult()
                .setRows(Collections.<java.util.Map<String, Object>>emptyList())
                .setColumnNames(Collections.singletonList("total"))
        )),
        request -> request == null ? Collections.<String, Object>emptyMap() : request.getExecutionContext());

    var result = service.execute(minimalRequest());

    assertEquals("sql", result.getDiagnostics().getQueryType());
  }

  @Test
  void shouldRejectExecutionWhenRequiredRoleIsMissing() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);
    dataset.setSchema(schema(field("total", "metric")));
    dataset.setQueryDefinition(sqlQuery("select 1 as total"));
    dataset.setPermissionDefinition(Collections.<String, Object>singletonMap(
        "requiredRoles",
        Collections.singletonList("statistics-reporting.read")
    ));

    var request = minimalRequest();
    request.setExecutionContext(Collections.<String, Object>singletonMap(
        "roles",
        Collections.singletonList("guest")
    ));

    var service = new DefaultDatasetExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(() ->
            Collections.<DatasetDefinition>singletonList(dataset))),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        Collections.emptyList(),
        input -> input == null ? Collections.<String, Object>emptyMap() : input.getExecutionContext()) {
      @Override
      protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest request) {
        DatasetResult result = new DatasetResult();
        Map<String, Object> executionMeta = new java.util.LinkedHashMap<String, Object>();
        executionMeta.put("resolvedRoles", request.getExecutionContext().get("roles"));
        result.setExecutionMeta(executionMeta);
        return result;
      }
    };

    var error = assertThrows(IllegalArgumentException.class, () -> service.execute(request));

    assertEquals("dataset access denied: demo.sales", error.getMessage());
  }

  @Test
  void shouldReplaceClientExecutionContextBeforePermissionInjection() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);
    dataset.setSchema(schema(field("total", "metric")));
    dataset.setQueryDefinition(sqlQuery("select 1 as total"));
    dataset.setPermissionDefinition(Collections.<String, Object>singletonMap(
        "requiredRoles",
        Collections.singletonList("statistics-reporting.read")
    ));

    var request = minimalRequest();
    request.setExecutionContext(Collections.<String, Object>singletonMap(
        "roles",
        Collections.singletonList("guest")
    ));

    var service = new DefaultDatasetExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(() ->
            Collections.<DatasetDefinition>singletonList(dataset))),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        Collections.emptyList(),
        input -> Collections.<String, Object>singletonMap(
            "roles",
            Collections.singletonList("statistics-reporting.read")
        )) {
      @Override
      protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest request) {
        DatasetResult result = new DatasetResult();
        Map<String, Object> executionMeta = new java.util.LinkedHashMap<String, Object>();
        executionMeta.put("resolvedRoles", request.getExecutionContext().get("roles"));
        result.setExecutionMeta(executionMeta);
        return result;
      }
    };

    DatasetResult result = service.execute(request);

    assertNotNull(result);
    assertEquals(Collections.singletonList("statistics-reporting.read"),
        result.getExecutionMeta().get("resolvedRoles"));
    assertEquals(Collections.singletonList("guest"), request.getExecutionContext().get("roles"));
  }

  @Test
  void shouldOverrideRuntimeParameterWithTrustedScopeBinding() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);

    var departmentId = new ParameterDefinition();
    departmentId.setName("departmentId");
    dataset.setParameters(Collections.singletonList(departmentId));
    dataset.setPermissionDefinition(Collections.<String, Object>singletonMap(
        "scopeBindings",
        Collections.<String, String>singletonMap("departmentId", "departmentId")
    ));

    var request = minimalRequest();
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("departmentId", "D-200"));

    var service = new DefaultDatasetExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(() ->
            Collections.<DatasetDefinition>singletonList(dataset))),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        Collections.emptyList(),
        input -> Collections.<String, Object>singletonMap("departmentId", "D-100")) {
      @Override
      protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest request) {
        DatasetResult result = new DatasetResult();
        result.setAppliedParameters(request.getAppliedParameters());
        return result;
      }
    };

    var result = service.execute(request);

    assertEquals("D-100", result.getAppliedParameters().get("departmentId"));
    assertEquals(Collections.singletonList("departmentId<-departmentId"),
        result.getDiagnostics().getAttributes().get("permissionBindings"));
  }

  @Test
  void shouldKeepTrustedScopeBindingWhenClientRuntimeParametersAttemptTamper() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);

    var departmentId = new ParameterDefinition();
    departmentId.setName("departmentId");
    dataset.setParameters(Collections.singletonList(departmentId));
    dataset.setPermissionDefinition(Collections.<String, Object>singletonMap(
        "scopeBindings",
        Collections.<String, String>singletonMap("departmentId", "departmentId")
    ));

    var request = minimalRequest();
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("departmentId", "D-999"));

    var service = new DefaultDatasetExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(() ->
            Collections.<DatasetDefinition>singletonList(dataset))),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        Collections.emptyList(),
        input -> Collections.<String, Object>singletonMap("departmentId", "D-100")) {
      @Override
      protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest request) {
        DatasetResult result = new DatasetResult();
        result.setAppliedParameters(request.getAppliedParameters());
        return result;
      }
    };

    var result = service.execute(request);

    assertEquals("D-100", result.getAppliedParameters().get("departmentId"));
    assertEquals(Collections.singletonList("departmentId<-departmentId"),
        result.getDiagnostics().getAttributes().get("permissionBindings"));
    assertEquals("D-999", request.getRuntimeParameters().get("departmentId"));
  }

  @Test
  void shouldRejectMissingTrustedScopeBeforeExecutingEvenWhenClientProvidesTheScope() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);
    var departmentId = new ParameterDefinition();
    departmentId.setName("departmentId");
    dataset.setParameters(Collections.singletonList(departmentId));
    dataset.setPermissionDefinition(Collections.<String, Object>singletonMap(
        "scopeBindings", Collections.singletonMap("departmentId", "departmentId")));

    var request = minimalRequest();
    request.setRuntimeParameters(Collections.singletonMap("departmentId", "D-999"));
    request.setExecutionContext(Collections.singletonMap("departmentId", "D-999"));
    var service = new DefaultDatasetExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(() ->
            Collections.singletonList(dataset))),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        Collections.emptyList(),
        input -> Collections.emptyMap()) {
      @Override
      protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest authorizedRequest) {
        throw new AssertionError("query must not execute without its trusted permission scope");
      }
    };

    var error = assertThrows(IllegalArgumentException.class, () -> service.execute(request));

    assertEquals("missing required permission scope binding: departmentId", error.getMessage());
    assertEquals("D-999", request.getRuntimeParameters().get("departmentId"));
    assertEquals("D-999", request.getExecutionContext().get("departmentId"));
  }

  @Test
  void shouldCompileDynamicFilterAfterPermissionInjectionWithoutOverwritingAppliedParameters() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);
    dataset.setSchema(schema(field("departmentId", "dimension"), field("total", "metric")));

    var departmentId = new ParameterDefinition();
    departmentId.setName("departmentId");
    dataset.setParameters(Collections.singletonList(departmentId));
    dataset.setPermissionDefinition(Collections.<String, Object>singletonMap(
        "scopeBindings",
        Collections.<String, String>singletonMap("departmentId", "departmentId")
    ));

    DynamicFilterSpec dynamicFilter = new DynamicFilterSpec();
    dynamicFilter.setFilter(condition("total", "gt", 10));
    dynamicFilter.setSort(Collections.singletonList(sort("total", "desc")));

    var request = minimalRequest();
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("departmentId", "D-999"));
    request.setDynamicFilter(dynamicFilter);

    var service = new DefaultDatasetExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(() ->
            Collections.<DatasetDefinition>singletonList(dataset))),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        Collections.emptyList(),
        input -> Collections.<String, Object>singletonMap("departmentId", "D-100")) {
      @Override
      protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest request) {
        CompiledDynamicFilter compiledFilter =
            (CompiledDynamicFilter) request.getExecutionContext().get(DynamicFilterCompiler.CONTEXT_KEY);
        DatasetResult result = new DatasetResult();
        result.setAppliedParameters(new LinkedHashMap<String, Object>(request.getAppliedParameters()));
        Map<String, Object> executionMeta = new LinkedHashMap<String, Object>();
        executionMeta.put("whereClause", compiledFilter.getWhereClause());
        executionMeta.put("orderByClause", compiledFilter.getOrderByClause());
        executionMeta.put("dynamicParameters", compiledFilter.getParameters());
        result.setExecutionMeta(executionMeta);
        return result;
      }
    };

    var result = service.execute(request);

    assertEquals("D-100", result.getAppliedParameters().get("departmentId"));
    assertEquals(Collections.singletonMap("__dyn0", 10), result.getExecutionMeta().get("dynamicParameters"));
    assertTrue(String.valueOf(result.getExecutionMeta().get("whereClause")).contains(":__dyn0"));
    assertTrue(String.valueOf(result.getExecutionMeta().get("orderByClause")).contains("DESC"));
    assertEquals("D-999", request.getRuntimeParameters().get("departmentId"));
  }

  @Test
  void shouldRejectDynamicFilterFieldOutsideDatasetSchema() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new DynamicFilterCompiler().compile(datasetWithSchema("total"),
            requestWithDynamicFilter(condition("secret", "eq", "hidden"), null, null)));

    assertTrue(error.getMessage().contains("filter-field-not-allowed"));
  }

  @Test
  void shouldRejectUnsupportedDynamicFilterOperator() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new DynamicFilterCompiler().compile(datasetWithSchema("total"),
            requestWithDynamicFilter(condition("total", "between", 1), null, null)));

    assertEquals("filter-operator-invalid", error.getMessage());
  }

  @Test
  void shouldRejectSortFieldOutsideDatasetSchema() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new DynamicFilterCompiler().compile(datasetWithSchema("total"),
            requestWithDynamicFilter(null, Collections.singletonList(sort("secret", "asc")), null)));

    assertTrue(error.getMessage().contains("sort-field-not-allowed"));
  }

  @Test
  void shouldRejectInvalidSortDirection() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new DynamicFilterCompiler().compile(datasetWithSchema("total"),
            requestWithDynamicFilter(null, Collections.singletonList(sort("total", "sideways")), null)));

    assertEquals("sort-direction-invalid", error.getMessage());
  }

  @Test
  void shouldRejectInvalidPageSpec() {
    var page = new PageSpec();
    page.setPageNo(0);
    page.setPageSize(20);
    IllegalArgumentException invalidPage = assertThrows(IllegalArgumentException.class,
        () -> new DynamicFilterCompiler().compile(datasetWithSchema("total"),
            requestWithDynamicFilter(null, null, page)));

    page.setPageNo(1);
    page.setPageSize(1001);
    com.ouroboros.exception.BusinessException invalidSize = assertThrows(com.ouroboros.exception.BusinessException.class,
        () -> new DynamicFilterCompiler().compile(datasetWithSchema("total"),
            requestWithDynamicFilter(null, null, page)));

    assertEquals("page-invalid", invalidPage.getMessage());
    assertTrue(invalidSize.getMessage().contains("dataset.page-size-exceeded"));
  }

  @Test
  void shouldRejectPageSpecWhenOffsetWouldOverflow() {
    var page = new PageSpec();
    page.setPageNo(Integer.MAX_VALUE);
    page.setPageSize(500);

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new DynamicFilterCompiler().compile(datasetWithSchema("total"),
            requestWithDynamicFilter(null, null, page)));

    assertEquals("page-invalid", error.getMessage());
  }

  private static DatasetExecutionRequest minimalRequest() {
    var request = new DatasetExecutionRequest();
    request.setDatasetName("demo.sales");
    request.setRuntimeParameters(Collections.<String, Object>emptyMap());
    return request;
  }

  private static class SpyDatasetExecutionService extends DefaultDatasetExecutionService {
    private final List<String> invocationOrder = new ArrayList<String>();

    private SpyDatasetExecutionService() {
      super(
          new DefaultDatasetDefinitionCenter(Collections.emptyList()),
          new DefaultDatasetParameterNormalizer(),
          (dataset, request) -> request,
          Collections.emptyList(),
          request -> request == null ? Collections.<String, Object>emptyMap() : request.getExecutionContext()
      );
    }

    @Override
    protected LoadedDefinitions loadDefinitions(DatasetExecutionRequest request) {
      invocationOrder.add("load");
      var dataset = new DatasetDefinition();
      dataset.setName(request.getDatasetName());
      return new LoadedDefinitions(dataset);
    }

    @Override
    protected DatasetExecutionRequest normalizeRequest(LoadedDefinitions definitions,
                                                      DatasetExecutionRequest request) {
      invocationOrder.add("normalize");
      request.setAppliedParameters(Collections.<String, Object>emptyMap());
      return request;
    }

    @Override
    protected DatasetExecutionRequest authorizeRequest(LoadedDefinitions definitions,
                                                       DatasetExecutionRequest request) {
      invocationOrder.add("authorize");
      return request;
    }

    @Override
    protected DatasetExecutionRequest compileDynamicFilter(LoadedDefinitions definitions,
                                                           DatasetExecutionRequest request) {
      invocationOrder.add("compile");
      return request;
    }

    @Override
    protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest request) {
      invocationOrder.add("execute");
      return new DatasetResult();
    }

    @Override
    protected DatasetResult projectResult(LoadedDefinitions definitions,
                                          DatasetExecutionRequest request,
                                          DatasetResult result) {
      invocationOrder.add("project");
      return result;
    }

    private List<String> getInvocationOrder() {
      return invocationOrder;
    }
  }

  private static SqlQueryDefinition sqlQuery(String sql) {
    var query = new SqlQueryDefinition();
    query.setSql(sql);
    return query;
  }

  private static DatasetSchemaDefinition schema(DatasetFieldDefinition... fields) {
    var schema = new DatasetSchemaDefinition();
    schema.setFields(Arrays.asList(fields));
    return schema;
  }

  private static DatasetFieldDefinition field(String name, String semanticRole) {
    var field = new DatasetFieldDefinition();
    field.setName(name);
    field.setDataType("string");
    field.setSemanticRole(semanticRole);
    field.setVisibleByDefault(Boolean.TRUE);
    return field;
  }

  private static DatasetDefinition datasetWithSchema(String... fields) {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setSchema(schema(Arrays.stream(fields)
        .map(field -> field(field, "metric"))
        .toArray(DatasetFieldDefinition[]::new)));
    return dataset;
  }

  private static DatasetExecutionRequest requestWithDynamicFilter(FilterNode filter,
                                                                  List<SortSpec> sort,
                                                                  PageSpec page) {
    DatasetExecutionRequest request = minimalRequest();
    if (filter != null || sort != null) {
      DynamicFilterSpec spec = new DynamicFilterSpec();
      spec.setFilter(filter);
      spec.setSort(sort);
      request.setDynamicFilter(spec);
    }
    request.setPage(page);
    return request;
  }

  private static FilterNode condition(String field, String op, Object value) {
    FilterNode node = new FilterNode();
    node.setField(field);
    node.setOp(op);
    node.setValue(value);
    return node;
  }

  private static SortSpec sort(String field, String direction) {
    SortSpec sortSpec = new SortSpec();
    sortSpec.setField(field);
    sortSpec.setDirection(direction);
    return sortSpec;
  }

}
