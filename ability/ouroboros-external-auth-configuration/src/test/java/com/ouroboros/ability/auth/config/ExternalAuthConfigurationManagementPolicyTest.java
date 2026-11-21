package com.ouroboros.ability.auth.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Json;

class ExternalAuthConfigurationManagementPolicyTest {
  private final ExternalAuthConfigurationManagementPolicy policy = new ExternalAuthConfigurationManagementPolicy();
  private final ConfigurationManagementContext actor = new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.MANAGEMENT);
  private MockedStatic<AuthorityCenter> definitions;

  @BeforeEach
  void setUp() {
    definitions = mockStatic(AuthorityCenter.class);
    definitions.when(() -> AuthorityCenter.getAuthority(anyString())).thenAnswer(call -> {
      var definition = new BaseAuthority(); definition.setFullName(call.getArgument(0)); return Optional.of(definition);
    });
    authenticate(ExternalAuthManagementAccess.CONFIGURE);
  }

  @AfterEach
  void tearDown() { definitions.close(); CurrentSecurityContextHolder.clearDelegate(); }

  @Test
  void projectsOnlyAuthorizedGroupsAndNeverExposesExistingPasswordsOrRawDefaults() {
    var snapshot = snapshot();
    var projected = policy.projectRead(actor, snapshot).orElseThrow();
    String value = Json.toJsonString(projected.value());
    assertThat(value).contains("ldap", "cas").doesNotContain("private-password", "publicBaseUrl", "futureExtension");
    assertThat(projected.storedRawValue()).isNull();
    assertThat(projected.metadata().getDefaultValue()).isNull();
    assertThat(Json.toJsonString(snapshot.value())).contains("private-password");
    authenticate(ExternalAuthManagementAccess.SETTINGS);
    assertThat(ExternalAuthConfigurationManagementPolicy.object(policy.projectRead(actor, snapshot()).orElseThrow().value()))
        .containsKey("publicBaseUrl").doesNotContainKeys("ldap", "oauth2", "cas");
    assertThat(policy.projectRead(new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.SCRIPT), snapshot()))
        .isEmpty();
  }

  @Test
  void editsOneProtocolAndPreservesUneditedProtocolsUnknownFieldsAndBlankPasswords() {
    var command = new ExternalAuthConfigurationMutation("ldap", Map.of("providers", List.of(Map.of(
        "key", "corp-directory", "displayName", "New directory", "searchBindPassword", ""))));
    String saved = Json.toJsonString(policy.prepareWrite(actor, snapshot(), command, Map.of()).value());
    assertThat(saved).contains("New directory", "private-password", "futureExtension", "other-protocol", "futureProviderField");
  }

  @Test
  void rejectsGenericReplacementCrossGroupWritesAndMissingPermissions() {
    assertThatThrownBy(() -> policy.prepareWrite(actor, snapshot(), new ConfigurationMutation.Replace(Map.of()), Map.of()))
        .hasMessageContaining("集成管理");
    assertThatThrownBy(() -> policy.prepareWrite(actor, snapshot(),
        new ExternalAuthConfigurationMutation("ldap", Map.of("publicBaseUrl", "https://evil.invalid")), Map.of()))
        .hasMessageContaining("无效");
    authenticate(ExternalAuthManagementAccess.SETTINGS);
    assertThatThrownBy(() -> policy.prepareWrite(actor, snapshot(),
        new ExternalAuthConfigurationMutation("ldap", Map.of("providers", List.of())), Map.of())).hasMessageContaining("无权");
  }

  @Test
  void replacingAPasswordWithAReferenceRemovesTheOldInlineSecret() {
    var command = new ExternalAuthConfigurationMutation("ldap", Map.of("providers", List.of(Map.of(
        "key", "corp-directory", "searchBindPasswordRef", "env:LDAP_PASSWORD"))));
    String saved = Json.toJsonString(policy.prepareWrite(actor, snapshot(), command, Map.of()).value());
    assertThat(saved).contains("env:LDAP_PASSWORD").doesNotContain("private-password");
  }

  private ConfigurationSnapshot snapshot() {
    var values = new java.util.LinkedHashMap<String, Object>();
    values.put("publicBaseUrl", "https://platform.invalid");
    values.put("ldap", List.of(Map.of("key", "corp-directory", "type", "ldap", "enabled", false,
        "searchBindPassword", "private-password", "futureProviderField", "retained")));
    values.put("cas", List.of(Map.of("key", "other-protocol", "type", "cas", "enabled", false)));
    values.put("oauth2", List.of()); values.put("futureExtension", Map.of("installedElsewhere", true));
    var metadata = new ConfigurationMetadata(); metadata.setKey(UnifiedExternalAuthConfiguration.KEY);
    metadata.setDefaultValue(Json.toJsonString(values));
    return new ConfigurationSnapshot(UnifiedExternalAuthConfiguration.KEY, values, Json.toJsonString(values), "revision-1", metadata);
  }

  private void authenticate(String authority) {
    var definition = new BaseAuthority(); definition.setFullName(authority);
    var grant = new EffectiveAuthority(definition, ResolvedAccessControlParams.all());
    var role = mock(Role.class); when(role.getAuthorities()).thenAnswer(call -> new ArrayList<>(List.of(grant)));
    var user = mock(User.class); when(user.getUserIdentity()).thenReturn("admin"); when(user.getRoles()).thenReturn(List.of(role));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
  }
}
