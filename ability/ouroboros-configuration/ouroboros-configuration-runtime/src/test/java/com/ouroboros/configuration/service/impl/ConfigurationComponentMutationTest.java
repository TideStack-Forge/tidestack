package com.ouroboros.configuration.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.center.ConfigurationMetadataCenter;
import com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.management.ConfigurationPreparedWrite;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.configuration.service.ConfigurationValueService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

class ConfigurationComponentMutationTest {
  private static final String TARGET = "security.test.clients";
  private static final String DEPENDENCY = "security.test.registration";
  private final RuntimeConfigurationServiceImpl service = new RuntimeConfigurationServiceImpl();
  private final ConfigurationValueService values = mock(ConfigurationValueService.class);
  private Map<String, ConfigurationMetadata> previous;
  private RuntimeConfigurationValueModel saved;

  @BeforeEach
  void setUp() {
    previous = ConfigurationMetadataCenter.getConfigurationMetadataMap();
    var metadata = new LinkedHashMap<String, ConfigurationMetadata>();
    metadata.put(TARGET, metadata(TARGET, ConfigValueType.ARRAY, "[]", true));
    metadata.put(DEPENDENCY, metadata(DEPENDENCY, ConfigValueType.OBJECT, "{\"enabled\":false}", true));
    metadata.put("ordinary", metadata("ordinary", ConfigValueType.STRING, "old", false));
    ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", metadata);
    ReflectionTestUtils.setField(service, "self", service);
    ReflectionTestUtils.setField(service, "valueService", values);
    ReflectionTestUtils.setField(service, "applicationContext", mock(ApplicationContext.class));
    ReflectionTestUtils.setField(service, "environment", new MockEnvironment());
    ReflectionTestUtils.setField(service, "managementPolicies", List.of(new ComponentPolicy()));
    when(values.getForUpdate(TARGET)).thenReturn(row(TARGET, "[\"existing\"]", 4));
    when(values.getForUpdate(DEPENDENCY)).thenReturn(row(DEPENDENCY, "{\"enabled\":true}", 7));
    when(values.update(any(), any(), any())).thenAnswer(invocation -> {
      saved = invocation.getArgument(0);
      saved.setRevision(5L);
      return true;
    });
  }

  @AfterEach
  void restore() {
    ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", previous);
  }

  @Test
  void componentQualificationWritesWithItsOwnAuditActorWithoutGrantingManagementRead() {
    var result = service.mutateManagementConfigurations(Map.of(TARGET, new Append("registered")), Map.of(TARGET, "4"));

    assertThat(result).isEmpty();
    assertThat(saved.getUpdateBy()).isEqualTo("component:test:request");
    assertThat(Json.toList(saved.getValue())).containsExactly("existing", "registered");
  }

  @Test
  void matchingDependencyRevisionGuardsTheWriteWithoutChangingTheDependency() {
    var result = service.mutateManagementConfigurations(Map.of(TARGET, new Append("registered")),
        Map.of(TARGET, "4", DEPENDENCY, "7"));

    assertThat(result).isEmpty();
    assertThat(saved.getKey()).isEqualTo(TARGET);
    assertThat(Json.toList(saved.getValue())).containsExactly("existing", "registered");
    verify(values, org.mockito.Mockito.times(1)).update(any(), any(), any());
  }

  @Test
  void staleDependencyRevisionRejectsTheMutationBeforePersistence() {
    assertThatThrownBy(() -> service.mutateManagementConfigurations(Map.of(TARGET, new Append("registered")),
        Map.of(TARGET, "4", DEPENDENCY, "6")))
        .hasMessageContaining("CONFIGURATION_REVISION_CONFLICT");
    assertThat(saved).isNull();
    verify(values, never()).update(any(), any(), any());
  }

  @Test
  void unrelatedRevisionCannotBeSubmittedAsACommandDependency() {
    assertThatThrownBy(() -> service.mutateManagementConfigurations(Map.of(TARGET, new Append("registered")),
        Map.of(TARGET, "4", "ordinary", "1")))
        .hasMessageContaining("CONFIGURATION_REVISION_REQUIRED");
    assertThat(saved).isNull();
    verify(values, never()).update(any(), any(), any());
  }

