package com.ouroboros.workflow.approval.external.runtime.reconcile;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/** Durable keyset checkpoint for the RUNNING-binding reconciliation scan. */
@Model(rawName = "t_approval_workflow_external_reconcile_checkpoint", label = "审批流外部对账检查点")
public class ExternalReconcileCheckpoint {

  public static final String ID = "external-running-bindings";

  @Field
  @PrimaryKey
  private String id;

  /** Last fully successful page key; null means start from the first page. */
  @Field(nullable = true)
  private String afterLocalInstanceId;

  @Field(nullable = true)
  private String claimOwner;

  @Field(nullable = true)
  private String claimToken;

  @Field(nullable = true)
  private LocalDateTime claimUntil;

  @Field
  private LocalDateTime updatedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getAfterLocalInstanceId() { return afterLocalInstanceId; }
  public void setAfterLocalInstanceId(String afterLocalInstanceId) { this.afterLocalInstanceId = afterLocalInstanceId; }
  public String getClaimOwner() { return claimOwner; }
  public void setClaimOwner(String claimOwner) { this.claimOwner = claimOwner; }
  public String getClaimToken() { return claimToken; }
  public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
  public LocalDateTime getClaimUntil() { return claimUntil; }
  public void setClaimUntil(LocalDateTime claimUntil) { this.claimUntil = claimUntil; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
