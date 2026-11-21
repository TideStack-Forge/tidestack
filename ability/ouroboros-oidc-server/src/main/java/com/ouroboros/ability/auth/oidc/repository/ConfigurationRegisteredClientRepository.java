package com.ouroboros.ability.auth.oidc.repository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.ability.auth.oidc.OidcClientSpec;
import com.ouroboros.ability.auth.oidc.OidcClientSpec.ClientType;
import com.ouroboros.ability.auth.oidc.OidcRuntimeSettings;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.util.Json;

/** The original configuration service is the sole authority for registrations, including credentials. */
public class ConfigurationRegisteredClientRepository implements RegisteredClientRepository {
  public static final String KEY = "security.auth.oauth2.clients";
  public static final Set<String> IDENTITY_SCOPES = Set.of("openid", "profile", "email", "phone", "offline_access");
  private final ConfigurationService configuration;

  public ConfigurationRegisteredClientRepository(ConfigurationService configuration) {
    this.configuration = configuration;
  }

  @Override
  public void save(RegisteredClient client) { throw invalid("客户端请通过统一配置管理"); }

  @Override
  public RegisteredClient findById(String id) {
    return clients().stream().filter(OidcClientSpec::isEnabled)
        .filter(spec -> spec.getRegistrationId().equals(id)).findFirst().map(this::registeredClient).orElse(null);
  }

  @Override
  public RegisteredClient findByClientId(String clientId) {
    return clients().stream().filter(OidcClientSpec::isEnabled)
        .filter(spec -> spec.getClientId().equals(clientId)).findFirst().map(this::registeredClient).orElse(null);
  }

  public OidcClientSpec findSpec(String registrationId) {
    return clients().stream().filter(spec -> spec.getRegistrationId().equals(registrationId))
        .filter(OidcClientSpec::isEnabled).findFirst().orElse(null);
  }

  private List<OidcClientSpec> clients() {
    return parseAndValidate(configuration.getSnapshot(KEY)
        .orElseThrow(() -> invalid("客户端配置不可用")).value());
  }

