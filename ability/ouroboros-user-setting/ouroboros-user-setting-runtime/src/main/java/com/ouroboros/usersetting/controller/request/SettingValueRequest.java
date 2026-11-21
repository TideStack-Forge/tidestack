package com.ouroboros.usersetting.controller.request;

import com.ouroboros.util.Json;

public class SettingValueRequest {
  String settingName;
  Object settingValue;
  Boolean isAutoLoad;

  public String getSettingName() {
    return settingName;
  }

  public SettingValueRequest setSettingName(String settingName) {
    this.settingName = settingName;
    return this;
  }

  public Object getSettingValue() {
    return settingValue;
  }

  public SettingValueRequest setSettingValue(Object settingValue) {
    this.settingValue = settingValue;
    return this;
  }

  /**
   * 获取原始设置值（Json 字符串格式）
   *
   * @return Json 字符串
   */
  public String getRawSettingValue() {
    if (settingValue == null) {
      return null;
    }
    if (settingValue instanceof String) {
      return (String) settingValue;
    }
    return Json.toJsonString(settingValue);
  }

  public Boolean getIsAutoLoad() {
    return isAutoLoad == null || isAutoLoad;
  }

  public SettingValueRequest setIsAutoLoad(Boolean isAutoLoad) {
    this.isAutoLoad = isAutoLoad;
    return this;
  }
}
