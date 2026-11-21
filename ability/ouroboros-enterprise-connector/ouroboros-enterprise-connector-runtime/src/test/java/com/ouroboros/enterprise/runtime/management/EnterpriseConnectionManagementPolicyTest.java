package com.ouroboros.enterprise.runtime.management;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.EnterpriseCredential;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionConfigurationValidator;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.runtime.credential.*;
import com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Json;

class EnterpriseConnectionManagementPolicyTest {
  private static final String CONNECTIONS = EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY;
  private static final String CREDENTIALS = EnterpriseManagedCredentials.KEY;
  private final ConfigurationManagementContext actor = new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.MANAGEMENT);
  private final EnterpriseCredentialCipher cipher = new EnterpriseCredentialCipher("key", Map.of("key", Base64.getEncoder().encodeToString(new byte[32])));
  private final EnterpriseManagedCredentials credentials = new EnterpriseManagedCredentials(mock(ConfigurationService.class), cipher);
  private final CompositeSecretResolver resolver = mock(CompositeSecretResolver.class);
  private EnterpriseConnectionManagementPolicy policy;
  private MockedStatic<AuthorityCenter> definitions;

  @BeforeEach
  void setUp() {
    definitions = mockStatic(AuthorityCenter.class);
    definitions.when(() -> AuthorityCenter.getAuthority(anyString())).thenAnswer(call -> {
      var definition = new BaseAuthority(); definition.setFullName(call.getArgument(0)); return Optional.of(definition);
    });
    var providers = EnterpriseConnectionProviderRegistry.of(List.of(new Provider()));
    policy = new EnterpriseConnectionManagementPolicy(providers,
        new EnterpriseConnectionConfigurationValidator(providers, List.of(new Mapper())), credentials, List.of(new Mapper()), resolver);
    authenticate(EnterpriseConnectionAccess.VIEW, EnterpriseConnectionAccess.CONFIGURE, EnterpriseConnectionAccess.ROTATE, EnterpriseConnectionAccess.DISABLE);
  }

  @AfterEach void tearDown() { definitions.close(); CurrentSecurityContextHolder.clearDelegate(); }

  @Test
  void budgetChangesUseTheSameProtectedPolicyAndPreserveOtherProviders() {
    String key = com.ouroboros.enterprise.runtime.ConfiguredEnterpriseApiBudget.KEY;
    var stored = snapshot(key, Map.of("test", 5, "missing-plugin", 8));
    var command = new EnterpriseApiBudgetMutation("test", new java.math.BigDecimal("12"));
    assertThat(policy.prepareWrite(actor, stored, command, Map.of()).value())
        .isEqualTo(Map.of("test", 12, "missing-plugin", 8));
    assertThat(policy.writeDependencies(key, command)).isEmpty();
    assertThatThrownBy(() -> policy.prepareWrite(actor, stored,
        new EnterpriseApiBudgetMutation("test", new java.math.BigDecimal("1.5")), Map.of())).hasMessageContaining("整数");
    assertThatThrownBy(() -> policy.prepareWrite(actor, stored,
        new EnterpriseApiBudgetMutation("absent", java.math.BigDecimal.ONE), Map.of())).hasMessageContaining("已安装");
    assertThatThrownBy(() -> policy.prepareWrite(actor, stored,
        new EnterpriseApiBudgetMutation(null, java.math.BigDecimal.ONE), Map.of())).isInstanceOf(com.ouroboros.exception.ValidationException.class);
    assertThatThrownBy(() -> policy.prepareWrite(actor, stored, new ConfigurationMutation.Replace(Map.of()), Map.of()))
        .hasMessageContaining("预算设置");
    authenticate(EnterpriseConnectionAccess.VIEW, EnterpriseConnectionAccess.ROTATE);
    assertThatThrownBy(() -> policy.prepareWrite(actor, stored, command, Map.of())).hasMessageContaining("无权");
  }

  @Test
  void clearsOnlyOrphanedCredentialsAndPreservesOtherEncryptedRecords() {
    var orphan = cipher.encrypt("absent-plugin", "orphan", payload("old-app", "old-secret"));
    var retained = cipher.encrypt("test", "main", payload("app-a", "secret"));
    var stored = snapshot(CREDENTIALS, Map.of("main", retained, "orphan", orphan));
    var command = new EnterpriseCredentialRemoval("orphan");
    var dependencies = Map.of(CONNECTIONS, snapshot(CONNECTIONS, List.of(entry(true))));
    var cleared = policy.prepareWrite(actor, stored, command, dependencies);
    assertThat(cleared.value()).isEqualTo(Map.of("main", retained));
    assertThat(cleared.auditActor()).isEqualTo("admin");
    assertThat(policy.writeDependencies(CREDENTIALS, command)).containsExactly(CONNECTIONS);
    assertThat(policy.prepareWrite(actor, snapshot(CREDENTIALS, cleared.value()), command, dependencies).value())
        .isEqualTo(cleared.value());
  }

  @Test
  void refusesClearingMaterialReferencedByActiveDisabledOrUnavailableConnections() {
    var stored = snapshot(CREDENTIALS, Map.of("main", cipher.encrypt("test", "main", payload("app-a", "secret"))));
    for (var connection : List.of(entry(true), entry(false),
        Map.<String, Object>of("connectionId", "other", "provider", "missing", "credentialRef", "managed:main"))) {
      assertThatThrownBy(() -> policy.prepareWrite(actor, stored, new EnterpriseCredentialRemoval("main"),
          Map.of(CONNECTIONS, snapshot(CONNECTIONS, List.of(connection)))))
          .hasMessageContaining("仍被企业连接引用");
    }
    assertThatThrownBy(() -> policy.prepareWrite(actor, stored, new EnterpriseCredentialRemoval("main"), Map.of()))
        .hasMessageContaining("锁定的连接");
    authenticate(EnterpriseConnectionAccess.VIEW, EnterpriseConnectionAccess.CONFIGURE);
    assertThatThrownBy(() -> policy.prepareWrite(actor, stored, new EnterpriseCredentialRemoval("main"),
        Map.of(CONNECTIONS, snapshot(CONNECTIONS, List.of())))).hasMessageContaining("无权");
  }

  @Test
  void savesConnectionAndEncryptedCredentialThroughTheTwoOriginalProtectedKeys() {
    var encrypted = cipher.encrypt("test", "main", payload("app-a", "secret-a"));
    var command = new EnterpriseConnectionMutation(entry(true), encrypted, false);
    var emptyConnections = snapshot(CONNECTIONS, List.of()); var emptyCredentials = snapshot(CREDENTIALS, Map.of());
    var connectionWrite = policy.prepareWrite(actor, emptyConnections, command, Map.of(CREDENTIALS, emptyCredentials));
    var credentialWrite = policy.prepareWrite(actor, emptyCredentials, command, Map.of(CONNECTIONS, emptyConnections));
    assertThat(connectionWrite.value()).isEqualTo(List.of(entry(true)));
    var record = EnterpriseManagedCredentials.records(Json.toMap(Json.toJsonString(credentialWrite.value()))).get("main");
    assertThat(cipher.decrypt(record)).isEqualTo(payload("app-a", "secret-a"));
    assertThat(Json.toJsonString(credentialWrite.value())).doesNotContain("secret-a", "app-a");
    assertThat(connectionWrite.auditActor()).isEqualTo("admin");
    assertThat(policy.writeDependencies(CONNECTIONS, command)).containsExactly(CREDENTIALS);
    assertThat(policy.writeDependencies(CREDENTIALS, command)).containsExactly(CONNECTIONS);
  }

  @Test
  void absentPluginsRemainOpaqueAndUnchangedWhenAnInstalledConnectionIsUpdated() {
    Map<String, Object> absent = Map.of("connectionId", "old", "provider", "absent-plugin",
        "customField", Map.of("vendor", "opaque-value"), "credentialRef", "managed:old");
    var previous = snapshot(CONNECTIONS, List.of(absent, entry(true)));
    var encrypted = cipher.encrypt("test", "main", payload("app-a", "secret"));
    var stored = snapshot(CREDENTIALS, Map.of("main", encrypted));
    var write = policy.prepareWrite(actor, previous, new EnterpriseConnectionMutation(entry(false), null, true),
        Map.of(CREDENTIALS, stored));
    assertThat(write.value()).isEqualTo(List.of(absent, entry(false)));
    var read = policy.projectRead(actor, previous).orElseThrow();
    assertThat(Json.toJsonString(read.value())).contains("absent-plugin", "pluginUnavailable")
        .doesNotContain("opaque-value", "customField");
    assertThatThrownBy(() -> policy.prepareWrite(actor, previous,
        new EnterpriseConnectionMutation(absent, null, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("连接类型未安装");
    var invalid = new LinkedHashMap<>(entry(true)); invalid.put("undeclared", "bad");
    assertThatThrownBy(() -> policy.prepareWrite(actor, previous,
        new EnterpriseConnectionMutation(invalid, null, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("未在 provider schema");
  }

  @Test
  void keepsOmittedCredentialsAndRotatesOnlyWithinTheSameApplicationIdentity() {
    var previous = cipher.encrypt("test", "main", payload("app-a", "old-secret"));
    var stored = snapshot(CREDENTIALS, Map.of("main", previous));
    var connections = snapshot(CONNECTIONS, List.of(entry(true)));
    var unchanged = new EnterpriseConnectionMutation(entry(true), null, false);
    assertThat(policy.prepareWrite(actor, stored, unchanged, Map.of(CONNECTIONS, connections)).value()).isEqualTo(Map.of("main", previous));
    var next = cipher.encrypt("test", "main", payload("app-a", "new-secret"));
    var rotated = policy.prepareWrite(actor, stored, new EnterpriseConnectionMutation(entry(true), next, false), Map.of(CONNECTIONS, connections));
    assertThat(rotated.value()).isEqualTo(Map.of("main", next));
    var switched = cipher.encrypt("test", "main", payload("app-b", "new-secret"));
    assertThatThrownBy(() -> policy.prepareWrite(actor, stored, new EnterpriseConnectionMutation(entry(true), switched, false), Map.of(CONNECTIONS, connections)))
        .hasMessageContaining("应用身份变更");
  }

  @Test
  void refusesGenericWritesWrongBindingsMissingCredentialsAndEnterpriseIdentityChanges() {
    var connections = snapshot(CONNECTIONS, List.of(entry(true)));
    var stored = snapshot(CREDENTIALS, Map.of("main", cipher.encrypt("test", "main", payload("app-a", "secret"))));
    assertThatThrownBy(() -> policy.prepareWrite(actor, connections, new ConfigurationMutation.Replace(List.of()), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("集成管理");
    assertThatThrownBy(() -> policy.prepareWrite(actor, connections, ConfigurationMutation.RestoreDefaults.INSTANCE, Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("集成管理");
    var wrongCorp = new LinkedHashMap<>(entry(true)); wrongCorp.put("corpId", "other");
    assertThatThrownBy(() -> policy.prepareWrite(actor, connections, new EnterpriseConnectionMutation(wrongCorp, null, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("企业身份变更");
    var wrongCipher = cipher.encrypt("test", "other", payload("app-a", "secret"));
    assertThatThrownBy(() -> policy.prepareWrite(actor, connections, new EnterpriseConnectionMutation(entry(true), wrongCipher, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("绑定不一致");
    assertThatThrownBy(() -> policy.prepareWrite(actor, snapshot(CONNECTIONS, List.of()), new EnterpriseConnectionMutation(entry(true), null, false), Map.of(CREDENTIALS, snapshot(CREDENTIALS, Map.of()))))
        .hasMessageContaining("托管凭据引用无效");
  }

  @Test
  void disableRequiresItsOwnPermissionAndExplicitAcknowledgment() {
    var connections = snapshot(CONNECTIONS, List.of(entry(true)));
    var stored = snapshot(CREDENTIALS, Map.of("main", cipher.encrypt("test", "main", payload("app-a", "secret"))));
    assertThatThrownBy(() -> policy.prepareWrite(actor, connections, new EnterpriseConnectionMutation(entry(false), null, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("确认停用影响");
    authenticate(EnterpriseConnectionAccess.CONFIGURE);
    assertThatThrownBy(() -> policy.prepareWrite(actor, connections, new EnterpriseConnectionMutation(entry(false), null, true), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("无权");
    authenticate(EnterpriseConnectionAccess.CONFIGURE, EnterpriseConnectionAccess.DISABLE);
    assertThat(policy.prepareWrite(actor, connections, new EnterpriseConnectionMutation(entry(false), null, true), Map.of(CREDENTIALS, stored)).value())
        .isEqualTo(List.of(entry(false)));
  }

  @Test
  void credentialsAreInvisibleToScriptsAndUnauthorizedReadersAndManagementGetsOnlyStatusAndVersion() {
    var record = cipher.encrypt("test", "main", payload("app-a", "secret"));
    var stored = snapshot(CREDENTIALS, Map.of("main", record));
    var projected = policy.projectRead(actor, stored).orElseThrow();
    assertThat(Json.toJsonString(projected.value())).contains("credentialVersion", record.credentialVersion(), "configured")
        .doesNotContain("secret", record.ciphertext(), record.nonce(), "keyId");
    assertThat(projected.storedRawValue()).isNull();
    assertThat(projected.metadata().getDefaultValue()).isNull();
    assertThat(policy.projectRead(new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.SCRIPT), stored)).isEmpty();
    authenticate(EnterpriseConnectionAccess.CONFIGURE);
    assertThat(policy.projectRead(actor, stored)).isEmpty();
  }

  @Test
  void rotationPermissionIsIndependentAndMalformedPayloadDoesNotLeakDetails() {
    var empty = snapshot(CONNECTIONS, List.of()); var stored = snapshot(CREDENTIALS, Map.of());
    var encrypted = cipher.encrypt("test", "main", "secret-invalid-json");
    authenticate(EnterpriseConnectionAccess.CONFIGURE);
    assertThatThrownBy(() -> policy.prepareWrite(actor, empty, new EnterpriseConnectionMutation(entry(true), encrypted, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("无权");
    authenticate(EnterpriseConnectionAccess.CONFIGURE, EnterpriseConnectionAccess.ROTATE);
    assertThatThrownBy(() -> policy.prepareWrite(actor, empty, new EnterpriseConnectionMutation(entry(true), encrypted, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("凭据格式").hasMessageNotContaining("secret-invalid-json").hasNoCause();
  }

  @Test
  void migrationChecksExternalApplicationIdentityAndRequiresExplicitReplacement() {
    var external = new LinkedHashMap<>(entry(true)); external.put("credentialRef", "env:LEGACY");
    var old = snapshot(CONNECTIONS, List.of(external)); var stored = snapshot(CREDENTIALS, Map.of());
    when(resolver.resolve("env:LEGACY")).thenReturn(Optional.of(payload("app-a", "legacy-secret")));
    var same = cipher.encrypt("test", "main", payload("app-a", "new-secret"));
    assertThat(policy.prepareWrite(actor, old, new EnterpriseConnectionMutation(entry(true), same, false), Map.of(CREDENTIALS, stored)).value())
        .isEqualTo(List.of(entry(true)));
    var other = cipher.encrypt("test", "main", payload("app-b", "new-secret"));
    assertThatThrownBy(() -> policy.prepareWrite(actor, old, new EnterpriseConnectionMutation(entry(true), other, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("应用身份");
    var changed = new LinkedHashMap<>(external); changed.put("credentialRef", "env:OTHER");
    assertThatThrownBy(() -> policy.prepareWrite(actor, old, new EnterpriseConnectionMutation(changed, null, false), Map.of(CREDENTIALS, stored)))
        .hasMessageContaining("显式凭据替换");
  }

  @Test
  void secretRotationAndDisableCanBeDelegatedWithoutGeneralConfigurationRights() {
    var connections = snapshot(CONNECTIONS, List.of(entry(true)));
    var stored = snapshot(CREDENTIALS, Map.of("main", cipher.encrypt("test", "main", payload("app-a", "old-secret"))));
    authenticate(EnterpriseConnectionAccess.ROTATE);
    var replacement = cipher.encrypt("test", "main", payload("app-a", "new-secret"));
    assertThat(policy.prepareWrite(actor, stored, new EnterpriseConnectionMutation(entry(true), replacement, false),
        Map.of(CONNECTIONS, connections)).value()).isEqualTo(Map.of("main", replacement));
    authenticate(EnterpriseConnectionAccess.DISABLE);
    assertThat(policy.prepareWrite(actor, connections, new EnterpriseConnectionMutation(entry(false), null, true),
        Map.of(CREDENTIALS, stored)).value()).isEqualTo(List.of(entry(false)));
    var edit = new LinkedHashMap<>(entry(false)); edit.put("auth", Map.of("enabled", false));
    assertThatThrownBy(() -> policy.prepareWrite(actor, connections, new EnterpriseConnectionMutation(edit, null, true),
        Map.of(CREDENTIALS, stored))).hasMessageContaining("无权");
  }

  @Test
  void reencryptsAllStoredCredentialsWithExplicitRotationPermissionThroughTheOriginalPolicy() {
    var previous = cipher.encrypt("test", "main", payload("app-a", "private"));
    var providers = EnterpriseConnectionProviderRegistry.of(List.of(new Provider()));
    var nextCipher = new EnterpriseCredentialCipher("next", Map.of("key", Base64.getEncoder().encodeToString(new byte[32]),
        "next", Base64.getEncoder().encodeToString(new byte[32])));
    var rotating = new EnterpriseConnectionManagementPolicy(providers,
        new EnterpriseConnectionConfigurationValidator(providers, List.of(new Mapper())),
        new EnterpriseManagedCredentials(mock(ConfigurationService.class), nextCipher), List.of(new Mapper()), resolver);
    var stored = snapshot(CREDENTIALS, Map.of("main", previous));
    authenticate(EnterpriseConnectionAccess.ROTATE);
    var prepared = rotating.prepareWrite(actor, stored, EnterpriseCredentialReencryption.INSTANCE, Map.of());
    var record = EnterpriseManagedCredentials.records(Json.toMap(Json.toJsonString(prepared.value()))).get("main");
    assertThat(record.keyId()).isEqualTo("next");
    assertThat(record.credentialVersion()).isEqualTo(previous.credentialVersion());
    assertThat(nextCipher.decrypt(record)).isEqualTo(payload("app-a", "private"));
    assertThat(prepared.auditActor()).isEqualTo("admin");
    authenticate(EnterpriseConnectionAccess.CONFIGURE);
    assertThatThrownBy(() -> rotating.prepareWrite(actor, stored, EnterpriseCredentialReencryption.INSTANCE, Map.of()))
        .hasMessageContaining("无权");
  }

  @Test
  void editorSubmitsBothRowsAndTheirExpectedRevisionsAsOneEncryptedCommand() {
    var management = mock(com.ouroboros.configuration.management.ConfigurationManagementService.class);
    when(management.readManagementSnapshot(CONNECTIONS)).thenReturn(Optional.of(snapshot(CONNECTIONS, List.of(entry(true)))));
    when(management.readManagementSnapshot(CREDENTIALS)).thenReturn(Optional.of(snapshot(CREDENTIALS, Map.of())));
    Map<String, String> revisions = Map.of(CONNECTIONS, "connection-revision", CREDENTIALS, "credential-revision");
    when(management.mutateManagementConfigurations(anyMap(), eq(revisions))).thenAnswer(invocation -> {
      Map<?, ?> changes = invocation.getArgument(0, Map.class);
      assertThat(changes.keySet()).hasSize(2);
      assertThat(changes.get(CONNECTIONS)).isSameAs(changes.get(CREDENTIALS));
      assertThat(changes.get(CONNECTIONS)).isInstanceOfSatisfying(EnterpriseConnectionMutation.class, command -> {
        assertThat(command.connection().get("credentialRef")).isEqualTo("managed:main");
        assertThat(cipher.decrypt(command.replacement())).isEqualTo(payload("app-a", "private-secret"));
        assertThat(command.toString()).doesNotContain("private-secret", command.replacement().ciphertext());
      });
      return Map.of();
    });
    var controller = new EnterpriseConnectionManagementController(management, cipher,
        EnterpriseConnectionProviderRegistry.of(List.of(new Provider() {
          @Override public int maxConnections() { return 1; }
        })), Optional.empty());
    var request = new EnterpriseConnectionManagementController.SaveRequest(entry(true),
        Json.toMap(payload("app-a", "private-secret")), revisions, false);
    assertThat(Json.toJsonString(controller.save(request))).doesNotContain("private-secret", "ciphertext");
    assertThat(request.toString()).doesNotContain("private-secret");
    verify(management).mutateManagementConfigurations(anyMap(), eq(revisions));
    var response = new org.springframework.mock.web.MockHttpServletResponse();
    controller.preventCaching(response);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  void callbackAddressUsesConfiguredPublicBaseAndAlwaysTheUnifiedBusinessRoute() {
    var management = mock(com.ouroboros.configuration.management.ConfigurationManagementService.class);
    when(management.readManagementSnapshot(CONNECTIONS)).thenReturn(Optional.of(snapshot(CONNECTIONS, List.of(entry(true)))));
    when(management.readManagementSnapshot(CREDENTIALS)).thenReturn(Optional.of(snapshot(CREDENTIALS, Map.of())));
    var properties = new com.ouroboros.security.externalauth.config.ExternalAuthProperties();
    com.ouroboros.security.externalauth.config.ExternalAuthConfiguration configuration = () -> properties;
    var controller = new EnterpriseConnectionManagementController(management, cipher,
        EnterpriseConnectionProviderRegistry.of(List.of(new Provider() {
          @Override public int maxConnections() { return 1; }
        })), Optional.of(configuration));
    assertThat(controller.readProvider("test").getData().get("businessCallbackUrl"))
        .isEqualTo("/api/enterprise-callbacks/main/callback");
    properties.setPublicBaseUrl("https://public.example/backend/");
    assertThat(controller.readProvider("test").getData().get("businessCallbackUrl"))
        .isEqualTo("https://public.example/backend/api/enterprise-callbacks/main/callback");
    properties.setPublicBaseUrl("https://public.example/?redirect=other");
    assertThatThrownBy(() -> controller.readProvider("test")).hasMessageContaining("公开平台地址配置无效");
  }

  @Test
  void editorRejectsMissingVersionsAndInsufficientRightsBeforeWriting() {
    var management = mock(com.ouroboros.configuration.management.ConfigurationManagementService.class);
    var controller = new EnterpriseConnectionManagementController(management, cipher,
        EnterpriseConnectionProviderRegistry.of(List.of(new Provider())), Optional.empty());
    assertThatThrownBy(() -> controller.save(new EnterpriseConnectionManagementController.SaveRequest(entry(true), null, Map.of(), false)))
        .hasMessageContaining("配置版本");
    authenticate(EnterpriseConnectionAccess.VIEW);
    assertThatThrownBy(() -> controller.save(new EnterpriseConnectionManagementController.SaveRequest(entry(true), null, Map.of(), false)))
        .hasMessageContaining("无权");
    verifyNoInteractions(management);
  }

  private static ConfigurationSnapshot snapshot(String key, Object value) {
    var metadata = new ConfigurationMetadata(); metadata.setKey(key); metadata.setDefaultValue(Json.toJsonString(value));
    return new ConfigurationSnapshot(key, value, Json.toJsonString(value), "revision", metadata);
  }

  private static Map<String, Object> entry(boolean enabled) {
    return Map.of("connectionId", "main", "provider", "test", "corpId", "corp", "credentialRef", "managed:main", "enabled", enabled);
  }
  private static String payload(String app, String secret) { return Json.toJsonString(Map.of("appId", app, "secret", secret)); }
  private record Credential(String appId) implements EnterpriseCredential {
    @Override public Map<String, String> applicationIdentity() { return Map.of("appId", appId); }
  }
  private static final class Mapper implements EnterpriseConnectionCredentialMapper {
    @Override public String provider() { return "test"; }
    @Override public EnterpriseCredential map(String payload) { return new Credential((String) Json.toMap(payload).get("appId")); }
  }
  private static class Provider implements EnterpriseConnectionProvider {
    @Override public String provider() { return "test"; }
    @Override public Set<String> connectionConfigFields() { return Set.of("corpId"); }
    @Override public Set<String> connectionIdentityFields() { return Set.of("corpId"); }
  }
  private void authenticate(String... names) {
    var grants = java.util.Arrays.stream(names).map(name -> {
      var definition = new BaseAuthority(); definition.setFullName(name);
      return new EffectiveAuthority(definition, ResolvedAccessControlParams.all());
    }).toList();
    var role = mock(Role.class); when(role.getAuthorities()).thenAnswer(call -> new ArrayList<>(grants));
    var user = mock(User.class); when(user.getUserIdentity()).thenReturn("admin"); when(user.getRoles()).thenReturn(List.of(role));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
  }
}
