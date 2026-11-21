package com.ouroboros.i18n.model;

/**
 * 国际化条目接口
 * <p>
 * 用于文案提取阶段，表示从元数据中提取的单个文案条目。
 * 提取后的条目会被聚合到 I18nLocale 中。
 */
public interface I18nEntry {

  /**
   * 命名空间
   *
   * 可能的值：
   * - schemaPath：UI Schema 路径（如：/sys/user-management）
   * - menu：菜单分类
   * - authority：权限分类
   * - common：通用文案分类
   * - base：基础文案分类
   */
  String getNamespace();

  /**
   * 键
   *
   * 具体文案的路径标识，格式取决于命名空间的类型：
   * - UI Schema：{viewPath}.{propertyName}
   * - menu：{fullName}.{propertyName}
   * - authority：{fullName}.{propertyName}
   * - common/base：自定义格式
   */
  String getKey();

  /**
   * 原始文本（源语言）
   */
  String getSourceText();

  /**
   * 上下文信息
   *
   * 用于帮助理解文案来源，便于翻译和调试。
   * 示例：
   * - "UISchema: /sys/user-management"
   * - "MenuModel: system.user-management"
   * - "Authority: system.user.create"
   */
  String getContext();

  /**
   * 属性名称
   *
   * 文案在元数据中的属性名，如：
   * - label
   * - title
   * - placeholder
   * - description
   */
  String getPropertyName();
}
