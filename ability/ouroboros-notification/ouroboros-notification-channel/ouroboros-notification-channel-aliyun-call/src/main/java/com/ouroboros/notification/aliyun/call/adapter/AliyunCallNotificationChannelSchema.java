package com.ouroboros.notification.aliyun.call.adapter;

import com.ouroboros.notification.adapter.NotificationChannelSchema;

/**
 * @author liansz
 */
public class AliyunCallNotificationChannelSchema implements NotificationChannelSchema {

  /**
   * 电话模版消息id
   */
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
