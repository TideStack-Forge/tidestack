package com.ouroboros.configuration.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.configuration.service.ConfigurationValueService;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.util.Json;

class ConfigurationManagementSecurityTest {
  private static final String KEY = "security.test.clients";
  private final RuntimeConfigurationServiceImpl service = new RuntimeConfigurationServiceImpl();
  private final ConfigurationValueService values = mock(ConfigurationValueService.class);
  private final SecurityIdentity identity = mock(SecurityIdentity.class);
  private ConfigurationMetadata metadata;
  private Map<String, ConfigurationMetadata> previousMetadata;

  @BeforeEach
  void setUp() {
    previousMetadata = ConfigurationMetadataCenter.getConfigurationMetadataMap();
    metadata = metadata(KEY, ConfigValueType.ARRAY, "[{\"clientId\":\"a\",\"secret\":\"default-secret\"}]");
    metadata.setExtraProp("managementAccessPolicyRequired", true);
    metadata.setExtraProp("sensitiveSourceNames", List.of("SIGNING_KEY"));
    install(metadata);
    ReflectionTestUtils.setField(service, "self", service);
    ReflectionTestUtils.setField(service, "valueService", values);
    ReflectionTestUtils.setField(service, "environment", new MockEnvironment().withProperty("SIGNING_KEY", "private"));
    ReflectionTestUtils.setField(service, "applicationContext", mock(ApplicationContext.class));
    ReflectionTestUtils.setField(service, "managementPolicies", List.of(new TestPolicy()));
    when(identity.getUserIdentity()).thenReturn("operator");
    when(identity.getCredentialContext()).thenReturn(com.ouroboros.security.identity.CredentialContext.platform());
    when(values.getAllConfigurationValue()).thenReturn(List.of());
  }

  @AfterEach
  void restoreMetadata() {
    ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", previousMetadata);
  }

  @Test
  void anonymousCannotReadProtectedKeyButInternalComponentsCan() {
    assertThat(service.getManagedConfigurationModel(KEY)).isEmpty();
    assertThat(service.getManagedConfigurationModels()).isEmpty();
    assertThat(service.getSnapshot(KEY).orElseThrow().value()).isInstanceOf(List.class);
  }

  @Test
  void allReadRepresentationsAreProjectedWithoutMutatingInternalModel() {
    RuntimeConfigurationValueModel stored = row("[{\"clientId\":\"a\",\"secret\":\"stored-secret\"}]", 3L);
    when(values.getAllConfigurationValue()).thenAnswer(invocation -> List.of(stored));
    var projected = CurrentSecurityContextHolder.withIdentity(identity,
        () -> service.getManagedConfigurationModel(KEY).orElseThrow());

    assertThat(Json.toJsonString(projected)).doesNotContain("stored-secret", "default-secret", "\"secret\"");
    assertThat(projected.getRevision()).isEqualTo("3");
    assertThat(Json.toJsonString(service.getAllConfigurationModel().get(KEY))).contains("stored-secret", "default-secret");
  }

  @Test
  void missingOrDuplicatePoliciesFailClosed() {
    ReflectionTestUtils.setField(service, "managementPolicies", List.of());
    assertThatThrownBy(() -> service.getManagedConfigurationModel(KEY)).hasMessageContaining("CONFIGURATION_POLICY_REQUIRED");
    ReflectionTestUtils.setField(service, "managementPolicies", List.of(new TestPolicy(), new TestPolicy()));
    assertThatThrownBy(() -> service.getManagedConfigurationModels()).hasMessageContaining("CONFIGURATION_POLICY_REQUIRED");
  }

