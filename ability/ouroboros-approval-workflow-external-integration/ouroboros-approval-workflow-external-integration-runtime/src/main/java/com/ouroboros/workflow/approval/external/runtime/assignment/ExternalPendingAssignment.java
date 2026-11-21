package com.ouroboros.workflow.approval.external.runtime.assignment;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * 外部任务处理人待解析队列：记录外部用户无法映射到平台用户的 assignment 事实。
 */
@Model(rawName = "t_approval_workflow_external_pending_assignment", label = "审批流外部待处理 assignment")
@UniqueConstraint(name = "uk_awf_ext_pending_assignment", fields = {"assignmentRouteKey"})
@DataPlugin(name = "DuplicateDataChecker")
public class ExternalPendingAssignment {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_RESOLVED = "RESOLVED";

  @Field
  @PrimaryKey
  private String id;

  @Field
  private String scope;

  @Field
  private String flowKey;

  @Field
  private String mappingId;

  @Field
  private Integer mappingVersion;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  @Field
  private String externalInstanceId;

  @Field
  private String externalTaskId;

  @Field
  private String externalUserId;

  @Field(size = 96)
  private String assignmentRouteKey;

  @Field
  private String reason;

  @Field
  private String status;

  @Field
  private LocalDateTime firstSeenAt;

  @Field
  private LocalDateTime lastSeenAt;

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

  public String getExternalUserId() {
    return externalUserId;
  }

  public void setExternalUserId(String externalUserId) {
    this.externalUserId = externalUserId;
  }

  public String getAssignmentRouteKey() {
    return assignmentRouteKey;
  }

  public void setAssignmentRouteKey(String assignmentRouteKey) {
    this.assignmentRouteKey = assignmentRouteKey;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getFirstSeenAt() {
    return firstSeenAt;
  }

  public void setFirstSeenAt(LocalDateTime firstSeenAt) {
    this.firstSeenAt = firstSeenAt;
  }

  public LocalDateTime getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(LocalDateTime lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }
}
