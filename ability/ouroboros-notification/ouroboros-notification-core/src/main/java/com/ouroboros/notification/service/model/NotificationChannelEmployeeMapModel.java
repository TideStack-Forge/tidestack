package com.ouroboros.notification.service.model;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_notification_channel_employee_map", label = "通知渠道员工映射")
public class NotificationChannelEmployeeMapModel {

  @PrimaryKey(generator = "snowflake")
  @Field
  private String id;

  @Field
  private String channel;

  @Field
  private String employeeId;

  @Field
  private String address;

  @Field
  private String remarks;

  @Field
  private LocalDateTime operateTime;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getEmployeeId() {
    return employeeId;
  }

  public void setEmployeeId(String employeeId) {
    this.employeeId = employeeId;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getRemarks() {
    return remarks;
  }

  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  public LocalDateTime getOperateTime() {
    return operateTime;
  }

  public void setOperateTime(LocalDateTime operateTime) {
    this.operateTime = operateTime;
  }
}
