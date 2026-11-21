package com.ouroboros.dev.app.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author liansz
 */
public class AppContainerRequest {

  /**
   * 应用名称
   */
  private String appName;

  /**
   * 镜像
   */
  private String image;

  /**
   * 运行时后端类型
   */
  private String backendType;

  /**
   * 暴露策略
   */
  private String exposureStrategy;

  /**
   * 端口映射，仅用于 Docker TEMP_PORT 下的 web/api
   */
  private Map<String, Integer> portBindings;

  /**
   * 环境名称
   */
  private String environmentName;

  /**
   * 数据源配置
   */
  private DataSourceDefine datasource;

  /**
   * 配置文件
   */
  private ConfigFile configFile;

  /**
   * 环境变量
   */
  private Map<String, String> env;

  /**
   * 数据源
   */
  public static class DataSourceDefine {

    /**
     * default、memory、environment
     */
    private String type;

    /**
     * 是否重新初始化（如果数据库存在数据库会被删除！）
     */
    private boolean reinitialize;


    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public boolean getReinitialize() {
      return reinitialize;
    }

    public void setReinitialize(boolean reinitialize) {
      this.reinitialize = reinitialize;
    }
  }

  /**
   * 配置文件
   */
  public static class ConfigFile {

    /**
     * 是否重置
     */
    @JsonProperty("isReset")
    private boolean isReset = false;

    public boolean isReset() {
      return isReset;
    }

    public void setReset(boolean reset) {
      isReset = reset;
    }
  }


  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getImage() {
    return image;
  }

  public AppContainerRequest setImage(String image) {
    this.image = image;
    return this;
  }

  public String getBackendType() {
    return backendType;
  }

  public void setBackendType(String backendType) {
    this.backendType = backendType;
  }

  public String getExposureStrategy() {
    return exposureStrategy;
  }

  public void setExposureStrategy(String exposureStrategy) {
    this.exposureStrategy = exposureStrategy;
  }

  public Map<String, Integer> getPortBindings() {
    return portBindings;
  }

  public void setPortBindings(Map<String, Integer> portBindings) {
    this.portBindings = portBindings;
  }

  public String getEnvironmentName() {
    return environmentName;
  }

  public void setEnvironmentName(String environmentName) {
    this.environmentName = environmentName;
  }

  public DataSourceDefine getDatasource() {
    return datasource;
  }

  public void setDatasource(DataSourceDefine datasource) {
    this.datasource = datasource;
  }

  public ConfigFile getConfigFile() {
    return configFile;
  }

  public void setConfigFile(ConfigFile configFile) {
    this.configFile = configFile;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public void setEnv(Map<String, String> env) {
    this.env = env;
  }
}
