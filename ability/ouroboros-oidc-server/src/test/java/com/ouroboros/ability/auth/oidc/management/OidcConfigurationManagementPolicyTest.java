package com.ouroboros.ability.auth.oidc.management;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.ouroboros.ability.auth.oidc.OidcClientSpec;
import com.ouroboros.ability.auth.oidc.OidcRuntimeSettings;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.AuthorizationBoundary;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Json;

class OidcConfigurationManagementPolicyTest {
  private final OidcConfigurationManagementPolicy policy = new OidcConfigurationManagementPolicy();
  private final ConfigurationManagementContext actor = new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.MANAGEMENT);
  private MockedStatic<AuthorityCenter> definitions;
  private OidcClientSpec a;
  private OidcClientSpec b;
  private String firstSecret;

  @BeforeEach
  void setup() {
    definitions = mockStatic(AuthorityCenter.class);
    definitions.when(() -> AuthorityCenter.getAuthority(anyString())).thenAnswer(call -> {
      var authority = new BaseAuthority(); authority.setFullName(call.getArgument(0));
      authority.setDelegable(call.<String>getArgument(0).startsWith("orders."));
      return Optional.of(authority);
    });
    a = client("a"); b = client("b");
    firstSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    a.setClientType(OidcClientSpec.ClientType.CONFIDENTIAL);
    a.setClientSecretHash("{bcrypt}" + new BCryptPasswordEncoder().encode(firstSecret));
    authenticate(grant(ClientManagementAccess.SECURITY, ResolvedAccessControlParams.all()));
  }

  @AfterEach
  void cleanup() { definitions.close(); CurrentSecurityContextHolder.clearDelegate(); }

  @Test
  void everyValueShapeIsFilteredAndRedactedWithoutMutatingTheOriginal() {
    var source = snapshot(List.of(a, b));
    String original = Json.toJsonString(source.value());
    authenticate(grant(ClientManagementAccess.MANAGE, params("a", "clientName", List.of())));
    var projected = policy.projectRead(actor, source).orElseThrow();
    String result = Json.toJsonString(projected);
    assertFalse(result.contains("clientSecretHash"));
    assertFalse(result.contains(a.getClientSecretHash()));
    assertFalse(result.contains("registrationId"));
    assertFalse(result.contains("\"clientId\":\"b\""));
    assertTrue(result.contains("secretConfigured"));
    assertEquals("17", projected.revision());
    assertEquals(original, Json.toJsonString(source.value()));
  }

  @Test
  void ordinaryConfigurationPermissionAndDelegatedCredentialsCannotReadOrWrite() {
    authenticate(grant("systemSetting.configurationManagement", ResolvedAccessControlParams.all()));
    assertTrue(policy.projectRead(actor, snapshot(List.of(a))).isEmpty());
    assertThrows(RuntimeException.class, () -> update("a", Map.of("clientName", "x"), null));
    var user = user(List.of(grant(ClientManagementAccess.SECURITY, ResolvedAccessControlParams.all())));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user,
        AuthorizationBoundary.restricted("platform-api", Map.of(ClientManagementAccess.SECURITY, ResolvedAccessControlParams.all())),
        new CredentialContext(CredentialContext.Kind.DELEGATED, "a", "grant", "platform-api"))));
    assertTrue(policy.projectRead(actor, snapshot(List.of(a))).isEmpty());
    assertThrows(RuntimeException.class, () -> update("a", Map.of("clientName", "x"), null));
  }

  @Test
  void patchPreservesOtherClientsRegistrationAndCredentialAndNewSecretReplacesOnlyTheTarget() {
    var edited = update("a", Map.of("clientName", "renamed"), null);
    assertEquals(a.getRegistrationId(), edited.getFirst().getRegistrationId());
    assertEquals(a.getClientSecretHash(), edited.getFirst().getClientSecretHash());
    assertEquals(Json.toJsonString(b), Json.toJsonString(edited.getLast()));
    String nextSecret = Base64.getUrlEncoder().withoutPadding().encodeToString("a-new-random-value-of-32-bytes!!!x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var rotated = update("a", Map.of(), nextSecret).getFirst();
    assertTrue(new BCryptPasswordEncoder().matches(nextSecret, rotated.getClientSecretHash().substring(8)));
    assertFalse(new BCryptPasswordEncoder().matches(firstSecret, rotated.getClientSecretHash().substring(8)));
    assertEquals(a.getRegistrationId(), rotated.getRegistrationId());
    assertFalse(Json.toJsonString(rotated).contains(nextSecret));
  }

  @Test
  void reservedFieldsAreRejectedAtAllDepthsAndGenericReplacementCannotSupplyCredentials() {
    for (String name : List.of("registrationId", "clientSecretHash", "clientSecretRef", "newClientSecret", "secretConfigured", "owner")) {
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> update("a", Map.of(name, "x"), null));
    }
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> update("a", Map.of("oidc", Map.of("clientSecretHash", "x")), null));
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> update("a", Map.of("apiAuthorization", Map.of("allowedBoundary", Map.of("fields", List.of("id")))), null));
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> policy.prepareWrite(actor, snapshot(List.of(a)), new ConfigurationMutation.Replace(List.of(Map.of("clientId", "a", "clientSecretHash", "x"))), java.util.Map.of()).value());
  }

  @Test
  void sameGrantMustPermitTheClientFieldAndAddedPermissionsTogether() {
    var forA = grant(ClientManagementAccess.MANAGE, params("a", "clientName", List.of("orders.read")));
    var forB = grant(ClientManagementAccess.MANAGE, params("b", "apiAuthorization.allowedBoundary.permissions", List.of("orders.write")));
    authenticate(forA, forB);
    assertEquals("new", update("a", Map.of("clientName", "new"), null).getFirst().getClientName());
    assertThrows(RuntimeException.class, () -> update("a", Map.of("apiAuthorization", Map.of("allowedBoundary", Map.of("permissions", List.of("orders.write")))), null));
    assertThrows(RuntimeException.class, () -> update("b", Map.of("clientName", "new"), null));
    assertThrows(RuntimeException.class, () -> update("a", Map.of("enabled", false), null));
    assertThrows(RuntimeException.class, () -> policy.prepareWrite(actor, snapshot(List.of(a)), new ConfigurationMutation.Replace(List.of()), java.util.Map.of()).value());
  }

  @Test
  void defaultsPreserveCredentialsTypeAndIdentityAndCannotExpandAuthorization() {
    var source = snapshot(List.of(a, b));
    var disabled = client("a"); disabled.setEnabled(false);
    disabled.setClientType(a.getClientType()); disabled.setClientSecretHash(a.getClientSecretHash()); disabled.setRegistrationId(a.getRegistrationId());
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> policy.prepareWrite(actor, new ConfigurationSnapshot(source.key(), List.of(disabled), null, "17", source.metadata()), ConfigurationMutation.RestoreDefaults.INSTANCE, java.util.Map.of()).value());
    var restored = clients(policy.prepareWrite(actor, source, ConfigurationMutation.RestoreDefaults.INSTANCE, java.util.Map.of()).value());
    assertEquals(a.getClientSecretHash(), restored.getFirst().getClientSecretHash());
    assertEquals(a.getClientType(), restored.getFirst().getClientType());
    assertEquals(a.getRegistrationId(), restored.getFirst().getRegistrationId());
  }

  @Test
  void creatingAndRecreatingClientUsesFreshRegistrationAndRequiresConfidentialSecret() {
    var create = new ClientConfigurationMutation(ClientConfigurationMutation.Action.CREATE, "c",
        Map.of("clientType", "CONFIDENTIAL", "redirectUris", List.of("https://c.example/callback")), null);
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> policy.prepareWrite(actor, snapshot(List.of(a)), create, java.util.Map.of()).value());
    var command = new ClientConfigurationMutation(create.action(), "c", create.changes(), firstSecret);
    var one = clients(policy.prepareWrite(actor, snapshot(List.of(a)), command, java.util.Map.of()).value()).getLast();
    var two = clients(policy.prepareWrite(actor, snapshot(List.of(a)), command, java.util.Map.of()).value()).getLast();
    assertNotEquals(one.getRegistrationId(), two.getRegistrationId());
    assertEquals(OidcClientSpec.ClientType.CONFIDENTIAL, one.getClientType());
    assertFalse(one.getApiAuthorization().isEnabled());
  }

  @Test
  void invalidSecretAndPublicSecretAreRejectedAndEmptySecretPreservesOldHash() {
    for (String value : List.of("password", "***", "a".repeat(73))) {
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> update("a", Map.of(), value));
    }
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> update("b", Map.of(), firstSecret));
    assertEquals(a.getClientSecretHash(), update("a", Map.of(), "").getFirst().getClientSecretHash());
  }

  @Test
  void globalSettingsRequireTheirOwnAuthorityAndRespectDurationBounds() {
    var metadata = new ConfigurationMetadata(); metadata.setDefaultValue(Json.toJsonString(new OidcRuntimeSettings(false, Duration.ofMinutes(5), Duration.ofHours(8))));
    var settings = new ConfigurationSnapshot(OidcRuntimeSettings.KEY, new OidcRuntimeSettings(false, Duration.ofMinutes(5), Duration.ofHours(8)), null, "1", metadata);
    assertTrue(policy.projectRead(actor, settings).isEmpty());
    assertThrows(RuntimeException.class, () -> policy.prepareWrite(actor, settings, ConfigurationMutation.RestoreDefaults.INSTANCE, java.util.Map.of()).value());
    authenticate(grant(ClientManagementAccess.SETTINGS, ResolvedAccessControlParams.all()));
    assertTrue(policy.projectRead(actor, settings).isPresent());
    assertEquals(false, ((OidcRuntimeSettings) policy.prepareWrite(actor, settings, ConfigurationMutation.RestoreDefaults.INSTANCE, java.util.Map.of()).value()).enabled());
    assertThrows(ValidationException.class, () -> new OidcRuntimeSettings(true, Duration.ZERO, Duration.ofHours(1)));
  }

  private List<OidcClientSpec> update(String id, Map<String, Object> changes, String secret) {
    return clients(policy.prepareWrite(actor, snapshot(List.of(a, b)), new ClientConfigurationMutation(
        ClientConfigurationMutation.Action.UPDATE, id, changes, secret), java.util.Map.of()).value());
  }
  private List<OidcClientSpec> clients(Object value) { return ConfigurationRegisteredClientRepository.parseAndValidate(value); }
  private ConfigurationSnapshot snapshot(List<OidcClientSpec> clients) {
    var metadata = new ConfigurationMetadata(); metadata.setDefaultValue(Json.toJsonString(clients));
    return new ConfigurationSnapshot(ConfigurationRegisteredClientRepository.KEY, clients, Json.toJsonString(clients), "17", metadata);
  }
  private OidcClientSpec client(String id) {
    var spec = new OidcClientSpec(); spec.setClientId(id); spec.setRegistrationId(UUID.randomUUID().toString());
    spec.setRedirectUris(List.of("https://" + id + ".example/callback")); return spec;
  }
  private EffectiveAuthority grant(String name, AccessControlParams params) {
    var definition = new BaseAuthority(); definition.setFullName(name); return new EffectiveAuthority(definition, params);
  }
  private AccessControlParams params(String id, String field, List<String> permissions) {
    var value = mock(AccessControlParams.class);
    when(value.getFilters()).thenReturn(Map.of("clientIds", List.of(id), "grantableAuthorityNames", permissions));
    when(value.getFieldConstraint()).thenReturn(com.ouroboros.security.authority.accesscontrol.FieldConstraint.only(Set.of(field)));
    when(value.getRowConstraint()).thenReturn(com.ouroboros.security.authority.accesscontrol.RowConstraint.all());
    return value;
  }
  private void authenticate(EffectiveAuthority... grants) {
    var identity = new DefaultSecurityIdentity(user(List.of(grants)));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(identity));
  }
  private User user(List<EffectiveAuthority> grants) {
    Role role = mock(Role.class); when(role.getAuthorities()).thenAnswer(call -> new ArrayList<>(grants));
    User user = mock(User.class); when(user.getUserIdentity()).thenReturn("admin"); when(user.getRoles()).thenReturn(List.of(role)); return user;
  }
}
