package com.ouroboros.notification.internal.message.adapter;

import java.util.Map;

import com.ouroboros.notification.adapter.NotificationChannelOptions;

/**
 * @author liansz
 */
public class InternalMessageNotificationChannelOptions implements NotificationChannelOptions {

  /**
   * 接收终端
   */
  private Map<String, Boolean> platform;

  /**
   * 业务类型
   */
  private String type;

  /**
   * 动作
   */
  private Map<String, Object> action;

  public Map<String, Boolean> getPlatform() {
    return platform;
  }

  public void setPlatform(Map<String, Boolean> platform) {
    this.platform = platform;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Map<String, Object> getAction() {
    return action;
  }

  public void setAction(Map<String, Object> action) {
    this.action = action;
  }
}
