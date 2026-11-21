package com.ouroboros.notification.enterprise;

import com.ouroboros.notification.adapter.NotificationChannelSchema;

public class EnterpriseNotificationChannelSchema implements NotificationChannelSchema {
  private String titleTemplate;
  private String contentTemplate;
  private String linkTemplate;
  public String getTitleTemplate() { return titleTemplate; }
  public void setTitleTemplate(String value) { titleTemplate = value; }
  public String getContentTemplate() { return contentTemplate; }
  public void setContentTemplate(String value) { contentTemplate = value; }
  public String getLinkTemplate() { return linkTemplate; }
  public void setLinkTemplate(String value) { linkTemplate = value; }
}