  @Test
  void scriptsFrontendAndReferencesCannotForwardProtectedValues() {
    metadata.setScope(ConfigScope.FRONTEND);
    assertThat(service.getValues(ConfigScope.FRONTEND)).isEmpty();
    assertThatThrownBy(() -> service.getForExecution(KEY)).hasMessageContaining("CONFIGURATION_PROTECTED_READ_FORBIDDEN");
    install(metadata, metadata("public.forward", ConfigValueType.STRING, "#{security.test.clients}"));
    assertThatThrownBy(service::getAllConfigurationModel).hasMessageContaining("CONFIGURATION_SENSITIVE_REFERENCE_FORBIDDEN");
    install(metadata, metadata("public.forward", ConfigValueType.STRING, "#{SIGNING_KEY}"));
    assertThatThrownBy(service::getAllConfigurationModel).hasMessageContaining("CONFIGURATION_SENSITIVE_REFERENCE_FORBIDDEN");
    assertThat(service.getEnvironmentProperties()).doesNotContainKey("SIGNING_KEY");
  }

  @Test
  void protectedDefinitionsCannotBeOverridden() {
    assertThatThrownBy(() -> metadata.merge(metadata(KEY, ConfigValueType.STRING, "leaked")))
        .hasMessageContaining("CONFIGURATION_PROTECTED_METADATA");
    assertThatThrownBy(() -> metadata(KEY, ConfigValueType.STRING, "leaked").merge(metadata))
        .hasMessageContaining("CONFIGURATION_PROTECTED_METADATA");
  }

