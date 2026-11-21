package com.ouroboros.workflow.approval.external.runtime.binding;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * 结果型接入（RESULT_ONLY）绑定：业务记录与外部实例的关联，不创建本地实例或任务。
 */
@Model(rawName = "t_approval_workflow_external_result_binding", label = "审批流外部结果绑定")
@UniqueConstraint(name = "uk_awf_ext_result_external_instance", fields = {"externalInstanceRouteKey"})
@UniqueConstraint(name = "uk_awf_ext_result_idempotency", fields = {"idempotencyRouteKey"})
@UniqueConstraint(name = "uk_awf_ext_result_running_record", fields = {"businessRunningRouteKey"})
@DataPlugin(name = "DuplicateDataChecker")
public class ExternalResultBinding {

  public static final String STATUS_RUNNING = "RUNNING";
  public static final String STATUS_FINISHED = "FINISHED";
  public static final String STATUS_FAILED = "FAILED";

  @Field
  @PrimaryKey
  private String trackingId;

  @Field
  private String scope;

  @Field
  private String flowKey;

  @Field
  private String mappingId;

  @Field
  private Integer mappingVersion;

  /** 公共审批案件上下文；每个执行轮次固定不变。 */
  @Field(nullable = true)
  private String approvalCaseId;

  @Field(nullable = true)
  private Integer submissionRound;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  @Field(nullable = true)
  private String businessRecordType;

  @Field(nullable = true)
  private String businessRecordId;

  @Field
  private String externalInstanceId;

  /** 发起幂等键（同适配器/连接下唯一，用于重复发起去重）。 */
  @Field(nullable = true)
  private String idempotencyKey;

  /** 唯一事实键：adapterId + connectionId + externalInstanceId。 */
  @Field(size = 96)
  private String externalInstanceRouteKey;

  /** 唯一事实键：显式 idempotencyKey 绑定到 adapterId + connectionId。 */
  @Field(size = 96)
  private String idempotencyRouteKey;

  /** 唯一事实键：同 scope + flowKey + 业务记录仅允许一个 RUNNING 绑定。 */
  @Field(size = 96)
  private String businessRunningRouteKey;

  @Field
  private String externalDefinitionId;

  @Field
  private String status;

  @Field(nullable = true)
  private String outcome;

  @Field(nullable = true)
  private String externalRevision;

  @Field(nullable = true)
  private LocalDateTime externalEventTime;

  @Field(nullable = true)
  private String lastExternalEventId;

  @Field(nullable = true)
  private LocalDateTime finalResultAt;

  @Field(nullable = true)
  private String externalUrl;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  public ExternalResultBinding() {
  }

  public String getTrackingId() {
    return trackingId;
  }

  public void setTrackingId(String trackingId) {
    this.trackingId = trackingId;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getMappingId() {
    return mappingId;
  }

  public void setMappingId(String mappingId) {
    this.mappingId = mappingId;
  }

  public Integer getMappingVersion() {
    return mappingVersion;
  }

  public void setMappingVersion(Integer mappingVersion) {
    this.mappingVersion = mappingVersion;
  }

  public String getApprovalCaseId() {
    return approvalCaseId;
  }

  public void setApprovalCaseId(String approvalCaseId) {
    this.approvalCaseId = approvalCaseId;
  }

  public Integer getSubmissionRound() {
    return submissionRound;
  }

  public void setSubmissionRound(Integer submissionRound) {
    this.submissionRound = submissionRound;
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

  public String getBusinessRecordType() {
    return businessRecordType;
  }

  public void setBusinessRecordType(String businessRecordType) {
    this.businessRecordType = businessRecordType;
  }

  public String getBusinessRecordId() {
    return businessRecordId;
  }

  public void setBusinessRecordId(String businessRecordId) {
    this.businessRecordId = businessRecordId;
  }

  public String getExternalInstanceId() {
    return externalInstanceId;
  }

  public void setExternalInstanceId(String externalInstanceId) {
    this.externalInstanceId = externalInstanceId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getExternalInstanceRouteKey() {
    return externalInstanceRouteKey;
  }

  public void setExternalInstanceRouteKey(String externalInstanceRouteKey) {
    this.externalInstanceRouteKey = externalInstanceRouteKey;
  }

  public String getIdempotencyRouteKey() {
    return idempotencyRouteKey;
  }

  public void setIdempotencyRouteKey(String idempotencyRouteKey) {
    this.idempotencyRouteKey = idempotencyRouteKey;
  }

  public String getBusinessRunningRouteKey() {
    return businessRunningRouteKey;
  }

  public void setBusinessRunningRouteKey(String businessRunningRouteKey) {
    this.businessRunningRouteKey = businessRunningRouteKey;
  }

  public String getExternalDefinitionId() {
    return externalDefinitionId;
  }

  public void setExternalDefinitionId(String externalDefinitionId) {
    this.externalDefinitionId = externalDefinitionId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public String getExternalRevision() {
    return externalRevision;
  }

  public void setExternalRevision(String externalRevision) {
    this.externalRevision = externalRevision;
  }

  public LocalDateTime getExternalEventTime() {
    return externalEventTime;
  }

  public void setExternalEventTime(LocalDateTime externalEventTime) {
    this.externalEventTime = externalEventTime;
  }

  public String getLastExternalEventId() {
    return lastExternalEventId;
  }

  public void setLastExternalEventId(String lastExternalEventId) {
    this.lastExternalEventId = lastExternalEventId;
  }

  public LocalDateTime getFinalResultAt() {
    return finalResultAt;
  }

  public void setFinalResultAt(LocalDateTime finalResultAt) {
    this.finalResultAt = finalResultAt;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  public void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
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

  public boolean isRunning() {
    return STATUS_RUNNING.equals(status);
  }
}
