package com.ouroboros.security.externalauth;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.externalauth.cas.CasHealthChecker;
import com.ouroboros.security.externalauth.cas.CasHttpClient;
import com.ouroboros.security.externalauth.cas.CasProvider;
import com.ouroboros.security.externalauth.config.CasProviderSpec;
import com.ouroboros.security.externalauth.config.ExternalAuthProviderSpecSource;
import com.ouroboros.security.externalauth.config.LdapProviderSpec;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.security.externalauth.ldap.LdapAuthProvider;
import com.ouroboros.security.externalauth.ldap.LdapHealthChecker;
import com.ouroboros.security.externalauth.oauth2.JwksKeyResolver;
import com.ouroboros.security.externalauth.oauth2.OAuth2HealthChecker;
import com.ouroboros.security.externalauth.oauth2.OAuth2HttpClient;
import com.ouroboros.security.externalauth.oauth2.OAuth2Provider;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;

/**
 * Builds and manages runtime ExternalAuthProvider instances from ProviderSpec configuration.
 */
@Component
public class ExternalAuthProviderFactory {

  private static final Logger logger = LoggerFactory.getLogger(ExternalAuthProviderFactory.class);
  private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
  private static final int DEFAULT_READ_TIMEOUT_MS = 10000;
  private static final long DEFAULT_JWKS_CACHE_TTL_MS = Duration.ofMinutes(5).toMillis();
  private static final Set<String> BUILT_IN_PROVIDER_TYPES = Set.of("ldap", "oauth2", "cas");

  @Autowired
  private ExternalAuthProviderSpecSource specSource;

  @Autowired
  private CompositeSecretResolver secretResolver;

  @Autowired
  private CacheManager cacheManager;

  @Autowired
  private com.ouroboros.security.externalauth.config.ExternalAuthConfiguration externalAuthConfiguration;

  private String loadedRevision;
  private boolean initialized;

  @Autowired(required = false)
  private EnterpriseAuthProfileResolver enterpriseAuthProfileResolver;

  @Autowired(required = false)
  private List<ExternalAuthProviderBuilder> providerBuilders = List.of();

  private ProviderCatalog catalog = new ProviderCatalog(Map.of(), Map.of(), Map.of(), Set.of(), null);
  private final Map<String, ExternalAuthProviderBuilder> providerBuildersByType = new LinkedHashMap<>();
  /** JWKS 解析器跨 provider 实例复用（键为 HTTP 超时 + 缓存 TTL）。 */
  private final Map<String, JwksKeyResolver> jwksResolvers = new java.util.concurrent.ConcurrentHashMap<>();

  public synchronized void init() {
    initializeProviderBuilders();
    for (int attempt = 0; attempt < 3; attempt++) {
      String revision = specSource.getRevision().orElse(null);
      var properties = externalAuthConfiguration.get();
      List<ProviderSpec> specs = specSource.getProviderSpecs();
      // Validate/build in isolation, then publish the whole catalog under the factory lock.
      // The second revision read rejects a concurrent configuration update during construction.
      var replacement = buildCatalog(properties, specs, revision);
      if (java.util.Objects.equals(revision, specSource.getRevision().orElse(null))) {
        catalog = replacement;
        loadedRevision = revision;
        initialized = true;
        return;
      }
    }
    throw new com.ouroboros.exception.InternalException("外部认证配置持续变化，请重试");
  }

  private ProviderCatalog buildCatalog(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties,
      List<ProviderSpec> specs, String revision) {
    ExternalAuthSpecValidator.validate(properties, specs);
    var providers = new HashMap<String, ExternalAuthProvider>();
    var healthCheckers = new HashMap<String, ProviderHealthChecker>();
    var failures = new HashMap<String, String>();
    var keys = new HashSet<String>();
    for (ProviderSpec spec : specs) {
      keys.add(spec.getKey());
      spec.setConfigurationRevision(revision);
      if (!spec.isEnabled()) { continue; }
      ExternalAuthProvider provider = buildProvider(spec, properties, failures);
      if (provider != null) {
        providers.put(spec.getKey(), provider);
        healthCheckers.put(spec.getKey(), buildHealthChecker(spec));
      }
    }
    return new ProviderCatalog(Map.copyOf(providers), Map.copyOf(healthCheckers), Map.copyOf(failures),
        Set.copyOf(keys), properties);
  }

