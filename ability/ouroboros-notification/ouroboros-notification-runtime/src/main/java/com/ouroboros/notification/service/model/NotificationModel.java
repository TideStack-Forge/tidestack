package com.ouroboros.notification.service.model;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.util.JsonBag;

/**
 * @author liansz
 */
@Model(rawName = "t_notification", label = "消息通知")
public class NotificationModel {

  /**
   * 主键
   */
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  /**
   * 模板
   */
  @Field
  private String template;

  /**
   * 载荷
   */
  @Field(nullable = true)
  private JsonBag payload;

  /**
   * 渠道选项
   */
  @Field(nullable = true)
  private JsonBag channelOptions;

  /**
   * 发送员工
   */
  @Field(nullable = true)
  private String sendByEmployee;

  /** 调用方业务幂等键。 */
  @Field(nullable = true)
  private String idempotencyKey;

  /**
   * 发送时间
   */
  @Field
  private LocalDateTime sendTime;

  /** Frozen channel schemas for reliable sending; absent on legacy notifications. */
  @Field(nullable = true)
  private JsonBag channelSchemas;

  public JsonBag getChannelSchemas() { return channelSchemas; }

  public void setChannelSchemas(JsonBag channelSchemas) { this.channelSchemas = channelSchemas; }

  // Reliable delivery fields are absent on legacy notifications.
  @Field(nullable = true, size = 255)
  private String sourceFactId;

  @Field(nullable = true, size = 512)
  private String ruleIdentity;

  @Field(nullable = true, size = 255)
  private String ruleRevision;

  @Field(nullable = true, size = 64)
  private String planFingerprint;

  @Field(nullable = true)
  private LocalDateTime contentClearedAt;

  public LocalDateTime getContentClearedAt() { return contentClearedAt; }
  public void setContentClearedAt(LocalDateTime value) { contentClearedAt = value; }

  public String getSourceFactId() { return sourceFactId; }

  public void setSourceFactId(String sourceFactId) { this.sourceFactId = sourceFactId; }

  public String getRuleIdentity() { return ruleIdentity; }

  public void setRuleIdentity(String ruleIdentity) { this.ruleIdentity = ruleIdentity; }

  public String getRuleRevision() { return ruleRevision; }

  public void setRuleRevision(String ruleRevision) { this.ruleRevision = ruleRevision; }

  public String getPlanFingerprint() { return planFingerprint; }

  public void setPlanFingerprint(String planFingerprint) { this.planFingerprint = planFingerprint; }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTemplate() {
    return template;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public JsonBag getPayload() {
    return payload;
  }

  public void setPayload(JsonBag payload) {
    this.payload = payload;
  }

  public JsonBag getChannelOptions() {
    return channelOptions;
  }

  public void setChannelOptions(JsonBag channelOptions) {
    this.channelOptions = channelOptions;
  }

  public String getSendByEmployee() {
    return sendByEmployee;
  }

  public void setSendByEmployee(String sendByEmployee) {
    this.sendByEmployee = sendByEmployee;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public LocalDateTime getSendTime() {
    return sendTime;
  }

  public void setSendTime(LocalDateTime sendTime) {
    this.sendTime = sendTime;
  }
}
