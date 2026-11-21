package com.ouroboros.notification.wechat.adapter;

import com.ouroboros.notification.adapter.NotificationChannelSchema;

/**
 * @author liansz
 */
public class WechatNotificationChannelSchema implements NotificationChannelSchema {

  /**
   * 微信模版消息id
   */
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
