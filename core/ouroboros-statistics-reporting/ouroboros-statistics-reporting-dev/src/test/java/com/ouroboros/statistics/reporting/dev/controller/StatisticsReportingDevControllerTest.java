package com.ouroboros.statistics.reporting.dev.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultSqlDialectResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetParameterNormalizer;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.execution.JdbcSqlExecutionBackend;
import com.ouroboros.statistics.reporting.runtime.execution.SpringSqlDataSourceProvider;
import com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.executor.SqlDatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatisticsReportingDevControllerTest {
  @TempDir
  Path workspace;

  private static com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection platformConnection() {
    var connection = new com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection();
    connection.setKind("platform");
    connection.setPlatformDataSourceName("preview");
    return connection;
  }

  @Test
  void shouldPreviewEnabledWorkspaceDatasetThroughRuntimeShell() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    writeDataset(appHome, StatisticsReportingDevTestSupport.minimalDataset("demo.sales", true));

    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();
    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appHomeResolver,
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector()
    );

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("keyword", "A-1"));

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("A-1", response.getData().getAppliedParameters().get("keyword"));
    assertEquals("A-1", response.getData().getRows().get(0).get("id"));
  }

  @Test
  void shouldPreviewDisabledWorkspaceDatasetForDesignTime() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    writeDataset(appHome, StatisticsReportingDevTestSupport.minimalDataset("demo.sales", false));

    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();
    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appHomeResolver,
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector()
    );

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("keyword", "A-1"));

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("A-1", response.getData().getAppliedParameters().get("keyword"));
  }

  @Test
  void shouldPreviewInlineDatasetBeforeSavedWorkspaceDefinition() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    LowcodeDatasetDefinition savedDataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    savedDataset.getSchema().getFields().get(0).setName("saved_id");
    writeDataset(appHome, savedDataset);

    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();
    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appHomeResolver,
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector()
    );

    LowcodeDatasetDefinition inlineDataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    inlineDataset.getSchema().getFields().get(0).setName("inline_preview_id");

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");
    request.setDataset(inlineDataset);

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals(
        "inline_preview_id",
        response.getData().getResolvedSchema().getFields().get(0).getName()
    );
  }

  @Test
  void shouldApplyMatchingDatasetDevConfigBeforeDatasetMetadataDefaultsDuringPreview() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    dataset.getParameters().get(0).setDefaultValue("metadata-default");
    dataset.getQueryDefinition().setType("sql");
    ((com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition) dataset.getQueryDefinition())
        .setDatasource("metadata-datasource");
    writeDataset(appHome, dataset);

    DatasetDevConfigModel config = new DatasetDevConfigModel();
    config.setAppName("demo-app");
    config.setDatasetName("demo.sales");
    config.setDatasource("metadata-datasource");
    config.setParameters(Collections.<String, Object>singletonMap("keyword", "dev-keyword"));

    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();
    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appHomeResolver,
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        unavailableSchemaInferenceService(),
        new StubDatasetDevConfigService(config)
    );

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("dev-keyword", response.getData().getAppliedParameters().get("keyword"));
    assertEquals("metadata-datasource",
        response.getData().getDiagnostics().getAttributes().get("datasource"));
  }

  @Test
  void shouldIgnoreStaleDatasetDevConfigWhenPreviewUsesMetadataDatasource() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    dataset.getParameters().get(0).setDefaultValue("metadata-default");
    ((com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition) dataset.getQueryDefinition())
        .setDatasource("current-datasource");
    writeDataset(appHome, dataset);

    DatasetDevConfigModel config = new DatasetDevConfigModel();
    config.setAppName("demo-app");
    config.setDatasetName("demo.sales");
    config.setDatasource("old-datasource");
    config.setParameters(Collections.<String, Object>singletonMap("keyword", "dev-override-value"));
    config.setTestConnection(platformConnection());

    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appName -> workspace.resolve(appName).toString(),
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        unavailableSchemaInferenceService(),
        new StubDatasetDevConfigService(config)
    );

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("metadata-default", response.getData().getAppliedParameters().get("keyword"));
    assertFalse(response.getData().getDiagnostics().getAttributes().containsKey("datasource"));
  }

  @Test
  void shouldPreviewThroughRealJdbcUsingPlatformSideDatasetDevConfig() {
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    dataset.getQueryDefinition().setType("sql");
    ((com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition) dataset.getQueryDefinition())
        .setDatasource("default");

    DatasetDevConfigModel config = new DatasetDevConfigModel();
    config.setAppName("demo-app");
    config.setDatasetName("demo.sales");
    config.setDatasource("default");
    config.setParameters(Collections.<String, Object>singletonMap("keyword", "JDBC-42"));
    Map<String, Object> jdbcConnection = new java.util.LinkedHashMap<String, Object>();
    jdbcConnection.put("driverClassName", "org.h2.Driver");
    jdbcConnection.put("url", "jdbc:h2:mem:dataset_dev_preview;DB_CLOSE_DELAY=-1");
    jdbcConnection.put("username", "sa");
    jdbcConnection.put("password", "");
    config.setTestConnection(platformConnection());

    DatasetExecutor jdbcExecutor = new SqlDatasetExecutor(
        new DefaultSqlDialectResolver(),
        new SpringSqlDataSourceProvider(Collections.emptyMap(), null),
        new JdbcSqlExecutionBackend()
    );
    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appName -> workspace.resolve(appName).toString(),
        Collections.singletonList(jdbcExecutor),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        unavailableSchemaInferenceService(),
        new StubDatasetDevConfigService(config)
    );

    var sources = org.mockito.Mockito.mock(com.ouroboros.statistics.reporting.dev.service.DatasetDesignDataSourceService.class);
    jdbcConnection.put("type", "jdbc");
    org.mockito.Mockito.when(sources.connection(org.mockito.ArgumentMatchers.eq("demo-app"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(java.util.Optional.of(new com.ouroboros.dev.datasource.model.DataSourceDefine(jdbcConnection)));
    controller.setPreviewExecutorFactory(new com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory(
        sources, new DefaultSqlDialectResolver(), new JdbcSqlExecutionBackend()));

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");
    request.setDataset(dataset);

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("JDBC-42", response.getData().getRows().get(0).get("id"));
    assertEquals("default", response.getData().getExecutionMeta().get("datasource"));
  }

  @Test
  void shouldKeepRequestParametersAheadOfDatasetDevConfigDuringPreview() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    dataset.setParameters(new java.util.ArrayList<ParameterDefinition>(dataset.getParameters()));
    ParameterDefinition optionalParameter = new ParameterDefinition();
    optionalParameter.setName("region");
    optionalParameter.setType("string");
    dataset.getParameters().add(optionalParameter);
    writeDataset(appHome, dataset);

    DatasetDevConfigModel config = new DatasetDevConfigModel();
    config.setAppName("demo-app");
    config.setDatasetName("demo.sales");
    Map<String, Object> configParameters = new java.util.LinkedHashMap<String, Object>();
    configParameters.put("keyword", "dev-keyword");
    configParameters.put("region", "East");
    config.setParameters(configParameters);

    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();
    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appHomeResolver,
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        unavailableSchemaInferenceService(),
        new StubDatasetDevConfigService(config)
    );

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("keyword", "request-keyword"));

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("request-keyword", response.getData().getAppliedParameters().get("keyword"));
    assertEquals("East", response.getData().getAppliedParameters().get("region"));
  }

  @Test
  void shouldIgnoreDatasetDevConfigWhenPreviewExplicitlyUsesAnotherDatasource() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    dataset.getParameters().get(0).setDefaultValue("metadata-default");
    writeDataset(appHome, dataset);

    DatasetDevConfigModel config = new DatasetDevConfigModel();
    config.setAppName("demo-app");
    config.setDatasetName("demo.sales");
    config.setDatasource("old-datasource");
    config.setParameters(Collections.<String, Object>singletonMap("keyword", "dev-override-value"));

    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appName -> workspace.resolve(appName).toString(),
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        unavailableSchemaInferenceService(),
        new StubDatasetDevConfigService(config)
    );

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");
    request.setExecutionContext(Collections.<String, Object>singletonMap(
        "datasource",
        "current-datasource"
    ));

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("metadata-default", response.getData().getAppliedParameters().get("keyword"));
    assertEquals(
        "current-datasource",
        response.getData().getDiagnostics().getAttributes().get("datasource")
    );
  }

  @Test
  void shouldLeavePreviewBehaviorUnchangedWhenDatasetDevConfigIsMissing() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );
    dataset.getParameters().get(0).setDefaultValue("metadata-default");
    writeDataset(appHome, dataset);

    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();
    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appHomeResolver,
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector(),
        unavailableSchemaInferenceService(),
        new StubDatasetDevConfigService()
    );

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");

    WebApiResult<DatasetResult> response = controller.preview("demo-app", request);

    assertEquals(0, response.getCode());
    assertEquals("metadata-default", response.getData().getAppliedParameters().get("keyword"));
    assertFalse(response.getData().getDiagnostics().getAttributes().containsKey("datasource"));
  }

  @Test
  void shouldKeepDatasetDefinitionJsonIsolatedFromDevConfigFields() throws Exception {
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        false
    );

    String json = Json.toPrettyJsonString(dataset);

    assertFalse(json.contains("jdbcConnection"));
    assertFalse(json.contains("testDatasourceName"));
    assertFalse(json.contains("testParameters"));
    assertFalse(json.contains("fixture"));
  }

  @Test
  void shouldEnforceDatasetReadPermissionWhenPreviewUsesRuntimePermissionInjector() throws Exception {
    Path appHome = workspace.resolve("demo-app");
    LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
        "demo.sales",
        true
    );
    dataset.setPermissionDefinition(Collections.<String, Object>singletonMap(
        "requiredRoles",
        Collections.singletonList("statistics-reporting.read")
    ));
    writeDataset(appHome, dataset);

    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();
    StatisticsReportingDevController controller = new StatisticsReportingDevController(
        appHomeResolver,
        Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
        new DefaultDatasetParameterNormalizer(),
        new DefaultDatasetPermissionInjector()
    );

    DatasetPreviewRequest request = new DatasetPreviewRequest();
    request.setDatasetName("demo.sales");
    request.setExecutionContext(Collections.<String, Object>singletonMap(
        "roles",
        Collections.singletonList("guest")
    ));

    PlatformException error = assertThrows(PlatformException.class,
        () -> controller.preview("demo-app", request));

    assertEquals("dataset access denied: demo.sales", error.getMessage());
  }

  @Test
  void shouldRejectNullPermissionInjectorWhenConstructed() {
    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();

    NullPointerException error = assertThrows(NullPointerException.class,
        () -> new StatisticsReportingDevController(
            appHomeResolver,
            Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
            new DefaultDatasetParameterNormalizer(),
            null
        ));

    assertEquals("datasetPermissionInjector is required", error.getMessage());
  }

  @Test
  void shouldRejectNullDatasetDevConfigServiceWhenConstructed() {
    AppHomeResolver appHomeResolver = appName -> workspace.resolve(appName).toString();

    NullPointerException error = assertThrows(NullPointerException.class,
        () -> new StatisticsReportingDevController(
            appHomeResolver,
            Collections.<DatasetExecutor>singletonList(new StubDatasetExecutor()),
            new DefaultDatasetParameterNormalizer(),
            new DefaultDatasetPermissionInjector(),
            unavailableSchemaInferenceService(),
            null
        ));

    assertEquals("datasetDevConfigService is required", error.getMessage());
  }

  private void writeDataset(Path appHome, LowcodeDatasetDefinition dataset) throws Exception {
    Path file = appHome.resolve("statistics-reporting/datasets/demo.sales.dataset.json");
    Files.createDirectories(file.getParent());
    Files.write(file, Json.toPrettyJsonString(dataset).getBytes(StandardCharsets.UTF_8));
  }

  private static com.ouroboros.statistics.reporting.dev.service.DatasetSchemaInferenceService unavailableSchemaInferenceService() {
    return (appName, request) -> {
      throw new UnsupportedOperationException("dataset schema inference service is not configured");
    };
  }

  private static class StubDatasetExecutor implements DatasetExecutor {
    @Override
    public boolean supports(DatasetDefinition dataset) {
      return true;
    }

    @Override
    public DatasetResult execute(DatasetDefinition dataset, DatasetExecutionRequest request) {
      DatasetResult result = new DatasetResult();
      result.setResolvedSchema(dataset.getSchema());
      result.setRows(Collections.singletonList(row("id", "A-1", "name", "Phone", "secret", "hidden")));
      if (request != null && request.getExecutionContext() != null
          && request.getExecutionContext().containsKey("datasource")) {
        result.getDiagnostics().getAttributes().put("datasource",
            request.getExecutionContext().get("datasource"));
      }
      return result;
    }

    private Map<String, Object> row(Object... values) {
      Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
      for (int i = 0; i < values.length; i += 2) {
        row.put((String) values[i], values[i + 1]);
      }
      return row;
    }
  }

  private static class StubDatasetDevConfigService implements DatasetDevConfigService {
    private final DatasetDevConfigModel config;

    private StubDatasetDevConfigService() {
      this(null);
    }

    private StubDatasetDevConfigService(DatasetDevConfigModel config) {
      this.config = config;
    }

    @Override
    public io.vavr.control.Try<DatasetDevConfigModel> save(DatasetDevConfigModel config) {
      return io.vavr.control.Try.success(config);
    }

    @Override
    public java.util.Optional<DatasetDevConfigModel> findByDataset(String appName, String datasetName) {
      if (config == null || !same(config.getAppName(), appName) || !same(config.getDatasetName(), datasetName)) {
        return java.util.Optional.empty();
      }
      return java.util.Optional.of(config);
    }

    @Override
    public List<DatasetDevConfigModel> findByAppName(String appName) {
      if (config == null || !same(config.getAppName(), appName)) {
        return Collections.emptyList();
      }
      return Collections.singletonList(config);
    }

    private static boolean same(String left, String right) {
      return left == null ? right == null : left.equals(right);
    }
  }
}
