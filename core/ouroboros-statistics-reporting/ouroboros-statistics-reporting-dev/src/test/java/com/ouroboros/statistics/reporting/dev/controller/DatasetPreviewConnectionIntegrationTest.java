package com.ouroboros.statistics.reporting.dev.controller;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.ouroboros.dev.datasource.model.PlatformDataSourceModel;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.service.*;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.statistics.reporting.runtime.execution.*;
import com.ouroboros.statistics.reporting.runtime.executor.SqlDatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.projector.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetPreviewConnectionIntegrationTest {
  @TempDir
  java.nio.file.Path workspace;

  @Test
  void previewUsesPersistedPlatformConnectionAndCurrentRequestOverridesTheSavedConnection() throws Exception {
    String url = "jdbc:h2:mem:preview_" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE";
    try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
      statement.execute("create table preview_rows (id integer)");
      statement.execute("insert into preview_rows values (42)");
      var platforms = mock(PlatformDataSourceService.class);
      var platform = new PlatformDataSourceModel();
      platform.setDefinition(Map.of("dev", Map.of("type", "jdbc", "driverClassName", "org.h2.Driver", "url", url, "username", "sa", "password", "")));
      when(platforms.getByName("saved-connection")).thenReturn(Optional.of(platform));
      var sources = sources(platforms, mock(RuntimeStateStore.class));
      var selected = new DatasetTestConnection();
      selected.setKind("platform");
      selected.setPlatformDataSourceName("saved-connection");
      selected.setEnvName("dev");
      var fixture = new DatasetDevConfigModel();
      fixture.setTestConnection(selected);
      var fixtures = mock(DatasetDevConfigService.class, CALLS_REAL_METHODS);
      when(fixtures.findByDataset("crm", "sales.orders")).thenReturn(Optional.of(fixture));
      var controller = controller(sources, fixtures);
      var request = request();
      var result = controller.preview("crm", request);
      assertEquals(0, result.getCode(), result.getMsg());
      assertEquals(42, ((Number) result.getData().getRows().getFirst().get("id")).intValue());
      // Clearing the selection means resolve the app binding, never the platform's Spring datasource.
      var application = new DatasetTestConnection();
      application.setKind("application");
      request.setTestConnection(application);
      var missing = assertThrows(com.ouroboros.exception.BusinessException.class, () -> controller.preview("crm", request));
      assertTrue(missing.getMessage().contains("datasource-required"));
      verify(platforms, times(1)).getByName("saved-connection");
    }
  }

  @Test
  void nonDefaultPreviewUsesTheBoundDatabaseDespiteOldFixtureAndContextOverrides() throws Exception {
    String url = "jdbc:h2:mem:bound_preview_" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE";
    try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
      statement.execute("create table preview_rows (id integer)");
      statement.execute("insert into preview_rows values (99)");
      var platforms = mock(PlatformDataSourceService.class);
      var platform = new PlatformDataSourceModel();
      platform.setDefinition(Map.of("dev", Map.of("type", "jdbc", "driverClassName", "org.h2.Driver", "url", url, "username", "sa", "password", "")));
      when(platforms.getByName("bound")).thenReturn(Optional.of(platform));
      var bindings = mock(AppDataSourceService.class);
      var binding = new AppDataSourceModel();
      binding.setPlatformDataSourceName("bound");
      when(bindings.getByAppNameAndAppDataSourceName("crm", "reporting")).thenReturn(Optional.of(binding));
      var selected = new DatasetTestConnection();
      selected.setKind("platform");
      selected.setPlatformDataSourceName("obsolete");
      var fixture = new DatasetDevConfigModel();
      fixture.setTestConnection(selected);
      fixture.setDatasource("obsolete-source");
      var fixtures = mock(DatasetDevConfigService.class, CALLS_REAL_METHODS);
      when(fixtures.findByDataset("crm", "sales.orders")).thenReturn(Optional.of(fixture));
      var request = request();
      ((SqlQueryDefinition) request.getDataset().getQueryDefinition()).setDatasource("reporting");
      var result = controller(sources(platforms, mock(RuntimeStateStore.class), bindings), fixtures).preview("crm", request);
      assertEquals(0, result.getCode(), result.getMsg());
      assertEquals(99, ((Number) result.getData().getRows().getFirst().get("id")).intValue());
      verify(platforms, never()).getByName("obsolete");
    }
  }

  @Test
  void previewUsesTheSelectedRunningContainerDatabaseAndRejectsAnotherApplicationsRuntime() throws Exception {
    String url = "jdbc:h2:file:" + workspace.resolve("runtime-db") + ";DATABASE_TO_LOWER=TRUE";
    try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
      statement.execute("create table preview_rows (id integer)");
      statement.execute("insert into preview_rows values (84)");
      var runtimes = mock(RuntimeStateStore.class);
      var key = RuntimeIdentityKey.of("crm", "admin", RuntimeKind.TEST_APP, BackendType.DOCKER);
      when(runtimes.findKeyByRuntimeId("runtime-1")).thenReturn(Optional.of(key));
      when(runtimes.findInstance(key)).thenReturn(Optional.of(RuntimeInstance.builder().status(RuntimeStatus.RUNNING).build()));
      when(runtimes.findSpec(key)).thenReturn(Optional.of(RuntimeSpec.builder().env(Map.of(
          "SPRING_DATASOURCE_URL", url, "SPRING_DATASOURCE_DRIVERCLASSNAME", "org.h2.Driver",
          "SPRING_DATASOURCE_USERNAME", "sa", "SPRING_DATASOURCE_PASSWORD", "")).build()));
      var controller = controller(sources(mock(PlatformDataSourceService.class), runtimes), mock(DatasetDevConfigService.class, CALLS_REAL_METHODS));
      var request = request();
      var selected = new DatasetTestConnection();
      selected.setKind("runtime");
      selected.setRuntimeId("runtime-1");
      request.setTestConnection(selected);
      var result = controller.preview("crm", request);
      assertEquals(0, result.getCode(), result.getMsg());
      assertEquals(84, ((Number) result.getData().getRows().getFirst().get("id")).intValue());
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> controller.preview("other", request));
      verify(runtimes, never()).saveSpec(any(), any());
    }
  }

  private DatasetPreviewRequest request() {
    var dataset = StatisticsReportingDevTestSupport.minimalDataset("sales.orders", false);
    ((SqlQueryDefinition) dataset.getQueryDefinition()).setSql("select id from preview_rows");
    dataset.setParameters(List.of());
    dataset.getSchema().setFields(List.of(dataset.getSchema().getFields().getFirst()));
    var request = new DatasetPreviewRequest();
    request.setDataset(dataset);
    return request;
  }

  private DatasetDesignDataSourceService sources(PlatformDataSourceService platforms, RuntimeStateStore runtimes) {
    return sources(platforms, runtimes, mock(AppDataSourceService.class));
  }

  private DatasetDesignDataSourceService sources(PlatformDataSourceService platforms, RuntimeStateStore runtimes,
                                                 AppDataSourceService bindings) {
    var beans = new StaticListableBeanFactory();
    beans.addBean("runtimes", runtimes);
    var runtimeService = mock(com.ouroboros.dev.runtime.service.DevRuntimeService.class);
    when(runtimeService.getStatus(any())).thenAnswer(call -> io.vavr.control.Try.of(() -> runtimes.findInstance(call.getArgument(0)).orElseThrow()));
    beans.addBean("runtimeService", runtimeService);
    return new DatasetDesignDataSourceService(bindings, platforms, List.of(), List.of(),
        beans.getBeanProvider(RuntimeStateStore.class),
        beans.getBeanProvider(com.ouroboros.dev.runtime.service.DevRuntimeService.class));
  }

  private StatisticsReportingDevController controller(DatasetDesignDataSourceService sources, DatasetDevConfigService fixtures) {
    var variants = new DefaultSqlDialectResolver();
    var backend = new JdbcSqlExecutionBackend();
    var platformExecutor = new SqlDatasetExecutor(variants, (query, request) -> {
      throw new AssertionError("preview must not use the platform's Spring connection");
    }, backend);
    var controller = new StatisticsReportingDevController(app -> workspace.resolve(app).toString(), List.of(platformExecutor),
        new DefaultDatasetParameterNormalizer(), new DefaultDatasetPermissionInjector(),
        (app, request) -> { throw new UnsupportedOperationException(); }, fixtures);
    controller.setPreviewExecutorFactory(new DatasetPreviewExecutorFactory(sources, variants, backend));
    return controller;
  }
}
