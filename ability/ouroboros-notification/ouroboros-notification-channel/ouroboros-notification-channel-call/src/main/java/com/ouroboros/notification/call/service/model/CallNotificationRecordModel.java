package com.ouroboros.notification.call.service.model;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * @author liansz
 */
@Model(rawName = "t_notification_call_record", label = "通知语音外呼记录")
public class CallNotificationRecordModel {

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String provider;

  @Field
  private String phone;

  @Field(nullable = true)
  private String businessType;

  @Field(nullable = true)
  private String businessId;

  @Field(nullable = true)
  private String receipt;

  @Field
  private String state;

  @Field
  private LocalDateTime sendTime;

  @Field(nullable = true)
  private LocalDateTime finishTime;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getBusinessType() {
    return businessType;
  }

  public void setBusinessType(String businessType) {
    this.businessType = businessType;
  }

  public String getBusinessId() {
    return businessId;
  }

  public void setBusinessId(String businessId) {
    this.businessId = businessId;
  }

  public String getReceipt() {
    return receipt;
  }

  public void setReceipt(String receipt) {
    this.receipt = receipt;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public LocalDateTime getSendTime() {
    return sendTime;
  }

  public void setSendTime(LocalDateTime sendTime) {
    this.sendTime = sendTime;
  }

  public LocalDateTime getFinishTime() {
    return finishTime;
  }

  public void setFinishTime(LocalDateTime finishTime) {
    this.finishTime = finishTime;
  }
}
