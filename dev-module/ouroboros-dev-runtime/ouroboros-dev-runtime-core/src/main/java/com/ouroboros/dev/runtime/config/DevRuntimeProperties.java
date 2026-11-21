package com.ouroboros.dev.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;

@ConfigurationProperties(prefix = "ouroboros.dev.runtime")
public class DevRuntimeProperties {
  private BackendType defaultBackend = BackendType.DOCKER;
  private DebugProperties debug = new DebugProperties();
  private GatewayProperties gateway = new GatewayProperties();

  public BackendType getDefaultBackend() {
    return defaultBackend;
  }

  public void setDefaultBackend(BackendType defaultBackend) {
    this.defaultBackend = defaultBackend;
  }

  public DebugProperties getDebug() {
    return debug;
  }

  public void setDebug(DebugProperties debug) {
    this.debug = debug == null ? new DebugProperties() : debug;
  }

  public GatewayProperties getGateway() {
    return gateway;
  }

  public void setGateway(GatewayProperties gateway) {
    this.gateway = gateway == null ? new GatewayProperties() : gateway;
  }

  public static class DebugProperties {
    private Integer maxLifetimeSeconds = Integer.valueOf(1800);
    private Integer idleTimeoutSeconds = Integer.valueOf(600);
    private String brokerHost = "127.0.0.1";
    private DebugSessionBindingScope bindScope = DebugSessionBindingScope.LOOPBACK_ONLY;
    private Boolean loopbackOnly = Boolean.TRUE;

    public Integer getMaxLifetimeSeconds() {
      return maxLifetimeSeconds;
    }

    public void setMaxLifetimeSeconds(Integer maxLifetimeSeconds) {
      this.maxLifetimeSeconds = maxLifetimeSeconds;
    }

    public Integer getIdleTimeoutSeconds() {
      return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(Integer idleTimeoutSeconds) {
      this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public String getBrokerHost() {
      return brokerHost;
    }

    public void setBrokerHost(String brokerHost) {
      this.brokerHost = brokerHost;
    }

    public DebugSessionBindingScope getBindScope() {
      return bindScope;
    }

    public void setBindScope(DebugSessionBindingScope bindScope) {
      this.bindScope = bindScope;
    }

    public Boolean getLoopbackOnly() {
      return loopbackOnly;
    }

    public void setLoopbackOnly(Boolean loopbackOnly) {
      this.loopbackOnly = loopbackOnly;
    }
  }

  public static class GatewayProperties {
    private Boolean enabled = Boolean.FALSE;
    private String provider = "";
    private String stableDomainHostSuffix = "";
    private ApisixProperties apisix = new ApisixProperties();

    public Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
    }

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getStableDomainHostSuffix() {
      return stableDomainHostSuffix;
    }

    public void setStableDomainHostSuffix(String stableDomainHostSuffix) {
      this.stableDomainHostSuffix = stableDomainHostSuffix;
    }

    public ApisixProperties getApisix() {
      return apisix;
    }

    public void setApisix(ApisixProperties apisix) {
      this.apisix = apisix == null ? new ApisixProperties() : apisix;
    }
  }

  public static class ApisixProperties {
    private String baseUrl = "";
    private String adminKey = "";
    private String routeIdPrefix = "";
    private Integer connectTimeoutMillis = Integer.valueOf(2000);
    private Integer readTimeoutMillis = Integer.valueOf(5000);

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getAdminKey() {
      return adminKey;
    }

    public void setAdminKey(String adminKey) {
      this.adminKey = adminKey;
    }

    public String getRouteIdPrefix() {
      return routeIdPrefix;
    }

    public void setRouteIdPrefix(String routeIdPrefix) {
      this.routeIdPrefix = routeIdPrefix;
    }

    public Integer getConnectTimeoutMillis() {
      return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(Integer connectTimeoutMillis) {
      this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public Integer getReadTimeoutMillis() {
      return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(Integer readTimeoutMillis) {
      this.readTimeoutMillis = readTimeoutMillis;
    }
  }
}
