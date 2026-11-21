package com.ouroboros.dev.datasource.model;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * @author lianshuze
 **/
@Model(rawName = "t_dev_app_datasource", label = "应用数据源映射表")
public class AppDataSourceModel {

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String appName;

  @Field
  private String dataStationName;

  @Field
  private String platformDataSourceName;

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

  public String getDataStationName() {
    return dataStationName;
  }

  public void setDataStationName(String dataStationName) {
    this.dataStationName = dataStationName;
  }

  public String getPlatformDataSourceName() {
    return platformDataSourceName;
  }

  public void setPlatformDataSourceName(String platformDataSourceName) {
    this.platformDataSourceName = platformDataSourceName;
  }
}
