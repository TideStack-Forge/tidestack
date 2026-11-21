package com.ouroboros.statistics.reporting.dev.model;

public class DatasetDataSourceRequest {
  private String datasource;
  private String envName;
  private DatasetTestConnection testConnection;

  public String getDatasource() { return datasource; }
  public void setDatasource(String datasource) { this.datasource = datasource; }
  public String getEnvName() { return envName; }
  public void setEnvName(String envName) { this.envName = envName; }
  public DatasetTestConnection getTestConnection() { return testConnection; }
  public void setTestConnection(DatasetTestConnection testConnection) { this.testConnection = testConnection; }
}
