package com.ouroboros.workflow.approval.external.runtime.binding;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * FULL_PROCESS 外部实例绑定：本地实例 ID 与外部实例 ID 的持久关联，固定映射版本。
 */
@Model(rawName = "t_approval_workflow_external_instance_binding", label = "审批流外部实例绑定")
@UniqueConstraint(name = "uk_awf_ext_instance_external", fields = {"externalInstanceRouteKey"})
@DataPlugin(name = "DuplicateDataChecker")
public class ExternalInstanceBinding {

  public static final String STATUS_RUNNING = "RUNNING";
  public static final String STATUS_SUSPENDED = "SUSPENDED";
  public static final String STATUS_FINISHED = "FINISHED";

  @Field
  @PrimaryKey
  private String localInstanceId;

  @Field
  private String flowKey;

  @Field
  private String scope;

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

  @Field
  private String externalInstanceId;

  /** 唯一事实键：adapterId + connectionId + externalInstanceId。 */
  @Field(size = 96)
  private String externalInstanceRouteKey;

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
  private String externalUrl;

  /** 业务记录类型（统一结果事件业务上下文；Phase 2 扩展字段）。 */
  @Field(nullable = true)
  private String businessRecordType;

  /** 业务记录主键（统一结果事件业务上下文）。 */
  @Field(nullable = true)
  private String businessRecordId;

  /** 回调逻辑流绑定键（业务回调直连）。 */
  @Field(nullable = true)
  private String callbackFlow;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  public ExternalInstanceBinding() {
  }

  public String getLocalInstanceId() {
    return localInstanceId;
  }

  public void setLocalInstanceId(String localInstanceId) {
    this.localInstanceId = localInstanceId;
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
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

  public String getExternalInstanceId() {
    return externalInstanceId;
  }

  public void setExternalInstanceId(String externalInstanceId) {
    this.externalInstanceId = externalInstanceId;
  }

  public String getExternalInstanceRouteKey() {
    return externalInstanceRouteKey;
  }

  public void setExternalInstanceRouteKey(String externalInstanceRouteKey) {
    this.externalInstanceRouteKey = externalInstanceRouteKey;
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

  public String getExternalUrl() {
    return externalUrl;
  }

  public void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
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

  public String getCallbackFlow() {
    return callbackFlow;
  }

  public void setCallbackFlow(String callbackFlow) {
    this.callbackFlow = callbackFlow;
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
