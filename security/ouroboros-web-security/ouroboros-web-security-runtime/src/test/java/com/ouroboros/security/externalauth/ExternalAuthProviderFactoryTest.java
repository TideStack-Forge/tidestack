package com.ouroboros.security.externalauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.externalauth.config.ExternalAuthProperties;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;
import com.ouroboros.security.externalauth.config.ExternalAuthProviderSpecSource;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.externalauth.secret.SecretResolver;

@ExtendWith(MockitoExtension.class)
class ExternalAuthProviderFactoryTest {

  @Mock
  private EnterpriseAuthProfileResolver profileResolver;

  @Mock
  private ExternalAuthProviderSpecSource specSource;

  @Mock
  private CacheManager cacheManager;

  private ExternalAuthProviderFactory factory;

  @BeforeEach
  void setUp() {
    factory = new ExternalAuthProviderFactory();
    ExternalAuthProperties properties = new ExternalAuthProperties();
    properties.setPublicBaseUrl("https://platform.example.com");
    ReflectionTestUtils.setField(factory, "enterpriseAuthProfileResolver", profileResolver);
    ReflectionTestUtils.setField(factory, "specSource", specSource);
    ReflectionTestUtils.setField(factory, "cacheManager", cacheManager);
    ReflectionTestUtils.setField(factory, "externalAuthConfiguration", (ExternalAuthConfiguration) () -> properties);
    ReflectionTestUtils.setField(factory, "secretResolver", new CompositeSecretResolver(List.of(
        new SecretResolver() {
          @Override
          public boolean supports(String scheme) {
            return "test".equals(scheme);
          }

          @Override
          public Optional<String> resolve(String reference) {
            return Optional.of("client-secret");
          }
        },
        new SecretResolver() {
          @Override
          public boolean supports(String scheme) {
            return "json".equals(scheme);
          }

          @Override
          public Optional<String> resolve(String reference) {
            return Optional.of("{\"appId\":\"cli_test\",\"appSecret\":\"client-secret\"}");
          }
        })));
  }

