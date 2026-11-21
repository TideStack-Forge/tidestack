package com.ouroboros.statistics.reporting.dev.controller;

import com.ouroboros.statistics.reporting.dev.model.DatasetInferSchemaRequest;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetInferSchemaResult;
import com.ouroboros.statistics.reporting.dev.service.DatasetSchemaInferenceService;
import com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetParameterNormalizer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatasetInferSchemaControllerTest {
  private MockMvc mockMvc;
  private StubDatasetSchemaInferenceService inferenceService;
  private StubDatasetDevConfigService devConfigService;

  @BeforeEach
  void setUp() {
    inferenceService = new StubDatasetSchemaInferenceService();
    devConfigService = new StubDatasetDevConfigService();
    mockMvc = MockMvcBuilders.standaloneSetup(new StatisticsReportingDevController(
        appName -> "/tmp/" + appName,
        Collections.emptyList(),
        new DefaultDatasetParameterNormalizer(),
        (dataset, request) -> request,
        inferenceService,
        devConfigService
    )).build();
  }

  private static com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection platformConnection() {
    var connection = new com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection();
    connection.setKind("platform");
    connection.setPlatformDataSourceName("preview");
    return connection;
  }

  @Test
  void inferSchema_shouldReturnFieldsWithoutRowsAndNullSemanticRole() throws Exception {
    inferenceService.result = result(
        field("id", "integer"),
        field("created_at", "datetime")
    );

    mockMvc.perform(post("/api/dev/demo/dataset/infer-schema")
            .contentType("application/json")
            .content("{\"datasource\":\"reporting-main\",\"sql\":\"select id, created_at from sales\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.fields", hasSize(2)))
        .andExpect(jsonPath("$.data.fields[0].name").value("id"))
        .andExpect(jsonPath("$.data.fields[0].dataType").value("integer"))
        .andExpect(jsonPath("$.data.fields[0].semanticRole").doesNotExist())
        .andExpect(jsonPath("$.data.rows").doesNotExist());
  }

  @Test
  void inferSchema_shouldMergeDatasetDevConfigWhenDatasetNameIsProvided() throws Exception {
    DatasetDevConfigModel config = new DatasetDevConfigModel();
    config.setAppName("demo");
    config.setDatasetName("demo.sales");
    config.setDatasource("default");
    config.setParameters(Collections.<String, Object>singletonMap("startDate", "2026-01-01"));
    Map<String, Object> jdbcConnection = new LinkedHashMap<String, Object>();
    jdbcConnection.put("url", "jdbc:h2:mem:dataset_schema");
    config.setTestConnection(platformConnection());
    devConfigService.config = config;

    mockMvc.perform(post("/api/dev/demo/dataset/infer-schema")
            .contentType("application/json")
            .content("{\"datasetName\":\"demo.sales\",\"sql\":\"select ${startDate} as start_date\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    DatasetInferSchemaRequest request = inferenceService.lastRequest;
    org.junit.jupiter.api.Assertions.assertEquals("default", request.getDatasource());
    org.junit.jupiter.api.Assertions.assertEquals("2026-01-01", request.getParameters().get("startDate"));
    org.junit.jupiter.api.Assertions.assertEquals("preview", request.getTestConnection().getPlatformDataSourceName());
  }

  @Test
  void inferSchema_shouldIgnoreDevConfigFromAnotherDatasource() throws Exception {
    DatasetDevConfigModel config = new DatasetDevConfigModel();
    config.setAppName("demo");
    config.setDatasetName("demo.sales");
    config.setDatasource("old-datasource");
    config.setParameters(Collections.<String, Object>singletonMap("keyword", "dev-override-value"));
    config.setTestConnection(platformConnection());
    devConfigService.config = config;

    mockMvc.perform(post("/api/dev/demo/dataset/infer-schema")
            .contentType("application/json")
            .content("{\"datasetName\":\"demo.sales\",\"datasource\":\"current-datasource\","
                + "\"sql\":\"select ${keyword}\",\"parameters\":{\"keyword\":\"request-value\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    DatasetInferSchemaRequest request = inferenceService.lastRequest;
    org.junit.jupiter.api.Assertions.assertEquals("current-datasource", request.getDatasource());
    org.junit.jupiter.api.Assertions.assertEquals("request-value", request.getParameters().get("keyword"));
    org.junit.jupiter.api.Assertions.assertTrue(request.getTestConnection() == null);
  }

  @Test
  void testConnection_shouldReturnServiceResult() throws Exception {
    mockMvc.perform(post("/api/dev/demo/dataset/test-connection")
            .contentType("application/json")
            .content("{\"datasource\":\"default\",\"testConnection\":{\"kind\":\"platform\",\"platformDataSourceName\":\"preview\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    org.junit.jupiter.api.Assertions.assertEquals("demo", inferenceService.testConnectionAppName);
    org.junit.jupiter.api.Assertions.assertEquals(
        "preview",
        inferenceService.testConnectionRequest.getTestConnection().getPlatformDataSourceName()
    );
  }

  @Test
  void testConnection_shouldSurfaceConnectionFailure() throws Exception {
    inferenceService.testConnectionFailure =
        new IllegalArgumentException("datasource-unreachable: refused");

    DatasetInferSchemaRequest request = new DatasetInferSchemaRequest();
    request.setDatasource("default");

    PlatformException exception = assertThrows(PlatformException.class,
        () -> controller().testConnection("demo", request));
    assertEquals("datasource-unreachable: refused", exception.getMessage());
  }

  @Test
  void inferSchema_shouldReturnWarningsForFallbackMapping() throws Exception {
    DatasetInferSchemaResult result = result(field("payload", "string"));
    result.setWarnings(Collections.singletonList(
        "JDBC type " + Types.OTHER + "/JSON cannot be mapped exactly; fallback to string."
    ));
    inferenceService.result = result;

    mockMvc.perform(post("/api/dev/demo/dataset/infer-schema")
            .contentType("application/json")
            .content("{\"datasource\":\"reporting-main\",\"sql\":\"select payload from sales\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.warnings[0]", containsString("fallback to string")));
  }

  @Test
  void inferSchema_shouldSurfaceDatasourceNotFound() throws Exception {
    inferenceService.failure = new IllegalArgumentException("datasource-not-found: missing");

    DatasetInferSchemaRequest request = new DatasetInferSchemaRequest();
    request.setDatasource("missing");
    request.setSql("select 1");

    PlatformException exception = assertThrows(PlatformException.class,
        () -> controller().inferSchema("demo", request));
    assertEquals("datasource-not-found: missing", exception.getMessage());
  }

  @Test
  void inferSchema_shouldSurfaceDatasourceUnreachable() throws Exception {
    inferenceService.failure = new IllegalArgumentException("datasource-unreachable: timeout");

    DatasetInferSchemaRequest request = new DatasetInferSchemaRequest();
    request.setDatasource("reporting-main");
    request.setSql("select 1");

    PlatformException exception = assertThrows(PlatformException.class,
        () -> controller().inferSchema("demo", request));
    assertEquals("datasource-unreachable: timeout", exception.getMessage());
  }

  @Test
  void inferSchema_shouldSurfaceSqlInvalid() throws Exception {
    inferenceService.failure = new IllegalArgumentException("sql-invalid: syntax error");

    DatasetInferSchemaRequest request = new DatasetInferSchemaRequest();
    request.setDatasource("reporting-main");
    request.setSql("select from");

    PlatformException exception = assertThrows(PlatformException.class,
        () -> controller().inferSchema("demo", request));
    assertEquals("sql-invalid: syntax error", exception.getMessage());
  }

  private StatisticsReportingDevController controller() {
    return new StatisticsReportingDevController(
        appName -> "/tmp/" + appName,
        Collections.emptyList(),
        new DefaultDatasetParameterNormalizer(),
        (dataset, request) -> request,
        inferenceService,
        devConfigService
    );
  }

  @Test
  void getDatasetDevConfig_shouldReturnBlankConfigWhenMissing() throws Exception {
    mockMvc.perform(get("/api/dev/demo/dataset/demo.sales/dev-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.appName").value("demo"))
        .andExpect(jsonPath("$.data.datasetName").value("demo.sales"))
        .andExpect(jsonPath("$.data.datasource").doesNotExist())
        .andExpect(jsonPath("$.data.parameters").isMap())
        .andExpect(jsonPath("$.data.testConnection").doesNotExist());
  }

  @Test
  void saveDatasetDevConfig_shouldTrustPathAppAndDatasetName() throws Exception {
    mockMvc.perform(put("/api/dev/demo/dataset/demo.sales/dev-config")
            .contentType("application/json")
            .content("{\"appName\":\"other\",\"datasetName\":\"other.dataset\",\"datasource\":\"default\","
                + "\"parameters\":{\"keyword\":\"A-1\"},"
                + "\"testConnection\":{\"kind\":\"platform\",\"platformDataSourceName\":\"preview\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.appName").value("demo"))
        .andExpect(jsonPath("$.data.datasetName").value("demo.sales"))
        .andExpect(jsonPath("$.data.datasource").value("default"))
        .andExpect(jsonPath("$.data.parameters.keyword").value("A-1"))
        .andExpect(jsonPath("$.data.testConnection.platformDataSourceName").value("preview"));

    mockMvc.perform(get("/api/dev/demo/dataset/demo.sales/dev-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.appName").value("demo"))
        .andExpect(jsonPath("$.data.datasetName").value("demo.sales"))
        .andExpect(jsonPath("$.data.parameters.keyword").value("A-1"));
  }

  private DatasetInferSchemaResult result(DatasetFieldDefinition... fields) {
    DatasetInferSchemaResult result = new DatasetInferSchemaResult();
    result.setFields(Arrays.asList(fields));
    return result;
  }

  private DatasetFieldDefinition field(String name, String dataType) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setTitle(name);
    field.setDataType(dataType);
    field.setSemanticRole(null);
    return field;
  }

  private static class StubDatasetSchemaInferenceService implements DatasetSchemaInferenceService {
    private DatasetInferSchemaResult result = new DatasetInferSchemaResult();
    private RuntimeException failure;
    private DatasetInferSchemaRequest lastRequest;
    private RuntimeException testConnectionFailure;
    private String testConnectionAppName;
    private DatasetInferSchemaRequest testConnectionRequest;

    @Override
    public DatasetInferSchemaResult inferSchema(String appName, DatasetInferSchemaRequest request) {
      lastRequest = request;
      if (failure != null) {
        throw failure;
      }
      return result;
    }

    @Override
    public void testConnection(String appName, DatasetInferSchemaRequest request) {
      testConnectionAppName = appName;
      testConnectionRequest = request;
      if (testConnectionFailure != null) {
        throw testConnectionFailure;
      }
    }
  }

  private static class StubDatasetDevConfigService implements DatasetDevConfigService {
    private DatasetDevConfigModel config;

    @Override
    public io.vavr.control.Try<DatasetDevConfigModel> save(DatasetDevConfigModel config) {
      this.config = config;
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
