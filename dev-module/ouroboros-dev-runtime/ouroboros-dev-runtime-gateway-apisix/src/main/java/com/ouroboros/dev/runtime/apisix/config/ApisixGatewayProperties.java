package com.ouroboros.dev.runtime.apisix.config;

public class ApisixGatewayProperties {
  private String baseUrl;
  private String adminKey;
  private String routeIdPrefix;
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
