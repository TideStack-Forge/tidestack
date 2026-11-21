package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * 轮询补偿同步状态：按 (adapterId, connectionId) 记录增量对账游标。
 */
@Model(rawName = "t_approval_workflow_external_sync_state", label = "审批流外部同步状态")
public class ExternalSyncState {

  /** 主键取值约定：adapterId + ":" + connectionId。 */
  public static final String ID_SEPARATOR = ":";

  @Field
  @PrimaryKey
  private String id;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  @Field
  private LocalDateTime lastSyncAt;

  /** 当前 polling run 固定的窗口起点；run 完成后清空。 */
  @Field(nullable = true)
  private LocalDateTime runFrom;

  /** 当前 polling run 固定的窗口终点；run 完成后清空。 */
  @Field(nullable = true)
  private LocalDateTime runUntil;

  @Field(nullable = true)
  private String cursor;

  @Field(nullable = true)
  private String claimOwner;

  @Field(nullable = true)
  private String claimToken;

  @Field(nullable = true)
  private LocalDateTime claimUntil;

  @Field
  private LocalDateTime updatedAt;

  public ExternalSyncState() {
  }

  public static String buildId(String adapterId, String connectionId) {
    return adapterId + ID_SEPARATOR + connectionId;
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

  public LocalDateTime getLastSyncAt() {
    return lastSyncAt;
  }

  public void setLastSyncAt(LocalDateTime lastSyncAt) {
    this.lastSyncAt = lastSyncAt;
  }

  public LocalDateTime getRunFrom() {
    return runFrom;
  }

  public void setRunFrom(LocalDateTime runFrom) {
    this.runFrom = runFrom;
  }

  public LocalDateTime getRunUntil() {
    return runUntil;
  }

  public void setRunUntil(LocalDateTime runUntil) {
    this.runUntil = runUntil;
  }

  public String getCursor() {
    return cursor;
  }

  public void setCursor(String cursor) {
    this.cursor = cursor;
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

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
