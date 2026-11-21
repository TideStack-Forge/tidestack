package com.ouroboros.statistics.reporting.dev.model;

/** A reference to a persisted connection, never the connection's credentials. */
public class DatasetTestConnection {
  private String kind;
  private String platformDataSourceName;
  private String envName;
  private String runtimeId;

  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }
  public String getPlatformDataSourceName() { return platformDataSourceName; }
  public void setPlatformDataSourceName(String name) { this.platformDataSourceName = name; }
  public String getEnvName() { return envName; }
  public void setEnvName(String envName) { this.envName = envName; }
  public String getRuntimeId() { return runtimeId; }
  public void setRuntimeId(String runtimeId) { this.runtimeId = runtimeId; }
}
