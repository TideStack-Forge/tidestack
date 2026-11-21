package com.ouroboros.dev.datasource.model;

import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;

/**
 * 数据源定义类，用于封装数据源的配置信息
 * 包含数据源类型和属性配置
 */
public class DataSourceDefine {
  // 数据源类型
  private String type;
  // 数据源属性配置
  private Map<String, Object> properties;

  /**
   * 构造函数，根据传入的Map初始化数据源定义
   * @param define 包含数据源配置信息的Map，至少应包含"type"键
   */
  public DataSourceDefine(Map<String, Object> define) {
    // 从define中获取type，如果为null则使用空字符串
    this.type = ObjectUtils.defaultIfNull(define.get("type"), "").toString();
    // 将整个define作为properties保存
    this.properties = define;
  }

  /**
   * 获取数据源类型
   * @return 数据源类型字符串
   */
  public String getType() {
    return type;
  }

  /**
   * 设置数据源类型
   * @param type 要设置的数据源类型
   * @return 返回当前对象，支持链式调用
   */
  public DataSourceDefine setType(String type) {
    this.type = type;
    return this;
  }

  /**
   * 获取数据源属性配置
   * @return 包含数据源属性配置的Map
   */
  public Map<String, Object> getProperties() {
    return properties;
  }
}
