package com.ouroboros.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.configuration.center.ConfigurationGroupMetadataCenter;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.ConfigurationModel;
import com.ouroboros.configuration.service.impl.ConfigurationWorkbenchServiceImpl;
import com.ouroboros.configuration.util.ConfigurationEditorSchemaBuilder;
import com.ouroboros.util.Json;

class ConfigurationWorkbenchManagementEntryTest {
  @Test
  void dedicatedManagementRemovesTheItemFromWorkbenchFormsAndCounts() {
    var dedicated = configuration("clients", "security.auth", "/system-setting/auth-clients");
    var ordinary = configuration("passwordLogin", "security.auth", null);
    var configurations = Map.of(dedicated.getKey(), dedicated, ordinary.getKey(), ordinary);
    var runtime = mock(RuntimeConfigurationService.class);
    when(runtime.getManagedConfigurationModels()).thenReturn(configurations);
    var workbench = workbench(runtime);

    try (var groups = mockStatic(ConfigurationGroupMetadataCenter.class)) {
      groups.when(ConfigurationGroupMetadataCenter::getConfigurationGroupMetadataMap).thenReturn(Map.of());

      var panel = workbench.getGroupPanel("security.auth").orElseThrow();
      assertThat(panel.getItems()).extracting(item -> item.getKey()).containsExactly("passwordLogin");
      assertThat(panel.getStatistics().getTotalItems()).isEqualTo(1);
      var root = workbench.getGroupPanel("").orElseThrow();
      assertThat(root.getStatistics().getTotalItems()).isEqualTo(1);
      assertThat(root.getChildGroups().getFirst().getChildGroups().getFirst().getItems())
          .extracting(item -> item.getKey()).containsExactly("passwordLogin");
      assertThat(workbench.getItemPanel("clients")).isEmpty();
      assertThat(workbench.getItemPanel("passwordLogin").orElseThrow().getEditorSchema().getType())
          .isEqualTo("input-text");
      assertThat(runtime.getManagedConfigurationModels().get("clients").getValue()).isEqualTo("configured");
    }
  }

  @Test
  void dedicatedUngroupedItemsDoNotCreateAnEmptyOtherSettingsGroup() {
    var dedicated = configuration("clients", null, "/system-setting/auth-clients");
    var runtime = mock(RuntimeConfigurationService.class);
    when(runtime.getManagedConfigurationModels()).thenReturn(Map.of("clients", dedicated));
    var workbench = workbench(runtime);

    try (var groups = mockStatic(ConfigurationGroupMetadataCenter.class)) {
      groups.when(ConfigurationGroupMetadataCenter::getConfigurationGroupMetadataMap).thenReturn(Map.of());
      assertThat(workbench.getTree()).isEmpty();
      assertThat(workbench.getGroupPanel("").orElseThrow().getStatistics().getTotalItems()).isZero();
    }
  }

  private ConfigurationWorkbenchServiceImpl workbench(RuntimeConfigurationService runtime) {
    var workbench = new ConfigurationWorkbenchServiceImpl();
    ReflectionTestUtils.setField(workbench, "runtimeConfigurationService", runtime);
    ReflectionTestUtils.setField(workbench, "schemaBuilder", new ConfigurationEditorSchemaBuilder());
    return workbench;
  }

  private ConfigurationModel configuration(String key, String groupPath, String managementEntry) {
    var definition = new java.util.LinkedHashMap<String, Object>();
    definition.put("key", key);
    definition.put("valueType", "string");
    if (groupPath != null) { definition.put("groupPath", groupPath); }
    if (managementEntry != null) { definition.put("managementEntry", managementEntry); }
    var metadata = Json.toBean(ConfigurationMetadata.class, Json.toJsonString(definition));
    return new ConfigurationModel(key, "configured", metadata, null, "1");
  }
}