  @Test
  void listsCurrentEnterpriseProvidersWithoutCachingThemOrAdvertisingAmbiguousKeys() {
    var staticProfile = connectionProfile("collision");
    staticProfile.setConnectionScoped(false);
    staticProfile.setConnectionProfileRevision(null);
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    when(profileResolver.connectionIds()).thenReturn(List.of("feishu-main", "collision"));
    when(profileResolver.resolve("feishu-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile("feishu-main")));
    when(profileResolver.resolve("collision"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile("collision")));

    org.assertj.core.api.Assertions.assertThat(factory.getAllProviders())
        .extracting(ExternalAuthProvider::getProviderKey).containsExactly("feishu-main");
    assertTrue(factory.getProvider("feishu-main").isEmpty());
    when(profileResolver.resolve("feishu-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimedWithoutProfile());
    assertTrue(factory.getAllProviders().isEmpty());
  }

  @Test
  void resolvesAConnectionScopedOAuth2ProfileOnDemand() {
    when(profileResolver.resolve("feishu-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile("feishu-main")));

    Optional<RedirectAuthProvider> provider = factory.getRedirectProvider("feishu-main", "oauth2");

    assertTrue(provider.isPresent());
    assertTrue(((OAuth2ProviderSpec) provider.get().getSpec()).isConnectionScoped());
    assertTrue(factory.getProvider("feishu-main").isEmpty(),
        "connection-scoped providers must not leak into the static provider catalog");
  }

  @Test
  void resolvesAConnectionScopedProviderThroughItsRegisteredBuilder() {
    ProviderSpec profile = connectionBridgeProfile("wecom-main", "wecom-bridge");
    ReflectionTestUtils.setField(factory, "providerBuilders", List.of(
        new TestRedirectProviderBuilder("wecom-bridge")));
    when(specSource.getProviderSpecs()).thenReturn(List.of());
    factory.init();
    when(profileResolver.resolve("wecom-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(profile));

    Optional<RedirectAuthProvider> provider = factory.getRedirectProvider("wecom-main");

    assertTrue(provider.isPresent());
    assertTrue(provider.get().getSpec() == profile);
    assertTrue(factory.getProvider("wecom-main").isEmpty());
  }

  @Test
  void oauth2FlowAcceptsConnectionBridgeButRejectsStaticNonOAuthRedirectProvider() {
    ProviderSpec staticProfile = connectionBridgeProfile("static-bridge", "test-bridge");
    staticProfile.setConnectionScoped(false);
    ProviderSpec connectionProfile = connectionBridgeProfile("wecom-main", "test-bridge");
    ReflectionTestUtils.setField(factory, "providerBuilders", List.of(
        new TestRedirectProviderBuilder("test-bridge")));
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    factory.init();
    when(profileResolver.resolve("static-bridge"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.notClaimed());
    when(profileResolver.resolve("wecom-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile));

    assertFalse(factory.getOAuth2FlowProvider("static-bridge").isPresent());
    assertTrue(factory.getOAuth2FlowProvider("wecom-main").isPresent());
  }

  @Test
  void rejectsDuplicateExternalProviderBuilderTypesAtStartup() {
    ReflectionTestUtils.setField(factory, "providerBuilders", List.of(
        new TestRedirectProviderBuilder("wecom-bridge"),
        new TestRedirectProviderBuilder("wecom-bridge")));
    assertThrows(IllegalStateException.class, factory::init);
  }

  @Test
  void resolvesClientIdentityAndSecretFromOneStructuredCredentialReference() {
    OAuth2ProviderSpec profile = connectionProfile("feishu-main");
    profile.setClientId(null);
    profile.setClientIdRef("json:feishu-main#/appId");
    profile.setClientSecretRef("json:feishu-main#/appSecret");
    when(profileResolver.resolve("feishu-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(profile));

    Optional<RedirectAuthProvider> first = factory.getRedirectProvider("feishu-main", "oauth2");
    Optional<RedirectAuthProvider> second = factory.getRedirectProvider("feishu-main", "oauth2");

    assertTrue(first.isPresent());
    assertTrue(second.isPresent(), "resolving a profile must not mutate the source profile");
    assertTrue(first.get().buildAuthorizeUrl("/").getAuthorizeUrl().contains("client_id=cli_test"));
    assertTrue(profile.getClientId() == null);
    assertTrue("json:feishu-main#/appId".equals(profile.getClientIdRef()));
  }

  @Test
  void rejectsStartupWhenCallbackFlowHasNoPublicBaseUrl() {
    // 回调地址由 publicBaseUrl 拼接；为空会退化出相对 redirect_uri，必须启动期失败。
    ExternalAuthProperties withoutBaseUrl = new ExternalAuthProperties();
    ReflectionTestUtils.setField(factory, "externalAuthConfiguration", (ExternalAuthConfiguration) () -> withoutBaseUrl);
    when(specSource.getProviderSpecs()).thenReturn(List.of(connectionProfile("feishu-main")));

    assertThrows(IllegalStateException.class, factory::init);
  }

  @Test
  void ldapOnlyConfigurationDoesNotRequirePublicBaseUrl() {
    ExternalAuthProperties withoutBaseUrl = new ExternalAuthProperties();
    ReflectionTestUtils.setField(factory, "externalAuthConfiguration", (ExternalAuthConfiguration) () -> withoutBaseUrl);
    var ldapSpec = new com.ouroboros.security.externalauth.config.LdapProviderSpec();
    ldapSpec.setKey("corp-ldap");
    ldapSpec.setType("ldap");
    ldapSpec.setUrls(List.of("ldaps://ldap.example.com"));
    ldapSpec.setBaseDn("dc=example,dc=com");
    ldapSpec.setExternalIdAttribute("uid");
    when(specSource.getProviderSpecs()).thenReturn(List.of(ldapSpec));

    // LDAP 是直连校验、不产生回调，不应被回调地址校验拦住。
    factory.init();
    assertTrue(factory.getProvider("corp-ldap").isPresent());
  }

  @Test
  void rejectsAProfileWhoseKeyDoesNotMatchTheRequestedConnection() {
    when(profileResolver.resolve("feishu-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile("other-connection")));

    assertFalse(factory.getRedirectProvider("feishu-main", "oauth2").isPresent());
  }

  @Test
  void rejectsAnAmbiguousStaticAndConnectionProviderKey() {
    OAuth2ProviderSpec staticProfile = connectionProfile("shared-key");
    staticProfile.setConnectionScoped(false);
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    factory.init();
    when(profileResolver.resolve("shared-key"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile("shared-key")));

    assertFalse(factory.getRedirectProvider("shared-key", "oauth2").isPresent());
  }

  @Test
  void rejectsAConnectionProfileThatCollidesWithDisabledStaticProvider() {
    OAuth2ProviderSpec staticProfile = connectionProfile("shared-key");
    staticProfile.setConnectionScoped(false);
    staticProfile.setEnabled(false);
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    factory.init();
    when(profileResolver.resolve("shared-key"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile("shared-key")));

    assertFalse(factory.getRedirectProvider("shared-key", "oauth2").isPresent());
  }

  @Test
  void rejectsAConnectionProfileThatCollidesWithUnavailableStaticProvider() {
    OAuth2ProviderSpec staticProfile = connectionProfile("shared-key");
    staticProfile.setConnectionScoped(false);
    staticProfile.setClientSecretRef("missing:client-secret");
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    factory.init();
    when(profileResolver.resolve("shared-key"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile("shared-key")));

    assertFalse(factory.getRedirectProvider("shared-key", "oauth2").isPresent());
  }

  @Test
  void rejectsAConnectionProfileWithAnInlineSecret() {
    OAuth2ProviderSpec profile = connectionProfile("feishu-main");
    profile.setClientSecretRef(null);
    profile.setClientSecret("inline-secret");
    when(profileResolver.resolve("feishu-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(profile));

    assertFalse(factory.getRedirectProvider("feishu-main", "oauth2").isPresent());
  }

  @Test
  void doesNotRecordDynamicCredentialFailureAsAStartupFailure() {
    OAuth2ProviderSpec profile = connectionProfile("feishu-main");
    profile.setClientSecretRef("missing:client-secret");
    when(profileResolver.resolve("feishu-main"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(profile));

    assertFalse(factory.getRedirectProvider("feishu-main", "oauth2").isPresent());
    assertFalse(factory.isUnavailableAtStartup("feishu-main"));
  }

  @Test
  void doesNotFallBackToStaticProviderWhenConnectionCredentialCannotBeResolved() {
    OAuth2ProviderSpec staticProfile = connectionProfile("shared-key");
    staticProfile.setConnectionScoped(false);
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    factory.init();
    OAuth2ProviderSpec connectionProfile = connectionProfile("shared-key");
    connectionProfile.setClientSecretRef("missing:client-secret");
    when(profileResolver.resolve("shared-key"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimed(connectionProfile));

    assertFalse(factory.getRedirectProvider("shared-key", "oauth2").isPresent());
  }

  @Test
  void rejectsOAuth2ProfileWithoutAnIdentitySource() {
    OAuth2ProviderSpec profile = connectionProfile("missing-identity-source");
    profile.setUserInfoUri(null);

    when(specSource.getProviderSpecs()).thenReturn(List.of(profile));

    assertThrows(IllegalStateException.class, factory::init);
  }

  @Test
  void rejectsJwksProfileWithoutAnIssuer() {
    OAuth2ProviderSpec profile = connectionProfile("missing-issuer");
    profile.setUserInfoUri(null);
    profile.setJwksUri("https://accounts.example.com/jwks");

    when(specSource.getProviderSpecs()).thenReturn(List.of(profile));

    assertThrows(IllegalStateException.class, factory::init);
  }

  @Test
  void doesNotFallBackToStaticProviderWhenConnectionIsClaimedWithoutAProfile() {
    OAuth2ProviderSpec staticProfile = connectionProfile("shared-key");
    staticProfile.setConnectionScoped(false);
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    factory.init();
    when(profileResolver.resolve("shared-key"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.claimedWithoutProfile());

    assertFalse(factory.getRedirectProvider("shared-key", "oauth2").isPresent());
  }

  @Test
  void doesNotFallBackToStaticProviderWhenConnectionResolutionFails() {
    OAuth2ProviderSpec staticProfile = connectionProfile("shared-key");
    staticProfile.setConnectionScoped(false);
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    factory.init();
    when(profileResolver.resolve("shared-key"))
        .thenThrow(new IllegalStateException("connection store unavailable"));

    assertFalse(factory.getRedirectProvider("shared-key", "oauth2").isPresent());
  }

  @Test
  void keepsStaticProviderWhenConnectionNamespaceIsNotClaimed() {
    OAuth2ProviderSpec staticProfile = connectionProfile("static-only");
    staticProfile.setConnectionScoped(false);
    when(specSource.getProviderSpecs()).thenReturn(List.of(staticProfile));
    factory.init();
    when(profileResolver.resolve("static-only"))
        .thenReturn(EnterpriseAuthProfileResolver.Resolution.notClaimed());

    assertTrue(factory.getRedirectProvider("static-only", "oauth2").isPresent());
  }

  private OAuth2ProviderSpec connectionProfile(String key) {
    OAuth2ProviderSpec profile = new OAuth2ProviderSpec();
    profile.setKey(key);
    profile.setType("oauth2");
    profile.setDisplayName("Test connection");
    profile.setEnabled(true);
    profile.setConnectionScoped(true);
    profile.setAuthorizationUri("https://accounts.example.com/authorize");
    profile.setTokenUri("https://accounts.example.com/token");
    profile.setUserInfoUri("https://accounts.example.com/userinfo");
    profile.setClientId("client-id");
    profile.setClientSecretRef("test:client-secret");
    profile.setExternalIdField("sub");
    profile.setConnectionProfileRevision("revision-1");
    return profile;
  }

  private ProviderSpec connectionBridgeProfile(String key, String type) {
    ProviderSpec profile = new ProviderSpec();
    profile.setKey(key);
    profile.setType(type);
    profile.setDisplayName("Test bridge");
    profile.setEnabled(true);
    profile.setConnectionScoped(true);
    profile.setConnectionProfileRevision("revision-1");
    return profile;
  }

  private record TestRedirectProviderBuilder(String type)
      implements ExternalAuthProviderBuilder {

    @Override
    public Optional<ExternalAuthProvider> build(
        ProviderSpec spec, ExternalAuthProviderBuildContext context) {
      return Optional.of(new TestRedirectProvider(spec));
    }
  }

  private record TestRedirectProvider(ProviderSpec spec) implements RedirectAuthProvider {

    @Override
    public String getProviderKey() {
      return spec.getKey();
    }

    @Override
    public ProviderSpec getSpec() {
      return spec;
    }

    @Override
    public AuthorizeUrlResult buildAuthorizeUrl(String returnUrl) {
      return AuthorizeUrlResult.of("https://idp.example.com", "state", Map.of());
    }

    @Override
    public io.vavr.control.Either<AuthError, ExternalIdentity> handleCallback(
        CallbackParams params) {
      return io.vavr.control.Either.left(AuthError.invalidCredentials("not used"));
    }

    @Override
    public io.vavr.control.Either<AuthError, ExternalIdentity> handleExchange(
        ExchangeParams params) {
      return io.vavr.control.Either.left(AuthError.invalidCredentials("not used"));
    }
  }
}
