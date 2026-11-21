package com.ouroboros.widget;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * WidgetConfig 类，用于配置小部件的各种属性
 * 该类提供了设置和获取小标题、头部、页脚以及额外属性的方法
 */
public class WidgetConfig {
  // 小部件的标题
  private String title;
  // 小部件的头部内容
  private Object header;
  // 是否显示头部
  private Boolean showHeader;
  // 是否显示页脚
  private Boolean showFooter;
  // 存储额外属性的Map集合
  private Map<String, Object> props = new LinkedHashMap<>();

  /**
   * 获取小部件的标题
   * @return 返回小部件的标题字符串
   */
  public String getTitle() {
    return title;
  }

  /**
   * 设置小部件的标题
   * @param title 要设置的标题
   * @return 返回当前WidgetConfig对象，支持链式调用
   */
  public WidgetConfig setTitle(String title) {
    this.title = title;
    return this;
  }

  /**
   * 获取小部件的头部内容
   * @return 返回头部内容对象
   */
  public Object getHeader() {
    return header;
  }

  /**
   * 设置小部件的头部内容
   * @param header 要设置的头部内容
   * @return 返回当前WidgetConfig对象，支持链式调用
   */
  public WidgetConfig setHeader(Object header) {
    this.header = header;
    return this;
  }

  /**
   * 获取是否显示头部
   * @return 返回是否显示头部的布尔值
   */
  public Boolean getShowHeader() {
    return showHeader;
  }

  /**
   * 设置是否显示头部
   * @param showHeader 是否显示头部的布尔值
   * @return 返回当前WidgetConfig对象，支持链式调用
   */
  public WidgetConfig setShowHeader(Boolean showHeader) {
    this.showHeader = showHeader;
    return this;
  }

  /**
   * 获取是否显示页脚
   * @return 返回是否显示页脚的布尔值
   */
  public Boolean getShowFooter() {
    return showFooter;
  }

  /**
   * 设置是否显示页脚
   * @param showFooter 是否显示页脚的布尔值
   * @return 返回当前WidgetConfig对象，支持链式调用
   */
  public WidgetConfig setShowFooter(Boolean showFooter) {
    this.showFooter = showFooter;
    return this;
  }

  /**
   * 获取额外属性值
   * @param key 属性键名
   * @return 返回对应的属性值
   */
  public Object getExtraProp(String key) {
    return props.get(key);
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return props;
  }

  /**
   * 设置额外属性
   * @param key 属性键名
   * @param value 属性值
   * @return 返回当前WidgetConfig对象，支持链式调用
   */
  @JsonAnySetter
  public WidgetConfig setExtraProp(String key, Object value) {
    if (props == null) {
      props = new LinkedHashMap<>();
    } else {
      props.put(key, value);
    }
    return this;
  }
}
