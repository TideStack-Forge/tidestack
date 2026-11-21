package com.ouroboros.dev.app.model;

public class DevKeyPluginConfigDTO {
  private int schemaVersion = 1;
  private String developmentServerUrl;
  private String appName;
  private String devKey;

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getDevelopmentServerUrl() {
    return developmentServerUrl;
  }

  public void setDevelopmentServerUrl(String developmentServerUrl) {
    this.developmentServerUrl = developmentServerUrl;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getDevKey() {
    return devKey;
  }

  public void setDevKey(String devKey) {
    this.devKey = devKey;
  }
}
