package com.ouroboros.businessmodel.workflow.store;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/** Durable handoff receipt; it owns no scanner and creates no delivery queue. */
@Model(rawName = "t_business_workflow_fact_receipt", label = "业务工作流事实接收")
@UniqueConstraint(name = "uk_bwf_receiver_sequence", fields = {"projectionKey", "instanceId", "stateSequence"})
public class WorkflowBusinessFactReceipt {
  @Field(size = 64) @PrimaryKey
  private String receiptKey;
  @Field(size = 64)
  private String projectionKey;
  @Field
  private String sourceFactId;
  @Field
  private String instanceId;
  @Field(nullable = true)
  private Long stateSequence;
  @Field(nullable = true)
  private String businessEventId;
  @Field
  private String disposition;
  @Field
  private LocalDateTime receivedAt;

  public String getReceiptKey() { return receiptKey; }
  public void setReceiptKey(String receiptKey) { this.receiptKey = receiptKey; }
  public String getProjectionKey() { return projectionKey; }
  public void setProjectionKey(String projectionKey) { this.projectionKey = projectionKey; }
  public String getSourceFactId() { return sourceFactId; }
  public void setSourceFactId(String sourceFactId) { this.sourceFactId = sourceFactId; }
  public String getInstanceId() { return instanceId; }
  public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
  public Long getStateSequence() { return stateSequence; }
  public void setStateSequence(Long stateSequence) { this.stateSequence = stateSequence; }
  public String getBusinessEventId() { return businessEventId; }
  public void setBusinessEventId(String businessEventId) { this.businessEventId = businessEventId; }
  public String getDisposition() { return disposition; }
  public void setDisposition(String disposition) { this.disposition = disposition; }
  public LocalDateTime getReceivedAt() { return receivedAt; }
  public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
}
