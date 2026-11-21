package com.ouroboros.usersetting.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.util.Json;

/**
 * 默认用户设置实现
 */
public class DefaultUserSetting implements UserSetting {
  private String userId;
  private String clientType;
  private Boolean isAutoLoad;
  private String settingName;
  private String settingDescription;
  private String rawSettingValue;

  @Override
  public String getUserId() {
    return userId;
  }

  public DefaultUserSetting setUserId(String userId) {
    this.userId = userId;
    return this;
  }

  @Override
  public String getClientType() {
    return clientType;
  }

  public DefaultUserSetting setClientType(String clientType) {
    this.clientType = clientType;
    return this;
  }

  @Override
  public Boolean getIsAutoLoad() {
    return isAutoLoad;
  }

  public DefaultUserSetting setIsAutoLoad(Boolean isAutoLoad) {
    this.isAutoLoad = isAutoLoad;
    return this;
  }

  @Override
  public String getSettingName() {
    return settingName;
  }

  public DefaultUserSetting setSettingName(String settingName) {
    this.settingName = settingName;
    return this;
  }

  @Override
  public String getSettingDescription() {
    return settingDescription;
  }

  public DefaultUserSetting setSettingDescription(String settingDescription) {
    this.settingDescription = settingDescription;
    return this;
  }

  @Override
  public String getRawSettingValue() {
    return rawSettingValue;
  }

  public DefaultUserSetting setRawSettingValue(String rawSettingValue) {
    this.rawSettingValue = rawSettingValue;
    return this;
  }

  @Override
  public Object getSettingValue() {
    if (rawSettingValue == null) {
      return null;
    }

    var value = Json.toJsonBag(rawSettingValue);
    if (value.isList()) {
      return value.getList();
    }
    if (value.isMap()) {
      return value.getMap();
    }
    if (value.isValue()) {
      return value.get();
    }
    return null;
  }

  @Override
  public String getStringValue() {
    if (rawSettingValue == null) {
      return null;
    }
    // 尝试解析为字符串对象（带引号的 Json 字符串）
    var beanResult = Json.tryToBean(String.class, rawSettingValue);
    if (beanResult.isSuccess()) {
      return beanResult.get();
    }
    // 解析失败，直接返回原始值
    return rawSettingValue;
  }

  @Override
  public Number getNumberValue() {
    if (rawSettingValue == null) {
      return null;
    }
    var value = Json.toJsonBag(rawSettingValue);
    if (value.get() instanceof Number number) {
      return number;
    }
    return null;
  }

  @Override
  public Map<String, Object> getMapValue() {
    if (rawSettingValue == null) {
      return null;
    }
    return Json.toMap(rawSettingValue);
  }

  @Override
  public List<Object> getListValue() {
    if (rawSettingValue == null) {
      return null;
    }
    return Json.toList(rawSettingValue);
  }

  @Override
  public <T> T getValue(Class<T> clazz) {
    if (rawSettingValue == null) {
      return null;
    }
    return Json.toBean(clazz, rawSettingValue);
  }

  @Override
  public <T> List<T> getListValue(TypeReference<List<T>> typeReference) {
    if (rawSettingValue == null) {
      return null;
    }
    return Json.toList(typeReference, rawSettingValue);
  }
}
