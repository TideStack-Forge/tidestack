package com.ouroboros.workflow.approval.external.runtime.start;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * 外部启动事实：在调用远程 start 前先占用本地唯一事实，防止业务并发产生重复远程副作用。
 */
@Model(rawName = "t_approval_workflow_external_start_fact", label = "审批流外部启动事实")
@UniqueConstraint(name = "uk_awf_ext_start_business", fields = {"businessStartRouteKey"})
@UniqueConstraint(name = "uk_awf_ext_start_idempotency", fields = {"idempotencyStartRouteKey"})
@DataPlugin(name = "DuplicateDataChecker")
public class ExternalStartFact {

  public static final String STATUS_STARTING = "STARTING";
  public static final String STATUS_STARTED = "STARTED";
  public static final String STATUS_START_UNKNOWN = "START_UNKNOWN";
  public static final String STATUS_FAILED = "FAILED";

  public static final String PHASE_NOT_STARTED = "NOT_STARTED";
  public static final String PHASE_IN_FLIGHT = "IN_FLIGHT";
  public static final String PHASE_REPLIED = "REPLIED";

  public static final String MODE_FULL_PROCESS = "FULL_PROCESS";
  public static final String MODE_RESULT_ONLY = "RESULT_ONLY";

  @Field
  @PrimaryKey
  private String id;

  @Field
  private String scope;

  @Field
  private String flowKey;

  @Field(nullable = true)
  private String businessRecordType;

  @Field(nullable = true)
  private String businessRecordId;

  @Field(nullable = true)
  private String idempotencyKey;

  @Field(size = 96)
  private String businessStartRouteKey;

  @Field(size = 96)
  private String idempotencyStartRouteKey;

  @Field(size = 96)
  private String requestFingerprint;

  @Field
  private String mappingId;

  @Field
  private Integer mappingVersion;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  @Field
  private String integrationMode;

  /** 公共审批案件上下文；为空时保持历史外部启动兼容。 */
  @Field(nullable = true)
  private String approvalCaseId;

  @Field(nullable = true)
  private Integer submissionRound;

  /** 启动前预分配的本地实例 ID 或结果跟踪 ID。 */
  @Field
  private String trackingId;

  @Field
  private String actorUserId;

  @Field(nullable = true)
  private String callbackFlow;

  /** 规范 ExternalStartCommand JSON，供未发出请求的过期事实安全恢复。 */
  @Field(rawType = "longtext")
  private String requestJson;

  @Field
  private String status;

  @Field
  private String remoteCallPhase;

  @Field(nullable = true)
  private String claimToken;

  @Field(nullable = true)
  private LocalDateTime claimUntil;

  @Field(nullable = true)
  private String externalInstanceId;

  @Field(nullable = true)
  private String externalUrl;

  @Field(rawType = "longtext", nullable = true)
  private String lastError;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  @Field(nullable = true)
  private LocalDateTime repliedAt;

  public LocalDateTime getRepliedAt() { return repliedAt; }
  public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getBusinessStartRouteKey() {
    return businessStartRouteKey;
  }

  public void setBusinessStartRouteKey(String businessStartRouteKey) {
    this.businessStartRouteKey = businessStartRouteKey;
  }

  public String getIdempotencyStartRouteKey() {
    return idempotencyStartRouteKey;
  }

  public void setIdempotencyStartRouteKey(String idempotencyStartRouteKey) {
    this.idempotencyStartRouteKey = idempotencyStartRouteKey;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public void setRequestFingerprint(String requestFingerprint) {
    this.requestFingerprint = requestFingerprint;
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

  public String getIntegrationMode() {
    return integrationMode;
  }

  public void setIntegrationMode(String integrationMode) {
    this.integrationMode = integrationMode;
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

  public String getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(String actorUserId) {
    this.actorUserId = actorUserId;
  }

  public String getCallbackFlow() {
    return callbackFlow;
  }

  public void setCallbackFlow(String callbackFlow) {
    this.callbackFlow = callbackFlow;
  }

  public String getRequestJson() {
    return requestJson;
  }

  public void setRequestJson(String requestJson) {
    this.requestJson = requestJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getRemoteCallPhase() {
    return remoteCallPhase;
  }

  public void setRemoteCallPhase(String remoteCallPhase) {
    this.remoteCallPhase = remoteCallPhase;
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

  public String getExternalInstanceId() {
    return externalInstanceId;
  }

  public void setExternalInstanceId(String externalInstanceId) {
    this.externalInstanceId = externalInstanceId;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  public void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
  }

  public String getTrackingId() {
    return trackingId;
  }

  public void setTrackingId(String trackingId) {
    this.trackingId = trackingId;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
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
