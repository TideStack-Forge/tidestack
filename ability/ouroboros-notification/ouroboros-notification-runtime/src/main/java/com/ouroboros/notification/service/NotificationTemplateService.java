package com.ouroboros.notification.service;

import com.ouroboros.common.service.ModelService;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.service.model.NotificationTemplateModel;

/**
 * @author liansz
 */
public interface NotificationTemplateService extends ModelService<String, NotificationTemplate> {

  /**
   * 通过name获取
   *
   * @param name
   * @return
   */
  NotificationTemplate getByName(String name);

  NotificationTemplateModel getOverride(String name);

  boolean insertOverride(NotificationTemplateModel template);

  boolean replaceOverride(NotificationTemplateModel template, long expectedRevision);

  boolean deleteOverride(String id, long expectedRevision);

  /** Backfills concurrency metadata after the original model-driven schema migration. */
  int initializeRuntimeManagement();
}
