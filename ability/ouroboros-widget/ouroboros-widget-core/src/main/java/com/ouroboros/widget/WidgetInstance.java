package com.ouroboros.widget;

import java.util.Map;

import com.ouroboros.uischema.schema.SchemaNode;

/**
 * Widget类表示一个组件，包含组件的基本信息、布局、配置和模板结构
 */
public class WidgetInstance {
  // 组件的唯一标识符
  private String id;
  // 组件的名称
  private String widgetName;
  // 组件位置
  private GridPosition position;
  // 组件尺寸
  private GridSize size;
  // 组件的配置信息
  private WidgetConfig config;
  // 样式配置
  private Map<String, Object> styleConfig;
  // 组件的模板结构节点
  private SchemaNode schemaTpl;

  /**
   * 获取组件ID
   * @return 组件ID
   */
  public String getId() {
    return id;
  }

  /**
   * 设置组件ID
   * @param id 要设置的组件ID
   * @return 返回当前Widget对象，支持链式调用
   */
  public WidgetInstance setId(String id) {
    this.id = id;
    return this;
  }

  /**
   * 获取组件名称
   * @return 组件名称
   */
  public String getWidgetName() {
    return widgetName;
  }

  /**
   * 设置组件名称
   * @param widgetName 要设置的组件名称
   * @return 返回当前Widget对象，支持链式调用
   */
  public WidgetInstance setWidgetName(String widgetName) {
    this.widgetName = widgetName;
    return this;
  }

  public GridPosition getPosition() {
    return position;
  }

  public WidgetInstance setPosition(GridPosition position) {
    this.position = position;
    return this;
  }

  public GridSize getSize() {
    return size;
  }

  public WidgetInstance setSize(GridSize size) {
    this.size = size;
    return this;
  }

  /**
   * 获取组件配置信息
   * @return 组件的WidgetConfig对象
   */
  public WidgetConfig getConfig() {
    return config;
  }

  /**
   * 设置组件配置信息
   * @param config 要设置的WidgetConfig对象
   * @return 返回当前Widget对象，支持链式调用
   */
  public WidgetInstance setConfig(WidgetConfig config) {
    this.config = config;
    return this;
  }

  public Map<String, Object> getStyleConfig() {
    return styleConfig;
  }

  public WidgetInstance setStyleConfig(Map<String, Object> styleConfig) {
    this.styleConfig = styleConfig;
    return this;
  }

  /**
   * 获取组件模板结构节点
   * @return 组件的SchemaNode对象
   */
  public SchemaNode getSchemaTpl() {
    return schemaTpl;
  }

  /**
   * 设置组件模板结构节点
   * @param schemaTpl 要设置的SchemaNode对象
   * @return 返回当前Widget对象，支持链式调用
   */
  public WidgetInstance setSchemaTpl(SchemaNode schemaTpl) {
    this.schemaTpl = schemaTpl;
    return this;
  }

}
