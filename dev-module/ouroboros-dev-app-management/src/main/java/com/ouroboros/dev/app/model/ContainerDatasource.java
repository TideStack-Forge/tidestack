package com.ouroboros.dev.app.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ContainerDatasource {
  String driverClassName;
  String url;
  String username;
  String password;

  public String getDriverClassName() {
    return driverClassName;
  }

  public ContainerDatasource setDriverClassName(String driverClassName) {
    this.driverClassName = driverClassName;
    return this;
  }

  public String getUrl() {
    return url;
  }

  public ContainerDatasource setUrl(String url) {
    this.url = url;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public ContainerDatasource setUsername(String username) {
    this.username = username;
    return this;
  }

  public String getPassword() {
    return password;
  }

  public ContainerDatasource setPassword(String password) {
    this.password = password;
    return this;
  }

  public Map<String, String> asEnvMap() {
    Map<String, String> envMap = new LinkedHashMap<>();
    envMap.put("SPRING_DATASOURCE_TYPE", "com.alibaba.druid.pool.DruidDataSource");
    envMap.put("SPRING_DATASOURCE_DRIVERCLASSNAME", driverClassName);
    envMap.put("SPRING_DATASOURCE_URL", url);
    envMap.put("SPRING_DATASOURCE_USERNAME", username);
    envMap.put("SPRING_DATASOURCE_PASSWORD", password);
    return envMap;
  }
}
