package com.ouroboros.notification.internal.message.service.model;

import java.time.LocalDateTime;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * @author liansz
 */
@Model(rawName = "t_notification_internal_message", label = "消息通知之内部消息")
public class NotificationInternalMessageModel {

  /** Reliable channel delivery identity, absent on legacy messages. */
  @Field(nullable = true, size = 255)
  private String deliveryKey;

  @Field(nullable = true, size = 64)
  private String deliveryFingerprint;

  public String getDeliveryKey() { return deliveryKey; }
  public void setDeliveryKey(String deliveryKey) { this.deliveryKey = deliveryKey; }
  public String getDeliveryFingerprint() { return deliveryFingerprint; }
  public void setDeliveryFingerprint(String deliveryFingerprint) { this.deliveryFingerprint = deliveryFingerprint; }

  /**
   * 主键
   */
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  /**
   * 接收平台
   * InternalMessagePlatformEnum
   */
  @Field
  private String platform;

  /**
   * 标题
   */
  @Field
  private String title;

  /**
   * 内容
   */
  @Field
  private String content;

  /**
   * 业务类型
   */
  @Field(nullable = true)
  private String type;

  /**
   * 动作
   */
  @Field(nullable = true)
  private Map<String, Object> action;

  /**
   * 员工
   */
  @Field
  private String employee;

  /**
   * 接收时间
   */
  @Field
  private LocalDateTime receivingTime;

  /**
   * 状态
   * InternalMessageStateEnum
   */
  @Field
  private String state;

  /**
   * 读取/处理时间
   */
  @Field(nullable = true)
  private LocalDateTime handleTime;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
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

  public String getEmployee() {
    return employee;
  }

  public void setEmployee(String employee) {
    this.employee = employee;
  }

  public LocalDateTime getReceivingTime() {
    return receivingTime;
  }

  public void setReceivingTime(LocalDateTime receivingTime) {
    this.receivingTime = receivingTime;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public LocalDateTime getHandleTime() {
    return handleTime;
  }

  public void setHandleTime(LocalDateTime handleTime) {
    this.handleTime = handleTime;
  }
}
