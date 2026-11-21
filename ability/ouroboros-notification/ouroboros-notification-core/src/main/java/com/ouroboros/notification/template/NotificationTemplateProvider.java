package com.ouroboros.notification.template;

import java.util.List;
import java.util.Optional;

/**
 * @author liansz
 */
public interface NotificationTemplateProvider {

  /** Runtime overrides are excluded when resolving the current deployed default. */
  default boolean isRuntimeOverride() {
    return false;
  }

  /**
   * 获取模板
   *
   * @param name
   * @return
   */
  Optional<NotificationTemplate> getTemplate(String name);

  /**
   * 获取所有模板
   *
   * @return
   */
  List<NotificationTemplate> getAllTemplate();
}