  private record ProviderCatalog(Map<String, ExternalAuthProvider> providers,
      Map<String, ProviderHealthChecker> healthCheckers, Map<String, String> unavailableReasons,
      Set<String> keys, com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {}

  private void refreshIfChanged() {
    String revision = specSource.getRevision().orElse(null);
    if (!initialized || !java.util.Objects.equals(revision, loadedRevision)) {
      init();
    }
  }

  private ExternalAuthProvider buildProvider(ProviderSpec spec,
      com.ouroboros.security.externalauth.config.ExternalAuthProperties properties, Map<String, String> failures) {
    if ("ldap".equals(spec.getType()) && spec instanceof LdapProviderSpec) {
      LdapProviderSpec ldapSpec = (LdapProviderSpec) spec;
      ExternalAuthSpecValidator.validateLdapSpec(ldapSpec);
      if (isRefResolutionFailed(ldapSpec.getSearchBindPasswordRef())) {
        markUnavailable(spec.getKey(), "searchBindPasswordRef cannot be resolved", failures);
        return null;
      }
      return new LdapAuthProvider(
          ldapSpec,
          secretResolver,
          getLdapConnectTimeoutMs(properties),
          getLdapReadTimeoutMs(properties)
      );
    }
    if ("oauth2".equals(spec.getType()) && spec instanceof OAuth2ProviderSpec) {
      OAuth2ProviderSpec oauth2Spec = (OAuth2ProviderSpec) spec;
      ExternalAuthSpecValidator.validateOAuth2Spec(oauth2Spec);
      String resolvedClientId = oauth2Spec.getClientId();
      Optional<String> resolvedSecret;
      if (hasText(oauth2Spec.getClientIdRef()) && hasText(oauth2Spec.getClientSecretRef())) {
        Optional<Map<String, String>> resolvedClient = secretResolver.resolveProjections(Map.of(
            "clientId", oauth2Spec.getClientIdRef(),
            "clientSecret", oauth2Spec.getClientSecretRef()));
        if (resolvedClient.isEmpty()) {
          markUnavailable(spec.getKey(), "client credential references cannot be resolved",
              failures);
          return null;
        }
        resolvedClientId = resolvedClient.get().get("clientId");
        resolvedSecret = Optional.of(resolvedClient.get().get("clientSecret"));
      } else if (hasText(oauth2Spec.getClientIdRef())) {
        Optional<String> referencedClientId = secretResolver.resolve(oauth2Spec.getClientIdRef());
        if (referencedClientId.isEmpty()) {
          markUnavailable(spec.getKey(), "clientIdRef cannot be resolved", failures);
          return null;
        }
        resolvedClientId = referencedClientId.get();
        resolvedSecret = secretResolver.resolveWithFallback(null, oauth2Spec.getClientSecret());
      } else if (hasText(oauth2Spec.getClientSecretRef())) {
        resolvedSecret = secretResolver.resolve(oauth2Spec.getClientSecretRef());
        if (!resolvedSecret.isPresent()) {
          markUnavailable(spec.getKey(), "clientSecretRef cannot be resolved", failures);
          return null;
        }
      } else {
        resolvedSecret = secretResolver.resolveWithFallback(null, oauth2Spec.getClientSecret());
      }
      OAuth2HttpClient httpClient = new OAuth2HttpClient(getOauth2ConnectTimeoutMs(properties), getOauth2ReadTimeoutMs(properties), () -> {
        if (oauth2Spec.isConnectionScoped()) {
          if (enterpriseAuthProfileResolver == null) { throw new com.ouroboros.exception.BusinessException("企业认证连接不可用"); }
          enterpriseAuthProfileResolver.beforeRequest(oauth2Spec.getKey(), oauth2Spec.getConnectionProfileRevision());
        }
      });
      JwksKeyResolver jwksKeyResolver = sharedJwksKeyResolver(properties);
      return new OAuth2Provider(
          oauth2Spec,
          resolvedSecret.orElse(null),
          httpClient,
          jwksKeyResolver,
          properties.getPublicBaseUrl(),
          resolvedClientId
      );
    }
    if ("cas".equals(spec.getType()) && spec instanceof CasProviderSpec) {
      CasProviderSpec casSpec = (CasProviderSpec) spec;
      ExternalAuthSpecValidator.validateCasSpec(casSpec);
      CasHttpClient casHttpClient = new CasHttpClient(getCasConnectTimeoutMs(properties), getCasReadTimeoutMs(properties));
      return new CasProvider(casSpec, properties.getPublicBaseUrl(), cacheManager, casHttpClient);
    }
    ExternalAuthProviderBuilder builder = providerBuildersByType.get(spec.getType());
    if (builder != null) {
      Optional<ExternalAuthProvider> provider = builder.build(spec, buildContext(properties));
      if (provider.isEmpty()) {
        markUnavailable(spec.getKey(), "provider builder returned no provider", failures);
        return null;
      }
      if (!spec.getKey().equals(provider.get().getProviderKey()) || provider.get().getSpec() != spec) {
        throw new IllegalStateException("Provider builder returned a provider for another specification: "
            + spec.getKey());
      }
      return provider.get();
    }
    logger.warn("Unknown provider type: {} for key: {}", spec.getType(), spec.getKey());
    return null;
  }

  private void initializeProviderBuilders() {
    providerBuildersByType.clear();
    for (ExternalAuthProviderBuilder builder : providerBuilders) {
      if (builder == null || !hasText(builder.type())) {
        throw new IllegalStateException("External auth provider builder has null or empty type");
      }
      String type = builder.type();
      if (BUILT_IN_PROVIDER_TYPES.contains(type)) {
        throw new IllegalStateException("External auth provider builder conflicts with built-in type: " + type);
      }
      if (providerBuildersByType.putIfAbsent(type, builder) != null) {
        throw new IllegalStateException("Duplicate external auth provider builder type: " + type);
      }
    }
  }

  private ExternalAuthProviderBuildContext buildContext(
      com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    return new ExternalAuthProviderBuildContext() {
      @Override
      public Optional<String> resolveSecret(String reference) {
        return secretResolver.resolve(reference);
      }

      @Override
      public Optional<Map<String, String>> resolveSecretProjections(Map<String, String> references) {
        return secretResolver.resolveProjections(references);
      }

      @Override
      public String publicBaseUrl() {
        return properties.getPublicBaseUrl();
      }
    };
  }

  /**
   * JWKS 解析器按"JWKS 地址 + 缓存 TTL"复用。
   *
   * <p>连接作用域的 profile 每次登录都会重建 provider（见 {@link #resolveConnectionProvider} 的说明），
   * 若同时重建 {@link JwksKeyResolver}，它内部的 JWKS 缓存会被逐请求清空，等于每次登录都取一次
   * JWKS。这里把解析器独立于 provider 实例缓存，保留跨请求的密钥缓存。
   */
  private JwksKeyResolver sharedJwksKeyResolver(
      com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    long ttl = getJwksCacheTtlMillis(properties);
    String key = getOauth2ConnectTimeoutMs(properties) + "|" + getOauth2ReadTimeoutMs(properties) + "|" + ttl;
    return jwksResolvers.computeIfAbsent(key,
        ignored -> new JwksKeyResolver(new OAuth2HttpClient(
            getOauth2ConnectTimeoutMs(properties), getOauth2ReadTimeoutMs(properties)), ttl));
  }

  private void markUnavailable(String providerKey, String reason, Map<String, String> failures) {
    if (failures != null) { failures.put(providerKey, reason); }
    logger.warn("External auth provider unavailable: providerKey={}, reason={}", providerKey, reason);
  }

  private boolean isRefResolutionFailed(String ref) {
    return hasText(ref) && !secretResolver.resolve(ref).isPresent();
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private ProviderHealthChecker buildHealthChecker(ProviderSpec spec) {
    if ("ldap".equals(spec.getType()) && spec instanceof LdapProviderSpec) {
      return new LdapHealthChecker((LdapProviderSpec) spec);
    }
    if ("oauth2".equals(spec.getType()) && spec instanceof OAuth2ProviderSpec) {
      return new OAuth2HealthChecker((OAuth2ProviderSpec) spec);
    }
    if ("cas".equals(spec.getType()) && spec instanceof CasProviderSpec) {
      return new CasHealthChecker((CasProviderSpec) spec);
    }
    // Fallback: always healthy
    return new ProviderHealthChecker() {
      @Override
      public boolean isHealthy() {
        return true;
      }
    };
  }

  private int getOauth2ConnectTimeoutMs(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    return toTimeoutMillis(getExternalCallConfig(properties).getOauth2().getConnectTimeout(), DEFAULT_CONNECT_TIMEOUT_MS);
  }

  private int getOauth2ReadTimeoutMs(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    return toTimeoutMillis(getExternalCallConfig(properties).getOauth2().getReadTimeout(), DEFAULT_READ_TIMEOUT_MS);
  }

  private int getCasConnectTimeoutMs(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    return toTimeoutMillis(getExternalCallConfig(properties).getCas().getConnectTimeout(), DEFAULT_CONNECT_TIMEOUT_MS);
  }

  private int getCasReadTimeoutMs(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    return toTimeoutMillis(getExternalCallConfig(properties).getCas().getReadTimeout(), DEFAULT_READ_TIMEOUT_MS);
  }

  private int getLdapConnectTimeoutMs(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    return toTimeoutMillis(getExternalCallConfig(properties).getLdap().getConnectTimeout(), DEFAULT_CONNECT_TIMEOUT_MS);
  }

  private int getLdapReadTimeoutMs(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    return toTimeoutMillis(getExternalCallConfig(properties).getLdap().getReadTimeout(), DEFAULT_READ_TIMEOUT_MS);
  }

  private long getJwksCacheTtlMillis(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    com.ouroboros.security.externalauth.config.ExternalAuthProperties.JwksCacheConfig jwksCacheConfig =
        properties.getJwksCache();
    Duration ttl = jwksCacheConfig != null ? jwksCacheConfig.getTtl() : null;
    long ttlMs = ttl != null ? ttl.toMillis() : DEFAULT_JWKS_CACHE_TTL_MS;
    return ttlMs > 0 ? ttlMs : DEFAULT_JWKS_CACHE_TTL_MS;
  }

  private com.ouroboros.security.externalauth.config.ExternalAuthProperties.ExternalCallConfig getExternalCallConfig(com.ouroboros.security.externalauth.config.ExternalAuthProperties properties) {
    com.ouroboros.security.externalauth.config.ExternalAuthProperties.ExternalCallConfig config =
        properties.getExternalCall();
    if (config == null) {
      return new com.ouroboros.security.externalauth.config.ExternalAuthProperties.ExternalCallConfig();
    }
    if (config.getOauth2() == null) {
      config.setOauth2(new com.ouroboros.security.externalauth.config.ExternalAuthProperties.TimeoutConfig());
    }
    if (config.getCas() == null) {
      config.setCas(new com.ouroboros.security.externalauth.config.ExternalAuthProperties.TimeoutConfig());
    }
    if (config.getLdap() == null) {
      config.setLdap(new com.ouroboros.security.externalauth.config.ExternalAuthProperties.TimeoutConfig());
    }
    return config;
  }

  private int toTimeoutMillis(Duration duration, int defaultMillis) {
    long millis = duration != null ? duration.toMillis() : defaultMillis;
    if (millis <= 0) {
      millis = defaultMillis;
    }
    if (millis > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) millis;
  }

  /**
   * Get all registered (enabled + successfully built) providers.
   */
  public synchronized Collection<ExternalAuthProvider> getAllProviders() {
    refreshIfChanged();
    var providers = new java.util.LinkedHashMap<>(catalog.providers());
    if (enterpriseAuthProfileResolver != null) {
      for (String connectionId : enterpriseAuthProfileResolver.connectionIds()) {
        providers.remove(connectionId);
        getOAuth2FlowProvider(connectionId).ifPresent(provider -> providers.put(connectionId, provider));
      }
    }
    return List.copyOf(providers.values());
  }

  /**
   * Get a DirectAuthProvider by key.
   */
  public synchronized Optional<DirectAuthProvider> getDirectProvider(String key) {
    refreshIfChanged();
    ExternalAuthProvider provider = catalog.providers().get(key);
    if (provider instanceof DirectAuthProvider) {
      return Optional.of((DirectAuthProvider) provider);
    }
    return Optional.empty();
  }

  /**
   * Get a RedirectAuthProvider by key.
   */
  public Optional<RedirectAuthProvider> getRedirectProvider(String key) {
    return getRedirectProvider(key, null);
  }

  /**
   * Resolves providers accepted by the OAuth2-shaped browser flow: static OAuth2 providers and
   * connection-scoped provider bridges only.
   */
  public synchronized Optional<RedirectAuthProvider> getOAuth2FlowProvider(String key) {
    return getRedirectProvider(key).filter(provider -> {
      ProviderSpec spec = provider.getSpec();
      if (spec == null) {
        return false;
      }
      return "oauth2".equals(spec.getType())
          || (spec.isConnectionScoped() && !catalog.keys().contains(key));
    });
  }

  public synchronized Optional<RedirectAuthProvider> getRedirectProvider(String key, String expectedType) {
    refreshIfChanged();
    ExternalAuthProvider staticProvider = catalog.providers().get(key);
    ConnectionProviderResolution connectionResolution = resolveConnectionProvider(key);
    if (catalog.keys().contains(key) && connectionResolution.connectionClaimed()) {
      logger.warn("OAuth2 provider key is ambiguous between static and connection profiles: {}", key);
      return Optional.empty();
    }
    ExternalAuthProvider provider = staticProvider != null
        ? staticProvider
        : connectionResolution.provider();
    if (!(provider instanceof RedirectAuthProvider)) {
      return Optional.empty();
    }
    if (hasText(expectedType) && !expectedType.equals(provider.getSpec().getType())) {
      return Optional.empty();
    }
    return Optional.of((RedirectAuthProvider) provider);
  }

  /**
   * Get any ExternalAuthProvider by key.
   */
  public synchronized Optional<ExternalAuthProvider> getProvider(String key) {
    refreshIfChanged();
    return Optional.ofNullable(catalog.providers().get(key));
  }

  /**
   * Resolves a connection-scoped profile on demand. It is deliberately not cached: a published
   * connection update must take effect before the next login without relying on a second cache
   * invalidation channel.
   */
  private ConnectionProviderResolution resolveConnectionProvider(String connectionId) {
    if (!hasText(connectionId) || enterpriseAuthProfileResolver == null) {
      return ConnectionProviderResolution.notClaimed();
    }
    EnterpriseAuthProfileResolver.Resolution resolution;
    try {
      resolution = enterpriseAuthProfileResolver.resolve(connectionId);
    } catch (RuntimeException ex) {
      logger.warn("Connection auth profile resolution failed: connectionId={}, errorType={}",
          connectionId, ex.getClass().getSimpleName());
      return ConnectionProviderResolution.claimedWithoutProvider();
    }
    if (resolution == null) {
      logger.warn("Connection auth profile resolver returned null: connectionId={}", connectionId);
      return ConnectionProviderResolution.claimedWithoutProvider();
    }
    if (!resolution.connectionClaimed()) {
      return ConnectionProviderResolution.notClaimed();
    }
    if (resolution.profile().isEmpty()) {
      return ConnectionProviderResolution.claimedWithoutProvider();
    }
    ProviderSpec spec = resolution.profile().get();
    if (!connectionId.equals(spec.getKey())
        || !hasText(spec.getType())
        || !spec.isEnabled()
        || !spec.isConnectionScoped()
        || !hasText(spec.getConnectionProfileRevision())) {
      logger.warn("Rejected invalid connection auth profile: {}", connectionId);
      return ConnectionProviderResolution.claimedWithoutProvider();
    }
    try {
      spec.setConfigurationRevision(loadedRevision);
      ExternalAuthProvider provider = buildProvider(spec, catalog.properties(), null);
      if (provider == null) {
        return ConnectionProviderResolution.claimedWithoutProvider();
      }
      return ConnectionProviderResolution.claimed(provider);
    } catch (RuntimeException ex) {
      logger.warn("Connection auth provider unavailable: connectionId={}, errorType={}",
          connectionId, ex.getClass().getSimpleName());
      return ConnectionProviderResolution.claimedWithoutProvider();
    }
  }

  private record ConnectionProviderResolution(
      boolean connectionClaimed, ExternalAuthProvider provider) {

    private static ConnectionProviderResolution notClaimed() {
      return new ConnectionProviderResolution(false, null);
    }

    private static ConnectionProviderResolution claimedWithoutProvider() {
      return new ConnectionProviderResolution(true, null);
    }

    private static ConnectionProviderResolution claimed(ExternalAuthProvider provider) {
      return new ConnectionProviderResolution(true, provider);
    }
  }

  /**
   * Get the health checker for a provider by key.
   */
  public synchronized ProviderHealthChecker getHealthChecker(String providerKey) {
    refreshIfChanged();
    return catalog.healthCheckers().get(providerKey);
  }

  public synchronized boolean isUnavailableAtStartup(String providerKey) {
    refreshIfChanged();
    return catalog.unavailableReasons().containsKey(providerKey);
  }

  public synchronized Optional<String> getStartupUnavailableReason(String providerKey) {
    refreshIfChanged();
    return Optional.ofNullable(catalog.unavailableReasons().get(providerKey));
  }
}
