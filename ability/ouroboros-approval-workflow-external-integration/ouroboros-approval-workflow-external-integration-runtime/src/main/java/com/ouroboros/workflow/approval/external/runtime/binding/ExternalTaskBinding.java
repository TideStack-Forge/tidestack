package com.ouroboros.workflow.approval.external.runtime.binding;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * FULL_PROCESS 外部任务绑定：本地任务 ID 与外部任务 ID 的持久关联。
 */
@Model(rawName = "t_approval_workflow_external_task_binding", label = "审批流外部任务绑定")
@UniqueConstraint(name = "uk_awf_ext_task_external", fields = {"externalTaskRouteKey"})
@DataPlugin(name = "DuplicateDataChecker")
public class ExternalTaskBinding {

  public static final String STATUS_CREATED = "CREATED";
  public static final String STATUS_PROCESSING = "PROCESSING";
  public static final String STATUS_APPROVED = "APPROVED";
  public static final String STATUS_REJECTED = "REJECTED";
  public static final String STATUS_CANCELLED = "CANCELLED";

  @Field
  @PrimaryKey
  private String localTaskId;

  @Field
  private String localInstanceId;

  @Field
  private String scope;

  @Field
  private String mappingId;

  @Field
  private Integer mappingVersion;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  @Field
  private String externalTaskId;

  /** 唯一事实键：adapterId + connectionId + externalTaskId。 */
  @Field(size = 96)
  private String externalTaskRouteKey;

  @Field
  private String nodeId;

  /** 平台规范用户 ID（外部用户经 ExternalUserIdentityMapper 解析后写入）。 */
  @Field
  private String assignee;

  @Field
  private String status;

  @Field
  private String externalRevision;

  @Field
  private LocalDateTime externalEventTime;

  @Field
  private String lastExternalEventId;

  @Field
  private String externalUrl;

  @Field
  private LocalDateTime updatedAt;

  public ExternalTaskBinding() {
  }

  public String getLocalTaskId() {
    return localTaskId;
  }

  public void setLocalTaskId(String localTaskId) {
    this.localTaskId = localTaskId;
  }

  public String getLocalInstanceId() {
    return localInstanceId;
  }

  public void setLocalInstanceId(String localInstanceId) {
    this.localInstanceId = localInstanceId;
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

  public String getExternalTaskId() {
    return externalTaskId;
  }

  public void setExternalTaskId(String externalTaskId) {
    this.externalTaskId = externalTaskId;
  }

  public String getExternalTaskRouteKey() {
    return externalTaskRouteKey;
  }

  public void setExternalTaskRouteKey(String externalTaskRouteKey) {
    this.externalTaskRouteKey = externalTaskRouteKey;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
