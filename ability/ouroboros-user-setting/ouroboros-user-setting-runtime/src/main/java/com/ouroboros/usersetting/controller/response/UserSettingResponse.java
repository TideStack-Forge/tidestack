package com.ouroboros.usersetting.controller.response;

public class UserSettingResponse {
  private String settingName;
  private Object settingValue;

  public UserSettingResponse(String settingName, Object settingValue) {
    this.settingName = settingName;
    this.settingValue = settingValue;
  }

  public String getSettingName() {
    return settingName;
  }

  public UserSettingResponse setSettingName(String settingName) {
    this.settingName = settingName;
    return this;
  }

  public Object getSettingValue() {
    return settingValue;
  }

  public UserSettingResponse setSettingValue(Object settingValue) {
    this.settingValue = settingValue;
    return this;
  }
}
