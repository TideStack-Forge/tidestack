package com.ouroboros.notification.template;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author liansz
 */
public class DefaultNotificationTemplate implements NotificationTemplate {

  /**
   * 名称
   */
  private String name;

  /**
   * 标题
   */
  private String title;

  /**
   * 载荷架构
   */
  private Map<String, Object> payloadSchema;

  /**
   * 渠道
   */
  private Map<String, ?> channel;

  /**
   * 是否启用
   */
  @JsonProperty("isEnabled")
  private boolean isEnabled;

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public Map<String, Object> getPayloadSchema() {
    return payloadSchema;
  }

  public void setPayloadSchema(Map<String, Object> payloadSchema) {
    this.payloadSchema = payloadSchema;
  }

  @Override
  public Map getChannel() {
    return channel;
  }

  public void setChannel(Map<String, ?> channel) {
    this.channel = channel;
  }

  @Override
  @JsonProperty("isEnabled")
  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }
}
