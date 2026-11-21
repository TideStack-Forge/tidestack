package com.ouroboros.widget;

import java.util.List;
import java.util.Map;

import com.ouroboros.uischema.schema.SchemaNode;

/**
 * 组件定义脚手架接口
 * 该接口定义了构建组件定义所需的基本方法和元数据规范
 */
public interface WidgetDefinitionGenerator {
  /**
   * 获取组件名称
   * @return 组件的名称字符串
   */
  String getName();


  String getLabel();

  String getDescription();

  /**
   * 获取脚手架表单项列表
   * @return 包含所有表单项的SchemaNode列表
   */
  List<SchemaNode> getFormItems();

  /**
   * 根据组件定义脚手架元数据构建组件定义
   *
   * @param metadata 组件定义脚手架元数据，包含了构建组件定义所需的基本信息
   * @return 构建完成的组件定义对象
   */
  WidgetDefinition buildWidgetDefinition(WidgetSchema metadata);
}
