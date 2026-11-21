package com.ouroboros.usersetting.model;


import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_user_setting", label = "用户设置")
public class UserSettingEntity {
  @Field(size = 20)
  @PrimaryKey(generator = "snowflake")
  private String id;
  @Field(size = 100)
  private String userId;
  @Field(size = 200)
  private String appName;
  @Field(size = 100)
  private String clientType;
  @Field(nullable = true)
  private Boolean isAutoLoad = true;
  @Field(size = 900)
  private String settingName;
  @Field(size = 500, nullable = true)
  private String settingDescription;
  @Field(size = 2000)
  private String settingValue;
  @Field
  private LocalDateTime createdAt;
  @Field(nullable = true)
  private LocalDateTime updatedAt;

  public String getId() {
    return id;
  }

  public UserSettingEntity setId(String id) {
    this.id = id;
    return this;
  }

  public String getUserId() {
    return userId;
  }

  public UserSettingEntity setUserId(String userId) {
    this.userId = userId;
    return this;
  }

  public String getAppName() {
    return appName;
  }

  public UserSettingEntity setAppName(String appName) {
    this.appName = appName;
    return this;
  }

  public String getClientType() {
    return clientType;
  }

  public UserSettingEntity setClientType(String clientType) {
    this.clientType = clientType;
    return this;
  }

  public Boolean getIsAutoLoad() {
    return isAutoLoad;
  }

  public UserSettingEntity setIsAutoLoad(Boolean autoLoad) {
    isAutoLoad = autoLoad;
    return this;
  }

  public String getSettingName() {
    return settingName;
  }

  public UserSettingEntity setSettingName(String settingName) {
    this.settingName = settingName;
    return this;
  }

  public String getSettingDescription() {
    return settingDescription;
  }

  public UserSettingEntity setSettingDescription(String settingDescription) {
    this.settingDescription = settingDescription;
    return this;
  }

  public String getSettingValue() {
    return settingValue;
  }

  public UserSettingEntity setSettingValue(String settingValue) {
    this.settingValue = settingValue;
    return this;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public UserSettingEntity setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public UserSettingEntity setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /**
   * 获取 UserSetting 对象
   *
   * @return DefaultUserSetting 对象
   */
  public UserSetting getUserSetting() {
    return new DefaultUserSetting()
        .setUserId(this.userId)
        .setClientType(this.clientType)
        .setIsAutoLoad(this.isAutoLoad)
        .setSettingName(this.settingName)
        .setSettingDescription(this.settingDescription)
        .setRawSettingValue(this.settingValue);
  }
}
