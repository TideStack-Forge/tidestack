package com.ouroboros.widget;

import java.util.List;
import java.util.Optional;

/**
 * WidgetDefinitionProvider 接口，用于提供组件定义
 * 该接口定义了获取组件定义的方法
 */
public interface WidgetDefinitionProvider {
  /**
   * 根据组件名称获取组件定义
   * @param name 组件名称，用于查找特定的组件定义
   * @return 返回一个Optional对象，包含找到的组件定义，如果未找到则为空
   */
  Optional<WidgetDefinition> getWidgetDefinition(String name);
  /**
   * 获取所有组件定义
   * @return 返回包含所有组件定义的列表
   */
  List<WidgetDefinition> getAllWidgetDefinitions();
}