  @Test
  void legacyWriteAndStaleRevisionCannotReachPersistence() {
    assertThatThrownBy(() -> service.update(Map.of(KEY, List.of()), "forged"))
        .hasMessageContaining("CONFIGURATION_ACCESS_DENIED");
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> service.update(Map.of(KEY, List.of()), "forged"))
          .hasMessageContaining("CONFIGURATION_REVISION_REQUIRED");
      when(values.get(KEY)).thenReturn(row("[]", 8L));
      assertThatThrownBy(() -> service.update(Map.of(KEY, List.of()), "forged", Map.of(KEY, "7")))
          .hasMessageContaining("CONFIGURATION_REVISION_CONFLICT");
      return null;
    });
    verify(values, never()).update(any(), any(), any());
  }

  @Test
  void writeMergesCurrentDatabaseRowUsesTrustedActorAndReturnsCommittedProjection() {
    when(values.get(KEY)).thenReturn(row("[{\"clientId\":\"a\",\"secret\":\"database-secret\"}]", 8L));
    when(values.update(any(), eq("8"), any())).thenAnswer(invocation -> {
      RuntimeConfigurationValueModel saved = invocation.getArgument(0);
      assertThat(saved.getUpdateBy()).isEqualTo("operator");
      assertThat(saved.getValue()).contains("database-secret");
      saved.setRevision(9L);
      return true;
    });
    var result = CurrentSecurityContextHolder.withIdentity(identity, () -> service.updateManaged(
        Map.of(KEY, new ConfigurationMutation.Replace("rename")), "forged", Map.of(KEY, "8")));
    assertThat(result.get(KEY).getRevision()).isEqualTo("9");
    assertThat(Json.toJsonString(result)).doesNotContain("database-secret", "default-secret");
  }

  @Test
  void invalidStoredJsonCannotBeReplacedAsAnEmptyList() {
    when(values.get(KEY)).thenReturn(row("broken-json", 8L));
    assertThatThrownBy(() -> service.getSnapshot(KEY)).hasMessageContaining("CONFIGURATION_INVALID_VALUE");
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> service.update(Map.of(KEY, List.of()), "operator", Map.of(KEY, "8")))
          .hasMessageContaining("CONFIGURATION_INVALID_VALUE");
      return null;
    });
    verify(values, never()).update(any(), any(), any());
  }

  @Test
  void reportsCommittedRevisionWhenPostCommitVisibilityFails() {
    when(values.get(KEY)).thenReturn(row("[]", 8L));
    when(values.update(any(), eq("8"), any())).thenAnswer(invocation -> {
      RuntimeConfigurationValueModel saved = invocation.getArgument(0);
      saved.setRevision(9L);
      return true;
    });
    ApplicationContext events = mock(ApplicationContext.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("event unavailable"))
        .when(events).publishEvent(any(Object.class));
    ReflectionTestUtils.setField(service, "applicationContext", events);
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> service.updateManaged(
          Map.of(KEY, new ConfigurationMutation.Replace(List.of())), "operator", Map.of(KEY, "8")))
          .hasMessageContaining("CONFIGURATION_COMMITTED_VISIBILITY_UNCONFIRMED")
          .hasMessageContaining(KEY + "=9");
      return null;
    });
    verify(values).update(any(), eq("8"), any());
  }

  @Test
  void optInRestoreDeletesOverrideIncludingNullDefaultsAndFollowsFutureDefaults() {
    var host = metadata("notification.host", ConfigValueType.STRING, null);
    host.setNullable(true);
    host.setExtraProp("restoreDefaultRemovesOverride", true);
    install(host);
    when(values.removeOverride(eq(host.getKey()), eq("old-token"), any())).thenReturn(true);
    var restored = service.updateManaged(Map.of(host.getKey(), ConfigurationMutation.RestoreDefaults.INSTANCE),
        "operator", Map.of(host.getKey(), "old-token")).get(host.getKey());
    assertThat(restored.getValue()).isNull();
    assertThat(restored.getConfigValue()).isNull();
    assertThat(restored.getRevision()).startsWith("default-");
    verify(values, never()).update(any(), any(), any());
    host.setDefaultValue("new-deployment.invalid");
    assertThat(service.getSnapshot(host.getKey()).orElseThrow().value()).isEqualTo("new-deployment.invalid");
  }

  @Test
  void optInRestoreRequiresRevisionAndRejectsConcurrentChanges() {
    var host = metadata("notification.host", ConfigValueType.STRING, "default.invalid");
    host.setExtraProp("restoreDefaultRemovesOverride", true);
    install(host);
    assertThatThrownBy(() -> service.updateManaged(Map.of(host.getKey(), ConfigurationMutation.RestoreDefaults.INSTANCE),
        "operator", Map.of())).hasMessageContaining("CONFIGURATION_REVISION_REQUIRED");
    assertThatThrownBy(() -> service.updateManaged(Map.of(host.getKey(), ConfigurationMutation.RestoreDefaults.INSTANCE),
        "operator", Map.of(host.getKey(), "stale"))).hasMessageContaining("CONFIGURATION_REVISION_CONFLICT");
    verify(values, never()).update(any(), any(), any());
  }

  @Test
  void protectedRestoreStillUsesTheOwnersRuleUnlessItOptsIntoRemoval() {
    when(values.get(KEY)).thenReturn(row("[]", 8L));
    when(values.update(any(), eq("8"), any())).thenAnswer(invocation -> {
      RuntimeConfigurationValueModel model = invocation.getArgument(0);
      assertThat(model.getValue()).isEqualTo("[]");
      model.setRevision(9L);
      return true;
    });
    CurrentSecurityContextHolder.withIdentity(identity, () -> service.updateManaged(
        Map.of(KEY, ConfigurationMutation.RestoreDefaults.INSTANCE), "operator", Map.of(KEY, "8")));
    verify(values, never()).removeOverride(any(), any(), any());
    metadata.setExtraProp("restoreDefaultRemovesOverride", true);
    assertThatThrownBy(() -> service.updateManaged(Map.of(KEY, ConfigurationMutation.RestoreDefaults.INSTANCE),
        "operator", Map.of(KEY, "8"))).hasMessageContaining("CONFIGURATION_ACCESS_DENIED");
    verify(values, never()).removeOverride(any(), any(), any());
  }

  @Test
  void batchRestoreRemovesEveryOptedInOverrideAndReturnsEveryDefault() {
    var first = metadata("notification.a", ConfigValueType.STRING, null);
    var second = metadata("notification.b", ConfigValueType.STRING, "deployment-default");
    first.setNullable(true);
    first.setExtraProp("restoreDefaultRemovesOverride", true);
    second.setExtraProp("restoreDefaultRemovesOverride", true);
    install(first, second);
    when(values.removeOverride(eq(first.getKey()), eq("first-token"), any())).thenReturn(true);
    when(values.removeOverride(eq(second.getKey()), eq("second-token"), any())).thenReturn(true);

    var result = service.updateManaged(Map.of(
        first.getKey(), ConfigurationMutation.RestoreDefaults.INSTANCE,
        second.getKey(), ConfigurationMutation.RestoreDefaults.INSTANCE), "operator",
        Map.of(first.getKey(), "first-token", second.getKey(), "second-token"));

    assertThat(result).containsOnlyKeys(first.getKey(), second.getKey());
    assertThat(result.get(first.getKey()).getValue()).isNull();
    assertThat(result.get(second.getKey()).getValue()).isEqualTo("deployment-default");
    assertThat(result.values()).allSatisfy(item -> {
      assertThat(item.getConfigValue()).isNull();
      assertThat(item.getRevision()).startsWith("default-");
    });
    verify(values, never()).update(any(), any(), any());
  }

  @Test
  void authorizedProtectedRestoreRemovesOverrideWithANullDeploymentDefault() {
    metadata.setNullable(true);
    metadata.setDefaultValue(null);
    metadata.setExtraProp("restoreDefaultRemovesOverride", true);
    when(values.get(KEY)).thenReturn(row("[]", 8L));
    when(values.removeOverride(eq(KEY), eq("8"), any())).thenReturn(true);
    var result = CurrentSecurityContextHolder.withIdentity(identity, () -> service.updateManaged(
        Map.of(KEY, ConfigurationMutation.RestoreDefaults.INSTANCE), "operator", Map.of(KEY, "8")));
    assertThat(result.get(KEY).getConfigValue()).isNull();
    assertThat(result.get(KEY).getRevision()).startsWith("default-");
    verify(values).removeOverride(eq(KEY), eq("8"), any());
    verify(values, never()).update(any(), any(), any());
  }

  private RuntimeConfigurationValueModel row(String value, long revision) {
    var row = new RuntimeConfigurationValueModel();
    row.setKey(KEY);
    row.setValue(value);
    row.setRevision(revision);
    return row;
  }

  private ConfigurationMetadata metadata(String key, ConfigValueType type, String defaultValue) {
    var item = new ConfigurationMetadata();
    item.setKey(key);
    item.setValueType(type);
    item.setDefaultValue(defaultValue);
    item.setModifiable(true);
    return item;
  }

  private void install(ConfigurationMetadata... items) {
    Map<String, ConfigurationMetadata> map = new java.util.LinkedHashMap<>();
    for (var item : items) { map.put(item.getKey(), item); }
    ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", map);
  }

  private static class TestPolicy implements ConfigurationManagementAccessPolicy {
    public boolean supports(String key) { return KEY.equals(key); }

    public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
      snapshot.metadata().setDefaultValue("[]");
      return Optional.of(new ConfigurationSnapshot(snapshot.key(), List.of(Map.of("clientId", "a")),
          "[]", snapshot.revision(), snapshot.metadata()));
    }

    public java.util.Set<String> writeDependencies(String key, ConfigurationMutation mutation) { return java.util.Set.of(); }

    public com.ouroboros.configuration.management.ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context,
        ConfigurationSnapshot old, ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies) {
      if (!context.authenticated()) { throw new com.ouroboros.exception.BusinessException(403, "[CONFIGURATION_ACCESS_DENIED]"); }
      return new com.ouroboros.configuration.management.ConfigurationPreparedWrite(old.value(), context.userIdentity());
    }
  }
}
