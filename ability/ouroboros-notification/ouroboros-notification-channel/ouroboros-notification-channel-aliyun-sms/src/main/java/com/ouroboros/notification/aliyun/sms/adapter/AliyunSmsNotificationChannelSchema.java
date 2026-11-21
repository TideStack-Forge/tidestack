package com.ouroboros.notification.aliyun.sms.adapter;

import com.ouroboros.notification.adapter.NotificationChannelSchema;

/**
 * @author liansz
 */
public class AliyunSmsNotificationChannelSchema implements NotificationChannelSchema {

  /**
   * 短信模版消息id
   */
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
