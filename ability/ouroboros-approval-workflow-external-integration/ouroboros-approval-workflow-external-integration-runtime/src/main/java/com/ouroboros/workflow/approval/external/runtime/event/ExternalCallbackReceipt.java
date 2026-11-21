package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/** Provider-neutral durable receipt for a verified callback payload. */
@Model(rawName = "t_approval_workflow_external_callback_receipt", label = "审批流外部回调接收凭证")
@UniqueConstraint(name = "uk_awf_ext_callback_receipt_nonce", fields = {"nonceRouteKey"})
@DataPlugin(name = "DuplicateDataChecker")
public class ExternalCallbackReceipt {

  public static final String STATUS_RECEIVING = "RECEIVING";
  public static final String STATUS_COMPLETED = "COMPLETED";

  @Field
  @PrimaryKey
  private String id;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  @Field
  private String nonce;

  @Field(size = 160)
  private String nonceRouteKey;

  @Field(size = 96)
  private String payloadDigest;

  @Field
  private Integer eventCount;

  @Field
  private String status;

  @Field(nullable = true)
  private String claimToken;

  @Field(nullable = true)
  private LocalDateTime claimUntil;

  @Field(rawType = "longtext", nullable = true)
  private String lastError;

  @Field
  private LocalDateTime expiresAt;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  @Field(nullable = true)
  private LocalDateTime completedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getAdapterId() { return adapterId; }
  public void setAdapterId(String adapterId) { this.adapterId = adapterId; }
  public String getConnectionId() { return connectionId; }
  public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
  public String getNonce() { return nonce; }
  public void setNonce(String nonce) { this.nonce = nonce; }
  public String getNonceRouteKey() { return nonceRouteKey; }
  public void setNonceRouteKey(String nonceRouteKey) { this.nonceRouteKey = nonceRouteKey; }
  public String getPayloadDigest() { return payloadDigest; }
  public void setPayloadDigest(String payloadDigest) { this.payloadDigest = payloadDigest; }
  public Integer getEventCount() { return eventCount == null ? 0 : eventCount; }
  public void setEventCount(Integer eventCount) { this.eventCount = eventCount; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getClaimToken() { return claimToken; }
  public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
  public LocalDateTime getClaimUntil() { return claimUntil; }
  public void setClaimUntil(LocalDateTime claimUntil) { this.claimUntil = claimUntil; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
  public LocalDateTime getCompletedAt() { return completedAt; }
  public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
