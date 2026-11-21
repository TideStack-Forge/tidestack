package com.ouroboros.usersetting.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 用户设置
 *
 */
public interface UserSetting {

  String getUserId();

  String getClientType();

  Boolean getIsAutoLoad();

  String getSettingName();

  String getSettingDescription();

  /**
   * 获取原始设置值（Json 字符串格式）
   *
   * @return Json 字符串
   */
  String getRawSettingValue();

  /**
   * 获取设置值对象
   *
   * @return 设置值对象
   */
  Object getSettingValue();

  /**
   * 获取字符串类型的设置值
   *
   * @return 字符串值
   */
  String getStringValue();

  /**
   * 获取数值类型的设置值
   *
   * @return 数值
   */
  Number getNumberValue();

  /**
   * 获取 Map 类型的设置值
   *
   * @return Map<String, Object>
   */
  Map<String, Object> getMapValue();

  /**
   * 获取 List 类型的设置值
   *
   * @return List<Object>
   */
  List<Object> getListValue();

  /**
   * 获取指定类型的设置值
   *
   * @param clazz 目标类型
   * @param <T>   类型参数
   * @return 转换后的对象
   */
  <T> T getValue(Class<T> clazz);

  /**
   * 获取指定类型的 List 设置值
   *
   * @param typeReference 类型引用
   * @param <T>           元素类型
   * @return 转换后的 List
   */
  <T> List<T> getListValue(TypeReference<List<T>> typeReference);
}
