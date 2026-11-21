package com.ouroboros.notification.service.model;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * @author liansz
 */
@Model(rawName = "t_notification_state", label = "通知状态")
public class NotificationStateModel {

  /**
   * id
   */
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  /**
   * 通知id
   */
  @Field
  private String notificationId;

  /**
   * 渠道
   */
  @Field
  private String channel;

  /**
   * 地址
   */
  @Field(nullable = true)
  private String address;

  /**
   * 状态
   * 状态: error=发送失败、success=发送成功
   */
  @Field
  private String sendState;

  /**
   * 发送失败原因
   */
  @Field(nullable = true)
  private String sendFailCause;

  /** Whole address collection acknowledged by this recipient/channel unit. */
  @Field(nullable = true)
  private com.ouroboros.util.JsonBag deliveryAddresses;

  public com.ouroboros.util.JsonBag getDeliveryAddresses() { return deliveryAddresses; }

  public void setDeliveryAddresses(com.ouroboros.util.JsonBag deliveryAddresses) { this.deliveryAddresses = deliveryAddresses; }

  // Reliable delivery fields are absent on legacy notifications.
  @Field(nullable = true, size = 255)
  private String recipientId;

  @Field(nullable = true)
  private Integer attempt;

  @Field(nullable = true, size = 64)
  private String attemptToken;

  @Field(nullable = true)
  private java.time.LocalDateTime attemptDeadline;

  @Field(nullable = true)
  private java.time.LocalDateTime finishedAt;

  public java.time.LocalDateTime getFinishedAt() { return finishedAt; }
  public void setFinishedAt(java.time.LocalDateTime value) { finishedAt = value; }

  public String getRecipientId() { return recipientId; }

  public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

  public Integer getAttempt() { return attempt; }

  public void setAttempt(Integer attempt) { this.attempt = attempt; }

  public String getAttemptToken() { return attemptToken; }

  public void setAttemptToken(String attemptToken) { this.attemptToken = attemptToken; }

  public java.time.LocalDateTime getAttemptDeadline() { return attemptDeadline; }

  public void setAttemptDeadline(java.time.LocalDateTime attemptDeadline) { this.attemptDeadline = attemptDeadline; }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getNotificationId() {
    return notificationId;
  }

  public void setNotificationId(String notificationId) {
    this.notificationId = notificationId;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getSendState() {
    return sendState;
  }

  public void setSendState(String sendState) {
    this.sendState = sendState;
  }

  public String getSendFailCause() {
    return sendFailCause;
  }

  public void setSendFailCause(String sendFailCause) {
    this.sendFailCause = sendFailCause;
  }
}