  @Test
  void dependencyRevisionDoesNotReplaceTheRequiredTargetRevisionAndCannotBeBlank() {
    for (var revisions : List.of(Map.of(DEPENDENCY, "7"), Map.of(TARGET, "4", DEPENDENCY, ""))) {
      assertThatThrownBy(() -> service.mutateManagementConfigurations(Map.of(TARGET, new Append("registered")), revisions))
          .hasMessageContaining("CONFIGURATION_REVISION_REQUIRED");
    }
    assertThat(saved).isNull();
    verify(values, never()).update(any(), any(), any());
  }

  @Test
  void authoritativeDependencyRejectsARequestWhenRegistrationHasBeenClosed() {
    when(values.getForUpdate(DEPENDENCY)).thenReturn(row(DEPENDENCY, "{\"enabled\":false}", 8));

    assertThatThrownBy(() -> service.mutateManagementConfigurations(
        Map.of(TARGET, new Append("registered")), Map.of(TARGET, "4")))
        .hasMessageContaining("CONFIGURATION_ACCESS_DENIED");
    assertThat(saved).isNull();
  }

  @Test
  void absenceOfADependencyRowDoesNotTreatAnEnabledDefaultAsAPersistedQualification() {
    when(values.getForUpdate(DEPENDENCY)).thenReturn(null);

    assertThatThrownBy(() -> service.mutateManagementConfigurations(
        Map.of(TARGET, new Append("registered")), Map.of(TARGET, "4")))
        .hasMessageContaining("CONFIGURATION_ACCESS_DENIED");
    assertThat(saved).isNull();
  }

  @Test
  void anUnidentifiedCallerCannotReplaceRestoreOrMixUnprotectedConfiguration() {
    for (ConfigurationMutation mutation : List.of(new ConfigurationMutation.Replace(List.of()),
        ConfigurationMutation.RestoreDefaults.INSTANCE)) {
      assertThatThrownBy(() -> service.mutateManagementConfigurations(Map.of(TARGET, mutation), Map.of(TARGET, "4")))
          .hasMessageContaining("CONFIGURATION_ACCESS_DENIED");
    }
    assertThatThrownBy(() -> service.mutateManagementConfigurations(
        Map.of(TARGET, new Append("registered"), "ordinary", new ConfigurationMutation.Replace("changed")),
        Map.of(TARGET, "4", "ordinary", "1"))).hasMessageContaining("CONFIGURATION_ACCESS_DENIED");
    verify(values, never()).update(any(), any(), any());
  }

  private static ConfigurationMetadata metadata(String key, ConfigValueType type, String defaults, boolean protectedValue) {
    var metadata = new ConfigurationMetadata();
    metadata.setKey(key);
    metadata.setValueType(type);
    metadata.setDefaultValue(defaults);
    metadata.setModifiable(true);
    if (protectedValue) { metadata.setExtraProp("managementAccessPolicyRequired", true); }
    return metadata;
  }

  private static RuntimeConfigurationValueModel row(String key, String value, long revision) {
    var row = new RuntimeConfigurationValueModel();
    row.setKey(key);
    row.setValue(value);
    row.setRevision(revision);
    return row;
  }

  private record Append(String name) implements ConfigurationMutation { }

  private static class ComponentPolicy implements ConfigurationManagementAccessPolicy {
    public boolean supports(String key) { return TARGET.equals(key); }
    public Set<String> writeDependencies(String key, ConfigurationMutation mutation) { return Set.of(DEPENDENCY); }
    public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
      return Optional.empty();
    }
    public ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context, ConfigurationSnapshot old,
        ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies) {
      var registration = dependencies.get(DEPENDENCY);
      if (!(mutation instanceof Append append) || registration.storedRawValue() == null
          || !Boolean.TRUE.equals(Json.toMap(Json.toJsonString(registration.value())).get("enabled"))) {
        throw new BusinessException(403, "[CONFIGURATION_ACCESS_DENIED] Registration is unavailable");
      }
      var candidate = Json.toList(Json.toJsonString(old.value()));
      candidate.add(append.name());
      return new ConfigurationPreparedWrite(candidate, "component:test:request");
    }
  }
}
