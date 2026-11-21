package com.ouroboros.notification.email.adapter;

import com.ouroboros.notification.adapter.NotificationChannelSchema;

/**
 * @author liansz
 */
public class EmailNotificationChannelSchema implements NotificationChannelSchema {

  /**
   * 标题模版
   */
  private String titleTemplate;

  /**
   * 内容模版
   */
  private String contentTemplate;

  public String getTitleTemplate() {
    return titleTemplate;
  }

  public void setTitleTemplate(String titleTemplate) {
    this.titleTemplate = titleTemplate;
  }

  public String getContentTemplate() {
    return contentTemplate;
  }

  public void setContentTemplate(String contentTemplate) {
    this.contentTemplate = contentTemplate;
  }
}
