package com.ouroboros.notification.template;

import java.util.Map;

/**
 * @author liansz
 */
public interface NotificationTemplate {

  /**
   * 名称
   *
   * @return
   */
  String getName();

  /**
   * 标题
   *
   * @return
   */
  String getTitle();

  /**
   * 载荷架构
   *
   * @return
   */
  Map<String, Object> getPayloadSchema();

  /**
   * 渠道集合
   *
   * @return Map<channel, Map < schema | optional, object>
   */
  Map<String, Map<String, Object>> getChannel();

  /**
   * 是否启用
   *
   * @return
   */
  boolean isEnabled();
}
