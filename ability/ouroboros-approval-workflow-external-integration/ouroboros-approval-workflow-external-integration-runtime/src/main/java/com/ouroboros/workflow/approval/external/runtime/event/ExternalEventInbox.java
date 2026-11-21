package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * 外部事件收件箱：所有结果获取通道（HTTP 回调、适配器自有接收器、轮询补偿）统一经本表幂等写入，
 * 由处理器归一化为绑定更新与统一结果事件。
 */
@Model(rawName = "t_approval_workflow_external_event_inbox", label = "审批流外部事件收件箱")
@UniqueConstraint(name = "uk_awf_ext_inbox_event", fields = {"adapterId", "connectionId", "eventId"})
@DataPlugin(name = "DuplicateDataChecker")
public class ExternalEventInbox {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_PROCESSING = "PROCESSING";
  public static final String STATUS_PROCESSED = "PROCESSED";
  public static final String STATUS_STALE = "STALE";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_DEAD = "DEAD";

  /** 失败重试上限，超过进入死信。 */
  public static final int MAX_RETRY_COUNT = 5;

  @Field
  @PrimaryKey
  private String id;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  /** 外部事件唯一 ID（幂等键）。 */
  @Field
  private String eventId;

  @Field
  private String externalInstanceId;

  @Field(nullable = true)
  private String externalTaskId;

  /** 外部事件序号（可空，不假设必然递增）。 */
  @Field(nullable = true)
  private String sequence;

  /** 外部事件时间（原始格式，可空）。 */
  @Field(nullable = true)
  private String externalEventTime;

  @Field
  private String eventType;

  @Field(nullable = true)
  private String resultCode;

  @Field(nullable = true)
  private String payloadDigest;

  @Field
  private String status;

  @Field
  private Integer retryCount = 0;

  /** 自动重试最早可再次领取时间；人工重放不受该时间限制。 */
  @Field(nullable = true)
  private LocalDateTime nextAttemptAt;

  @Field(rawType = "longtext", nullable = true)
  private String lastError;

  /** 观测用 worker 标识；不能作为 fencing token。 */
  @Field(nullable = true)
  private String claimOwner;

  /** 每次领取生成的唯一 fencing token。 */
  @Field(nullable = true)
  private String claimToken;

  /** 当前 claim 租约到期时间。 */
  @Field(nullable = true)
  private LocalDateTime claimUntil;

  @Field
  private LocalDateTime receivedAt;

  @Field(nullable = true)
  private LocalDateTime processedAt;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  public ExternalEventInbox() {
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAdapterId() {
    return adapterId;
  }

  public void setAdapterId(String adapterId) {
    this.adapterId = adapterId;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public void setConnectionId(String connectionId) {
    this.connectionId = connectionId;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getExternalInstanceId() {
    return externalInstanceId;
  }

  public void setExternalInstanceId(String externalInstanceId) {
    this.externalInstanceId = externalInstanceId;
  }

  public String getExternalTaskId() {
    return externalTaskId;
  }

  public void setExternalTaskId(String externalTaskId) {
    this.externalTaskId = externalTaskId;
  }

  public String getSequence() {
    return sequence;
  }

  public void setSequence(String sequence) {
    this.sequence = sequence;
  }

  public String getExternalEventTime() {
    return externalEventTime;
  }

  public void setExternalEventTime(String externalEventTime) {
    this.externalEventTime = externalEventTime;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getResultCode() {
    return resultCode;
  }

  public void setResultCode(String resultCode) {
    this.resultCode = resultCode;
  }

  public String getPayloadDigest() {
    return payloadDigest;
  }

  public void setPayloadDigest(String payloadDigest) {
    this.payloadDigest = payloadDigest;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getRetryCount() {
    return retryCount == null ? 0 : retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public LocalDateTime getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public String getClaimOwner() {
    return claimOwner;
  }

  public void setClaimOwner(String claimOwner) {
    this.claimOwner = claimOwner;
  }

  public String getClaimToken() {
    return claimToken;
  }

  public void setClaimToken(String claimToken) {
    this.claimToken = claimToken;
  }

  public LocalDateTime getClaimUntil() {
    return claimUntil;
  }

  public void setClaimUntil(LocalDateTime claimUntil) {
    this.claimUntil = claimUntil;
  }

  public LocalDateTime getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(LocalDateTime receivedAt) {
    this.receivedAt = receivedAt;
  }

  public LocalDateTime getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(LocalDateTime processedAt) {
    this.processedAt = processedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
