package com.ouroboros.statistics.reporting.dev.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

@Model(rawName = "t_statistics_dataset_dev_config", label = "统计数据集开发态配置")
@UniqueConstraint(name = "uk_statistics_dev_config_app_dataset", fields = {"appName", "datasetName"})
public class DatasetDevConfigModel {
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String appName;

  @Field
  private String datasetName;

  @Field
  private String datasource;

  @Field(type = "json")
  private Map<String, Object> parameters = new LinkedHashMap<String, Object>();

  @Field(type = "json", nullable = true)
  private DatasetTestConnection testConnection;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public void setDatasetName(String datasetName) {
    this.datasetName = datasetName;
  }

  public String getDatasource() {
    return datasource;
  }

  public void setDatasource(String datasource) {
    this.datasource = datasource;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public void setParameters(Map<String, Object> parameters) {
    this.parameters = parameters == null ? new LinkedHashMap<String, Object>() : parameters;
  }

  public DatasetTestConnection getTestConnection() {
    return testConnection;
  }

  public void setTestConnection(DatasetTestConnection testConnection) {
    this.testConnection = testConnection;
  }
}
