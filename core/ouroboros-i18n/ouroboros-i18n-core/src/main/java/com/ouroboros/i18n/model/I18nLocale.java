package com.ouroboros.i18n.model;

import java.util.Map;

/**
 * 国际化语言环境接口
 * <p>
 * 定义多语言环境的数据结构，包含语言元数据和翻译内容。
 * 翻译消息采用 Namespace + Key 结构：
 * - Namespace：分类（schemaPath/menu/authority/common/base）
 * - Key：具体文案路径
 */
public interface I18nLocale {

  /**
   * 语言代码（如：zh-CN, en-US）
   */
  String getLocale();

  /**
   * 语言名称（如：简体中文, English）
   */
  String getName();

  /**
   * 版本号（遵循语义化版本规范）
   */
  String getVersion();

  /**
   * 是否启用该语言
   */
  Boolean getEnabled();

  /**
   * 是否为默认语言
   */
  Boolean getIsDefault();

  /**
   * 翻译消息内容
   * <p>
   * 结构：Map<Namespace, Map<Key, 翻译文本>>
   * <p>
   * 示例：
   * {
   * "/sys/user-management": {
   * "page.crud.columns.userName.label": "用户名"
   * },
   * "menu": {
   * "system.title": "系统管理"
   * }
   * }
   */
  Map<String, Map<String, String>> getMessages();

  /**
   * 文本方向
   */
  TextDirection getDirection();

  /**
   * 生成时间戳（毫秒）
   */
  Long getTimestamp();
}
