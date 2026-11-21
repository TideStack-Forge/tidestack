package com.ouroboros.statistics.reporting.dev.service;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.ouroboros.dev.datasource.model.PlatformDataSourceModel;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.datasource.service.impl.JDBCDataSourceMetadataService;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshotProvider;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.statistics.reporting.dev.model.DatasetDataSourceRequest;
import com.ouroboros.statistics.reporting.dev.model.DatasetInferSchemaRequest;
import com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection;
import com.ouroboros.statistics.reporting.dev.service.impl.DefaultDatasetSchemaInferenceService;
import com.ouroboros.web.SelectOption;
import com.ouroboros.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetDesignDataSourceServiceTest {
  private final AppDataSourceService bindings = mock(AppDataSourceService.class);
  private final PlatformDataSourceService platforms = mock(PlatformDataSourceService.class);
  private final ApplicationMetadataSnapshotProvider metadata = mock(ApplicationMetadataSnapshotProvider.class);
  private final RuntimeStateStore runtimes = mock(RuntimeStateStore.class);
  private final com.ouroboros.dev.runtime.service.DevRuntimeService runtimeService = mock(com.ouroboros.dev.runtime.service.DevRuntimeService.class);
  private DatasetDesignDataSourceService sources;

  @BeforeEach
  void setUp() {
    var beans = new StaticListableBeanFactory();
    beans.addBean("runtimes", runtimes);
    beans.addBean("runtimeService", runtimeService);
    sources = new DatasetDesignDataSourceService(bindings, platforms, List.of(metadata),
        List.of(new JDBCDataSourceMetadataService()), beans.getBeanProvider(RuntimeStateStore.class),
        beans.getBeanProvider(com.ouroboros.dev.runtime.service.DevRuntimeService.class));
    when(metadata.getApplicationMetadataSnapshot("crm")).thenReturn(ApplicationMetadataSnapshot.current(Map.of(
        "data-model", List.of(
            model("sales.Order", "default", "order_rows", Map.of("id", "Long", "amount", "Decimal", "customerId", "Long")),
            model("sales.Customer", "default", null, Map.of("id", "Long", "displayName", "String")),
            model("warehouse.Secret", "warehouse", "warehouse_secret", Map.of("id", "Long"))))));
    when(metadata.getApplicationMetadataSnapshot("other")).thenReturn(ApplicationMetadataSnapshot.current(Map.of()));
  }

  @Test
  void defaultAndBoundSourcesRemainSelectableWithoutRequiringMetadata() {
    var binding = new AppDataSourceModel();
    binding.setDataStationName("reporting");
    when(bindings.getByAppName("crm")).thenReturn(List.of(binding));
    assertEquals(List.of("default", "reporting"), sources.options("crm").stream().map(SelectOption::getValue).toList());
    assertEquals("默认数据源", sources.options("crm").getFirst().getLabel());
    assertTrue(sources.connection("crm", request(null)).isEmpty());
  }

  @Test
  void tablesUseCanonicalPhysicalNamesAndStayWithinApplicationAndSource() {
    var catalog = sources.tables("crm", request(null));
    assertEquals(List.of("order_rows", "sales_customer"), catalog.items().stream().map(DatasetDesignDataSourceService.Table::name).toList());
    assertEquals("model", catalog.items().getFirst().source());
    var customer = catalog.items().getLast();
    assertTrue(customer.columns().stream().anyMatch(field -> field.name().equals("display_name")));
    assertFalse(catalog.warnings().isEmpty());
    assertTrue(sources.tables("other", request(null)).items().isEmpty());
    assertEquals(List.of("warehouse_secret"), sources.models("crm", "warehouse").stream()
        .map(com.ouroboros.data.model.DataModel::getRawName).toList());
  }

  @Test
  void nonDefaultSourcesUseTheirBindingAndIgnoreStaleTestConnections() {
    platform("bound", "dev", "jdbc:h2:mem:bound");
    var binding = new AppDataSourceModel();
    binding.setPlatformDataSourceName("bound");
    when(bindings.getByAppNameAndAppDataSourceName("crm", "reporting")).thenReturn(Optional.of(binding));
    var request = request(platformReference("obsolete", "prod"));
    request.setDatasource("reporting");
    assertEquals("jdbc:h2:mem:bound", sources.connection("crm", request).orElseThrow().getProperties().get("url"));
    verify(platforms, never()).getByName("obsolete");
    request.setDatasource("missing");
    assertThrows(BusinessException.class, () -> sources.connection("crm", request));
  }

  @Test
  void offlineInferenceUnderstandsProjectionAliasesJoinsAggregatesAndParameters() {
    var request = new DatasetInferSchemaRequest();
    request.setDatasource("default");
    request.setSql("select c.display_name as customer, count(o.id) as order_count, sum(o.amount) as total "
        + "from order_rows o join sales_customer c on o.customer_id = c.id "
        + "where o.amount >= :minAmount group by c.display_name");
    request.setParameters(Map.of("minAmount", 10));
    var result = new DefaultDatasetSchemaInferenceService(sources).inferSchema("crm", request);
    assertEquals(List.of("customer", "order_count", "total"), result.getFields().stream().map(field -> field.getName()).toList());
    assertEquals(List.of("string", "integer", "number"), result.getFields().stream().map(field -> field.getDataType()).toList());
    assertEquals(1, result.getWarnings().size());
    assertNull(result.getFields().getFirst().getSemanticRole());
  }

  @Test
  void offlineInferenceRejectsUnknownTablesAndUnsupportedDialectFunctions() {
    var service = new DefaultDatasetSchemaInferenceService(sources);
    for (String sql : List.of("select * from warehouse_secret", "select unknown_function(amount) from order_rows")) {
      var request = new DatasetInferSchemaRequest();
      request.setSql(sql);
      assertTrue(assertThrows(BusinessException.class, () -> service.inferSchema("crm", request))
          .getMessage().startsWith("sql-invalid:"));
    }
  }

  @Test
  void staleMetadataCannotMasqueradeAsCurrentSchema() {
    when(metadata.getApplicationMetadataSnapshot("crm")).thenReturn(ApplicationMetadataSnapshot.stale(Map.of()));
    assertEquals("default", sources.options("crm").getFirst().getValue());
    assertTrue(assertThrows(BusinessException.class, () -> sources.models("crm", "default"))
        .getMessage().startsWith("metadata-unavailable:"));
  }

  @Test
  void realConnectionDiscoversAdditionalTablesWithoutReplacingModelTables() throws Exception {
    String url = "jdbc:h2:mem:catalog_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
      statement.execute("create table external_metrics (metric_value decimal(12,2))");
      statement.execute("create table order_rows (id bigint)");
      platform("warehouse", "dev", url);
      var catalog = sources.tables("crm", request(platformReference("warehouse", "dev")));
      var extra = catalog.items().stream().filter(table -> table.name().equals("external_metrics")).findFirst().orElseThrow();
      assertEquals("database", extra.source());
      assertEquals("metric_value", extra.columns().getFirst().name());
      assertEquals(1, catalog.items().stream().filter(table -> table.name().equals("order_rows")).count());
      assertTrue(catalog.items().stream().noneMatch(table -> table.name().equals("users")));
      assertTrue(catalog.warnings().isEmpty());
      var infer = new DatasetInferSchemaRequest();
      infer.setTestConnection(platformReference("warehouse", "dev"));
      infer.setSql("select metric_value as metric from external_metrics");
      assertEquals("number", new DefaultDatasetSchemaInferenceService(sources).inferSchema("crm", infer)
          .getFields().getFirst().getDataType());
    }
  }

  @Test
  void invalidSelectedConnectionFailsInsteadOfSilentlyFallingBack() {
    platform("broken", "dev", "jdbc:h2:tcp://127.0.0.1:1/missing");
    assertTrue(assertThrows(BusinessException.class,
        () -> sources.tables("crm", request(platformReference("broken", "dev"))))
        .getMessage().startsWith("datasource-unreachable:"));
    assertTrue(assertThrows(BusinessException.class,
        () -> sources.connection("crm", request(platformReference("broken", "prod"))))
        .getMessage().startsWith("datasource-not-found:"));
    assertTrue(assertThrows(BusinessException.class,
        () -> sources.connection("crm", request(platformReference("missing", "dev"))))
        .getMessage().startsWith("datasource-not-found:"));
  }

  @Test
  void runtimeSelectionUsesExistingSpecAndRejectsOtherAppsStoppedAndMemoryDatabases() {
    var key = RuntimeIdentityKey.of("crm", "admin", RuntimeKind.TEST_APP, BackendType.DOCKER);
    var instance = RuntimeInstance.builder().appName("crm").profile("admin").runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER).runtimeId("runtime-1").status(RuntimeStatus.RUNNING).build();
    var spec = RuntimeSpec.builder().appName("crm").profile("admin").runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER).env(Map.of("SPRING_DATASOURCE_URL", "jdbc:mysql://db/crm_admin",
            "SPRING_DATASOURCE_DRIVERCLASSNAME", "com.mysql.cj.jdbc.Driver",
            "SPRING_DATASOURCE_USERNAME", "user", "SPRING_DATASOURCE_PASSWORD", "secret")).build();
    when(runtimeService.recover("crm", RuntimeKind.TEST_APP)).thenReturn(io.vavr.control.Try.success(List.of()));
    when(runtimeService.recover("other", RuntimeKind.TEST_APP)).thenReturn(io.vavr.control.Try.success(List.of()));
    when(runtimeService.getStatus(key)).thenAnswer(call -> io.vavr.control.Try.of(() -> runtimes.findInstance(key).orElseThrow()));
    when(runtimes.allInstances()).thenReturn(Map.of(key, instance));
    when(runtimes.findKeyByRuntimeId("runtime-1")).thenReturn(Optional.of(key));
    when(runtimes.findInstance(key)).thenReturn(Optional.of(instance));
    when(runtimes.findSpec(key)).thenReturn(Optional.of(spec));
    assertEquals("runtime-1", sources.runtimeOptions("crm").getFirst().getValue());
    assertTrue(sources.runtimeOptions("other").isEmpty());
    var selection = new DatasetTestConnection();
    selection.setKind("runtime");
    selection.setRuntimeId("runtime-1");
    assertEquals("jdbc:mysql://db/crm_admin", sources.connection("crm", request(selection)).orElseThrow().getProperties().get("url"));
    assertThrows(BusinessException.class, () -> sources.connection("other", request(selection)));
    when(runtimes.findInstance(key)).thenReturn(Optional.of(RuntimeInstance.builder().status(RuntimeStatus.STOPPED).build()));
    assertThrows(BusinessException.class, () -> sources.connection("crm", request(selection)));
    when(runtimes.findInstance(key)).thenReturn(Optional.of(instance));
    when(runtimes.findSpec(key)).thenReturn(Optional.of(RuntimeSpec.builder().env(Map.of("SPRING_DATASOURCE_URL", "jdbc:h2:mem:app")).build()));
    assertTrue(sources.runtimeOptions("crm").isEmpty());
    assertThrows(BusinessException.class, () -> sources.connection("crm", request(selection)));
    verify(runtimes, never()).saveSpec(any(), any());
  }

  private DatasetDataSourceRequest request(DatasetTestConnection selected) {
    var request = new DatasetDataSourceRequest();
    request.setTestConnection(selected);
    return request;
  }

  private DatasetTestConnection platformReference(String name, String env) {
    var reference = new DatasetTestConnection();
    reference.setKind("platform");
    reference.setPlatformDataSourceName(name);
    reference.setEnvName(env);
    return reference;
  }

  private void platform(String name, String env, String url) {
    var platform = new PlatformDataSourceModel();
    platform.setName(name);
    platform.setTitle("测试连接");
    platform.setDefinition(Map.of(env, Map.of("type", "jdbc", "driverClassName", "org.h2.Driver", "url", url, "username", "sa", "password", "")));
    when(platforms.getByName(name)).thenReturn(Optional.of(platform));
    when(platforms.getAll()).thenReturn(List.of(platform));
    assertEquals(name, sources.platformOptions().getFirst().getValue());
  }

  private Metadata model(String fullName, String source, String rawName, Map<String, String> fields) {
    var metadata = new Metadata();
    metadata.setName(fullName);
    var details = new java.util.LinkedHashMap<String, Object>();
    details.put("namespace", fullName.substring(0, fullName.lastIndexOf('.')));
    details.put("name", fullName.substring(fullName.lastIndexOf('.') + 1));
    details.put("dataStation", source);
    details.put("rawName", rawName);
    details.put("primaryKeys", List.of("id"));
    details.put("fields", fields.entrySet().stream().map(field -> Map.of("name", field.getKey(), "type", field.getValue())).toList());
    metadata.setDetails(details);
    return metadata;
  }
}
