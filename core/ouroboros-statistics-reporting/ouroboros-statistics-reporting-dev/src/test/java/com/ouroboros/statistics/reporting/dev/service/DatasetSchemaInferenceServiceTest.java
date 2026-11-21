package com.ouroboros.statistics.reporting.dev.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.datasource.dto.TableInfo;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.model.PlatformDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlDialectDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.dev.model.DatasetInferSchemaRequest;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetInferSchemaResult;
import com.ouroboros.statistics.reporting.dev.service.impl.DefaultDatasetSchemaInferenceService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetSchemaInferenceServiceTest {
  private static final String APP_NAME = "demo-app";
  private static final String DATASOURCE_NAME = "reporting-main";
  private static final String PLATFORM_DATASOURCE_NAME = "platform-main";
  private static final String JDBC_URL =
      "jdbc:h2:mem:dataset_infer_schema;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";

  private DatasetSchemaInferenceService service;

  @BeforeEach
  void setUp() throws Exception {
    Class.forName("org.h2.Driver");
    try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
         Statement statement = connection.createStatement()) {
      statement.execute("drop table if exists sales");
      statement.execute("create table sales ("
          + "id bigint primary key, "
          + "name varchar(64), "
          + "amount decimal(18,2), "
          + "created_at timestamp, "
          + "enabled boolean, "
          + "payload json"
          + ")");
      statement.execute("drop alias if exists FAIL_IF_EXECUTED");
      statement.execute("create alias FAIL_IF_EXECUTED for \""
          + DatasetSchemaInferenceServiceTest.class.getName()
          + ".failIfExecuted\"");
      statement.execute("insert into sales (id, name, amount, created_at, enabled, payload) values "
          + "(1, 'alpha', 12.30, timestamp '2026-07-04 10:00:00', true, '{\"tier\":\"gold\"}')");
    }
    service = createService(JDBC_URL, "org.h2.Driver");
  }

  @Test
  void inferSchema_mapsStandardTimezoneTypesToDatetime() {
    var result = service.inferSchema(APP_NAME, request(
        "select cast(null as time with time zone) as zoned_time, "
            + "cast(null as timestamp with time zone) as zoned_timestamp", Map.of()));
    assertEquals(List.of("datetime", "datetime"), result.getFields().stream().map(DatasetFieldDefinition::getDataType).toList());
    assertTrue(result.getWarnings().isEmpty());
  }

  @Test
  void inferSchema_shouldReadJdbcMetadataWithoutReturningBusinessRows() {
    DatasetInferSchemaRequest request = request(
        "select id, name, amount, created_at, enabled from sales where id = ${id}",
        Collections.<String, Object>singletonMap("id", Long.valueOf(1))
    );

    DatasetInferSchemaResult result = service.inferSchema(APP_NAME, request);

    assertEquals(5, result.getFields().size());
    assertEquals("id", result.getFields().get(0).getName());
    assertEquals("integer", result.getFields().get(0).getDataType());
    assertEquals("string", result.getFields().get(1).getDataType());
    assertEquals("number", result.getFields().get(2).getDataType());
    assertEquals("datetime", result.getFields().get(3).getDataType());
    assertEquals("boolean", result.getFields().get(4).getDataType());
    for (DatasetFieldDefinition field : result.getFields()) {
      assertEquals(field.getName(), field.getTitle());
      assertNull(field.getSemanticRole());
    }
    assertTrue(result.getWarnings().isEmpty());
  }

  @Test
  void inferSchema_shouldNotExecuteBusinessExpressionsWhileInferringMetadata() {
    DatasetInferSchemaRequest request = request(
        "select id, FAIL_IF_EXECUTED() as should_not_execute from sales where id = ${id}",
        Collections.<String, Object>singletonMap("id", Long.valueOf(1))
    );

    DatasetInferSchemaResult result = service.inferSchema(APP_NAME, request);

    assertEquals(2, result.getFields().size());
    assertEquals("id", result.getFields().get(0).getName());
    assertEquals("should_not_execute", result.getFields().get(1).getName());
  }

  @Test
  void inferSchema_shouldWarnWhenJdbcTypeFallsBackToString() {
    DatasetInferSchemaResult result = service.inferSchema(
        APP_NAME,
        request("select payload from sales", Collections.<String, Object>emptyMap())
    );

    assertEquals(1, result.getFields().size());
    assertEquals("payload", result.getFields().get(0).getName());
    assertEquals("string", result.getFields().get(0).getDataType());
    assertEquals(1, result.getWarnings().size());
    assertTrue(result.getWarnings().get(0).contains("fallback to string"));
  }

  @Test
  void inferSchema_shouldUseSelectedDialectSqlInsteadOfGenericSql() {
    DatasetInferSchemaRequest request = request(
        "select missing_column from missing_table",
        Collections.<String, Object>emptyMap()
    );
    SqlDialectDefinition dialect = new SqlDialectDefinition();
    dialect.setDialectName("h2");
    dialect.setSql("select id from sales");
    request.setDialectName("H2");
    request.setDialects(Collections.singletonList(dialect));

    DatasetInferSchemaResult result = service.inferSchema(APP_NAME, request);

    assertEquals(1, result.getFields().size());
    assertEquals("id", result.getFields().get(0).getName());
  }

  @Test
  void inferSchema_shouldUseParameterDefaultWhenNoDevOverrideExists() {
    DatasetInferSchemaRequest request = request(
        "select id from sales where id = ${marker}",
        Collections.<String, Object>emptyMap()
    );
    ParameterDefinition parameter = new ParameterDefinition();
    parameter.setName("marker");
    parameter.setDefaultValue(Long.valueOf(42));
    request.setParameterDefinitions(Collections.singletonList(parameter));

    DatasetInferSchemaResult result = service.inferSchema(APP_NAME, request);

    assertEquals("id", result.getFields().get(0).getName());
    assertEquals("integer", result.getFields().get(0).getDataType());
  }

  @Test
  void testConnection_shouldOpenConfiguredDatasource() {
    assertDoesNotThrow(() -> service.testConnection(
        APP_NAME,
        request("select 1", Collections.<String, Object>emptyMap())
    ));
  }

  @Test
  void inferSchema_shouldIgnoreEmptyInlineConnectionAndUseConfiguredDatasource() {
    DatasetInferSchemaRequest request = request(
        "select id from sales",
        Collections.<String, Object>emptyMap()
    );
    request.setTestConnection(new com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection());

    DatasetInferSchemaResult result = service.inferSchema(APP_NAME, request);

    assertEquals("id", result.getFields().get(0).getName());
  }

  @Test
  void testConnection_shouldAllowJdbcAutoLoadedDriver() {
    DatasetInferSchemaRequest request = new DatasetInferSchemaRequest();
    Map<String, Object> jdbcConnection = new LinkedHashMap<String, Object>();
    jdbcConnection.put("url", JDBC_URL);
    jdbcConnection.put("username", "sa");
    jdbcConnection.put("password", "");
    request.setDatasource(DATASOURCE_NAME);

    service = createService(JDBC_URL, "");
    assertDoesNotThrow(() -> service.testConnection(APP_NAME, request));
  }

  @Test
  void testConnection_shouldClassifyMissingDriverAsUnreachable() {
    DatasetInferSchemaRequest request = new DatasetInferSchemaRequest();
    Map<String, Object> jdbcConnection = new LinkedHashMap<String, Object>();
    jdbcConnection.put("driverClassName", "missing.Driver");
    jdbcConnection.put("url", JDBC_URL);
    jdbcConnection.put("username", "sa");
    jdbcConnection.put("password", "");
    request.setDatasource(DATASOURCE_NAME);

    service = createService(JDBC_URL, "missing.Driver");
    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.testConnection(APP_NAME, request)
    );

    assertTrue(error.getMessage().startsWith("datasource-unreachable:"));
  }

  @Test
  void inferSchema_shouldClassifyInvalidSqlAsSqlInvalid() {
    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.inferSchema(APP_NAME, request("select from", Collections.<String, Object>emptyMap()))
    );

    assertTrue(error.getMessage().startsWith("sql-invalid:"));
  }

  @Test
  void inferSchema_shouldClassifyUnreachableDatasourceSeparately() {
    DatasetSchemaInferenceService unreachableService = createService("jdbc:h2:tcp://127.0.0.1:1/missing", "org.h2.Driver");

    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> unreachableService.inferSchema(APP_NAME, request("select 1", Collections.<String, Object>emptyMap()))
    );

    assertTrue(error.getMessage().startsWith("datasource-unreachable:"));
  }

  private DatasetSchemaInferenceService createService(String url, String driver) {
    var sources = org.mockito.Mockito.mock(DatasetDesignDataSourceService.class);
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("type", "jdbc");
    definition.put("url", url);
    definition.put("driverClassName", driver);
    definition.put("username", "sa");
    definition.put("password", "");
    org.mockito.Mockito.when(sources.connection(org.mockito.ArgumentMatchers.eq(APP_NAME), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(new com.ouroboros.dev.datasource.model.DataSourceDefine(definition)));
    return new DefaultDatasetSchemaInferenceService(sources);
  }

  private DatasetInferSchemaRequest request(String sql, Map<String, Object> parameters) {
    DatasetInferSchemaRequest request = new DatasetInferSchemaRequest();
    request.setDatasource(DATASOURCE_NAME);
    request.setSql(sql);
    request.setParameters(parameters);
    return request;
  }

  public static Integer failIfExecuted() {
    throw new AssertionError("infer-schema must not execute business SQL expressions");
  }

  private static class StubAppDataSourceService implements AppDataSourceService {
    @Override
    public Try<AppDataSourceModel> add(AppDataSourceModel model) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> delete(String primaryKey) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> update(String primaryKey, AppDataSourceModel model) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Optional<AppDataSourceModel> getByPrimaryKey(String primaryKey) {
      return Optional.empty();
    }

    @Override
    public List<AppDataSourceModel> getByAppName(String appName) {
      return Collections.emptyList();
    }

    @Override
    public Optional<AppDataSourceModel> getByAppNameAndAppDataSourceName(String appName, String appDataSourceName) {
      if (!APP_NAME.equals(appName) || !DATASOURCE_NAME.equals(appDataSourceName)) {
        return Optional.empty();
      }
      AppDataSourceModel model = new AppDataSourceModel();
      model.setAppName(APP_NAME);
      model.setDataStationName(DATASOURCE_NAME);
      model.setPlatformDataSourceName(PLATFORM_DATASOURCE_NAME);
      return Optional.of(model);
    }

    @Override
    public Long count(Map<String, Object> where) {
      return Long.valueOf(0L);
    }

    @Override
    public List<AppDataSourceModel> query(Map<String, Object> statement) {
      return Collections.emptyList();
    }

    @Override
    public List<AppDataSourceModel> getAll() {
      return Collections.emptyList();
    }

    @Override
    public List<TableInfo> getTableInfo(String appName, String dataStationName, String envName) {
      return Collections.emptyList();
    }
  }

  private static class StubPlatformDataSourceService implements PlatformDataSourceService {
    private final String jdbcUrl;

    private StubPlatformDataSourceService(String jdbcUrl) {
      this.jdbcUrl = jdbcUrl;
    }

    @Override
    public Try<PlatformDataSourceModel> add(PlatformDataSourceModel model) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> delete(String primaryKey) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> update(String primaryKey, PlatformDataSourceModel model) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Optional<PlatformDataSourceModel> getByPrimaryKey(String primaryKey) {
      return Optional.empty();
    }

    @Override
    public Optional<PlatformDataSourceModel> getByName(String name) {
      if (!PLATFORM_DATASOURCE_NAME.equals(name)) {
        return Optional.empty();
      }
      PlatformDataSourceModel model = new PlatformDataSourceModel();
      model.setName(PLATFORM_DATASOURCE_NAME);
      Map<String, Object> definition = new LinkedHashMap<String, Object>();
      definition.put("type", "jdbc");
      definition.put("driverClassName", "org.h2.Driver");
      definition.put("url", jdbcUrl);
      definition.put("username", "sa");
      definition.put("password", "");
      model.setDefinition(Collections.singletonMap("default", definition));
      return Optional.of(model);
    }

    @Override
    public List<PlatformDataSourceModel> getByNames(List<String> names) {
      return Collections.emptyList();
    }

    @Override
    public Long count(Map<String, Object> where) {
      return Long.valueOf(0L);
    }

    @Override
    public List<PlatformDataSourceModel> query(Map<String, Object> statement) {
      return Collections.emptyList();
    }

    @Override
    public List<PlatformDataSourceModel> getAll() {
      return Collections.emptyList();
    }

    @Override
    public List<TableInfo> getTables(String platformDataSourceName, String envName) {
      return Collections.emptyList();
    }
  }
}