  public static List<OidcClientSpec> parseAndValidate(Object value) {
    String json = value instanceof String text ? text : Json.toJsonString(value);
    List<OidcClientSpec> clients = Json.tryToList(new TypeReference<List<OidcClientSpec>>() {}, json)
        .filter(java.util.Objects::nonNull).getOrElseThrow(error -> invalid("客户端配置必须为有效数组"));
    Set<String> clientIds = new HashSet<>();
    Set<String> registrations = new HashSet<>();
    for (OidcClientSpec client : clients) {
      if (client == null || client.getClientId() == null || !client.getClientId().matches("[A-Za-z0-9._-]{1,128}")
          || !clientIds.add(client.getClientId()) || client.getRegistrationId() == null
          || !registrations.add(client.getRegistrationId())) { throw invalid("客户端标识或注册身份无效或重复"); }
      try { UUID.fromString(client.getRegistrationId()); }
      catch (IllegalArgumentException error) { throw invalid("注册身份无效"); }
      if (!("web".equals(client.getApplicationType()) || "native".equals(client.getApplicationType())) || client.getClientType() == null || client.getOidc() == null || client.getApiAuthorization() == null
          || client.getConsentPolicy() == null || client.getConsentPolicy().getMode() == null) { throw invalid("客户端配置不完整"); }
      if (client.getClientName() != null && (client.getClientName().length() > 200
          || client.getClientName().chars().anyMatch(Character::isISOControl))) { throw invalid("客户端名称无效"); }
      boolean confidential = client.getClientType() == ClientType.CONFIDENTIAL;
      if (confidential ? client.getClientSecretHash() == null
          || !client.getClientSecretHash().matches("\\{bcrypt\\}\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}")
          : client.getClientSecretHash() != null) { throw invalid("客户端类型与密钥配置不一致"); }
      if (client.getRedirectUris() == null || client.getRedirectUris().isEmpty()) { throw invalid("必须注册回调地址"); }
      client.getRedirectUris().forEach(com.ouroboros.ability.auth.oidc.OidcRedirectUris::validate);
      if (client.getIntrospectionResources() == null || (!confidential && !client.getIntrospectionResources().isEmpty())
          || client.getIntrospectionResources().stream().anyMatch(resource -> resource == null
              || !resource.matches("[A-Za-z0-9._:-]{1,128}"))) { throw invalid("资源内省权限无效"); }
      if (!client.effectiveGrantTypes().contains("authorization_code")
          || !Set.of("authorization_code", "refresh_token").containsAll(client.effectiveGrantTypes())
          || !confidential && client.effectiveGrantTypes().contains("refresh_token")) { throw invalid("授权类型配置无效"); }
      if (client.getRegistration() != null && (!Set.of("MANUAL", "DCR_OPEN", "DCR_INITIAL_ACCESS_TOKEN").contains(client.getRegistration().source())
          || client.getRegistration().createdAt() <= 0)) { throw invalid("注册来源配置无效"); }
      var identity = client.getOidc();
      if (identity.getScopes() == null || identity.getPostLogoutRedirectUris() == null
          || !IDENTITY_SCOPES.containsAll(identity.getScopes())
          || (identity.isEnabled() && !identity.getScopes().contains("openid"))
          || (identity.getScopes().contains("offline_access") && (!confidential || !client.effectiveGrantTypes().contains("refresh_token")))) { throw invalid("身份范围配置无效"); }
      identity.getPostLogoutRedirectUris().forEach(com.ouroboros.ability.auth.oidc.OidcRedirectUris::validate);
      var api = client.getApiAuthorization();
      if (api.getAllowedBoundary() == null || api.getAllowedBoundary().getPermissions() == null) { throw invalid("业务范围配置无效"); }
      for (String name : api.getAllowedBoundary().getPermissions()) {
        if (name == null || name.isBlank() || name.contains("*")
            || AuthorityCenter.getAuthority(name).filter(authority -> authority.isDelegable()).isEmpty()) {
          throw invalid("业务范围只能包含明确允许委托的权限项");
        }
      }
      if (!identity.isEnabled() && !api.isEnabled()) { throw invalid("至少启用一种接入能力"); }
    }
    return List.copyOf(clients);
  }

  public static String businessScope(String authority) {
    return "authority:" + Base64.getUrlEncoder().withoutPadding().encodeToString(authority.getBytes(StandardCharsets.UTF_8));
  }

  private RegisteredClient registeredClient(OidcClientSpec spec) {
    var settings = OidcRuntimeSettings.current(configuration);
    var client = RegisteredClient.withId(spec.getRegistrationId()).clientId(spec.getClientId())
        .clientName(spec.getClientName() == null ? spec.getClientId() : spec.getClientName())
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .clientSettings(ClientSettings.builder().requireProofKey(true)
            .requireAuthorizationConsent(spec.getConsentPolicy().getMode() == OidcClientSpec.ConsentMode.EXPLICIT).build())
        .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(settings.accessTokenTimeToLive())
            .refreshTokenTimeToLive(settings.refreshTokenTimeToLive()).reuseRefreshTokens(false).build());
    if (spec.getClientType() == ClientType.PUBLIC) { client.clientAuthenticationMethod(ClientAuthenticationMethod.NONE); }
    else {
      client.clientSecret(spec.getClientSecretHash()).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
      if (spec.effectiveGrantTypes().contains("refresh_token")) { client.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN); }
    }
    spec.getRedirectUris().forEach(client::redirectUri);
    if (spec.getOidc().isEnabled()) {
      spec.getOidc().getScopes().forEach(client::scope);
      spec.getOidc().getPostLogoutRedirectUris().forEach(client::postLogoutRedirectUri);
    }
    if (spec.getApiAuthorization().isEnabled()) {
      spec.getApiAuthorization().getAllowedBoundary().getPermissions().stream()
          .map(ConfigurationRegisteredClientRepository::businessScope).forEach(client::scope);
    }
    return client.build();
  }

  private static ValidationException invalid(String message) {
    return new ValidationException("[AUTH_CLIENT_INVALID] " + message);
  }
}
