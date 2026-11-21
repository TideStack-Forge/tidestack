package com.ouroboros.security.externalauth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for external authentication.
 */
@ConfigurationProperties(prefix = "ouroboros.security.external-auth")
public class ExternalAuthProperties {

  private String publicBaseUrl;
  private List<LdapProviderSpec> ldap = new ArrayList<>();
  private List<OAuth2ProviderSpec> oauth2 = new ArrayList<>();
  private List<CasProviderSpec> cas = new ArrayList<>();
  private BindingRateLimitConfig bindingRateLimit = new BindingRateLimitConfig();
  private ExternalCallConfig externalCall = new ExternalCallConfig();
  private JwksCacheConfig jwksCache = new JwksCacheConfig();

  public String getPublicBaseUrl() { return publicBaseUrl; }
  public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

  public List<LdapProviderSpec> getLdap() { return ldap; }
  public void setLdap(List<LdapProviderSpec> ldap) { this.ldap = ldap; }

  public List<OAuth2ProviderSpec> getOauth2() { return oauth2; }
  public void setOauth2(List<OAuth2ProviderSpec> oauth2) { this.oauth2 = oauth2; }

  public List<CasProviderSpec> getCas() { return cas; }
  public void setCas(List<CasProviderSpec> cas) { this.cas = cas; }

  public BindingRateLimitConfig getBindingRateLimit() { return bindingRateLimit; }
  public void setBindingRateLimit(BindingRateLimitConfig bindingRateLimit) { this.bindingRateLimit = bindingRateLimit; }

  public ExternalCallConfig getExternalCall() { return externalCall; }
  public void setExternalCall(ExternalCallConfig externalCall) { this.externalCall = externalCall; }

  public JwksCacheConfig getJwksCache() { return jwksCache; }
  public void setJwksCache(JwksCacheConfig jwksCache) { this.jwksCache = jwksCache; }

  public static class BindingRateLimitConfig {
    private String byExternalId = "5/10m";
    private String byUsername = "10/10m";
    private String bySourceIp = "30/10m";
    private boolean sourceIpEnabled = true;
    private Map<String, ProviderRateLimitOverrideConfig> providers = new HashMap<>();

    public String getByExternalId() { return byExternalId; }
    public void setByExternalId(String byExternalId) { this.byExternalId = byExternalId; }

    public String getByUsername() { return byUsername; }
    public void setByUsername(String byUsername) { this.byUsername = byUsername; }

    public String getBySourceIp() { return bySourceIp; }
    public void setBySourceIp(String bySourceIp) { this.bySourceIp = bySourceIp; }

    public boolean isSourceIpEnabled() { return sourceIpEnabled; }
    public void setSourceIpEnabled(boolean sourceIpEnabled) { this.sourceIpEnabled = sourceIpEnabled; }

    public Map<String, ProviderRateLimitOverrideConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderRateLimitOverrideConfig> providers) { this.providers = providers; }
  }

  public static class ProviderRateLimitOverrideConfig {
    private String byExternalId;
    private String byUsername;
    private String bySourceIp;
    private Boolean sourceIpEnabled;

    public String getByExternalId() { return byExternalId; }
    public void setByExternalId(String byExternalId) { this.byExternalId = byExternalId; }

    public String getByUsername() { return byUsername; }
    public void setByUsername(String byUsername) { this.byUsername = byUsername; }

    public String getBySourceIp() { return bySourceIp; }
    public void setBySourceIp(String bySourceIp) { this.bySourceIp = bySourceIp; }

    public Boolean getSourceIpEnabled() { return sourceIpEnabled; }
    public void setSourceIpEnabled(Boolean sourceIpEnabled) { this.sourceIpEnabled = sourceIpEnabled; }
  }

  public static class ExternalCallConfig {
    private TimeoutConfig oauth2 = new TimeoutConfig();
    private TimeoutConfig cas = new TimeoutConfig();
    private TimeoutConfig ldap = new TimeoutConfig();

    public TimeoutConfig getOauth2() { return oauth2; }
    public void setOauth2(TimeoutConfig oauth2) { this.oauth2 = oauth2; }

    public TimeoutConfig getCas() { return cas; }
    public void setCas(TimeoutConfig cas) { this.cas = cas; }

    public TimeoutConfig getLdap() { return ldap; }
    public void setLdap(TimeoutConfig ldap) { this.ldap = ldap; }
  }

  public static class TimeoutConfig {
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
  }

  public static class JwksCacheConfig {
    private Duration ttl = Duration.ofMinutes(5);

    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
  }
}
