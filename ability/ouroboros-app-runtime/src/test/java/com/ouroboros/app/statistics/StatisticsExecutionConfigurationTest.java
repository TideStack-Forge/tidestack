package com.ouroboros.app.statistics;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionPolicy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsExecutionConfigurationTest {
  @Test
  void readsOneSnapshotPerExecutionAndUsesTheChangedRevision() {
    var service = mock(ConfigurationService.class);
    when(service.getSnapshot(DatasetExecutionPolicy.KEY)).thenReturn(
        Optional.of(new ConfigurationSnapshot(DatasetExecutionPolicy.KEY, Map.of("queryTimeoutSeconds", 10,
            "maxPageSize", 50, "maxExportRows", 100), null, "r1", null)),
        Optional.of(new ConfigurationSnapshot(DatasetExecutionPolicy.KEY, Map.of("queryTimeoutSeconds", 20,
            "maxPageSize", 100, "maxExportRows", 200), null, "r2", null)));
    assertEquals(50, StatisticsExecutionConfiguration.read(service).maxPageSize());
    assertEquals("r2", StatisticsExecutionConfiguration.read(service).revision());
    verify(service, times(2)).getSnapshot(DatasetExecutionPolicy.KEY);
    verifyNoMoreInteractions(service);
  }

  @Test
  void validatesPublishedDefinitionsBeforeConfigurationStorageIsReady() {
    var service = mock(ConfigurationService.class);
    when(service.getSnapshot(anyString())).thenThrow(new IllegalStateException("typed model not ready"));
    try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      context.register(com.ouroboros.common.app.util.SpringContextUtil.class);
      context.getBeanFactory().registerSingleton("configurationService", service);
      context.refresh();
      var source = Map.of("queryDefinition", Map.of("type", "sql", "sql", "select 1 as id"),
          "schema", Map.of("fields", java.util.List.of(Map.of("name", "id", "dataType", "integer", "semanticRole", "dimension"))),
          "requiredContext", java.util.List.of("currentUser"), "executionConstraints", Map.of("maxPageSize", 10));
      var published = com.ouroboros.statistics.reporting.definition.EmbeddedQueryDefinition.resolve(source, "demo.view");
      assertEquals("demo.view.private-query", published.getName());
      assertEquals(10, published.getExecutionConstraints().get("maxPageSize"));
      verifyNoInteractions(service);
      assertThrows(IllegalStateException.class, () ->
          com.ouroboros.statistics.reporting.definition.EmbeddedQueryDefinition.resolve(source, "demo.view", true));
    }
  }

  @Test
  void contributesDefaultsAndRejectsInvalidUpdates() {
    var provider = new StatisticsExecutionConfiguration();
    var metadata = provider.getAllConfigurationMetadata().getFirst();
    assertEquals(DatasetExecutionPolicy.KEY, metadata.getKey());
    var group = provider.getAllConfigurationGroupMetadata().getFirst();
    assertEquals(metadata.getGroupPath(), group.getPath());
    assertEquals("统计分析", group.getTitle());
    var validator = new StatisticsExecutionPolicyValidator();
    assertTrue(validator.supports(metadata.getKey()));
    assertFalse(validator.supports("other"));
    validator.validate(metadata.getDefaultValue());
    assertThrows(BusinessException.class, () -> validator.validate(Map.of("queryTimeoutSeconds", 0)));
    var service = mock(ConfigurationService.class);
    when(service.getSnapshot(DatasetExecutionPolicy.KEY)).thenReturn(Optional.empty());
    assertEquals(1000, StatisticsExecutionConfiguration.read(service).maxPageSize());
  }
}
