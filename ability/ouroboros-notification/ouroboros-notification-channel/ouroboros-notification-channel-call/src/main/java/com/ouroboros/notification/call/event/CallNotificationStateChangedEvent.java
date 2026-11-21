package com.ouroboros.notification.call.event;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEvent;

import com.ouroboros.notification.call.enums.CallState;

/**
 * @author liansz
 */
public class CallNotificationStateChangedEvent extends ApplicationEvent {

  /**
   * 手机号
   */
  private String phone;

  /**
   * 业务类型
   */
  private String businessType;

  /**
   * 业务ID
   */
  private String businessId;

  /**
   * 状态
   */
  private CallState state;

  /**
   * 发送时间
   */
  private LocalDateTime sendTime;

  /**
   * 完成时间
   */
  private LocalDateTime finishTime;

  public CallNotificationStateChangedEvent(Object source, String phone, String businessType, String businessId,
                                           CallState state, LocalDateTime sendTime, LocalDateTime finishTime) {
    super(source);

    this.phone = phone;
    this.businessType = businessType;
    this.businessId = businessId;
    this.state = state;
    this.sendTime = sendTime;
    this.finishTime = finishTime;
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

  public CallState getState() {
    return state;
  }

  public void setState(CallState state) {
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
