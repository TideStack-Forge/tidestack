package com.ouroboros.workflow.approval.external.runtime.action;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.DataPlugin;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/** 调用外部动作前持久化的可恢复事实。 */
@Model(rawName = "t_approval_workflow_external_action_fact", label = "审批流外部动作事实")
@UniqueConstraint(name = "uk_awf_ext_action_active", fields = {"activeRouteKey"})
@UniqueConstraint(name = "uk_awf_ext_action_idempotency", fields = {"idempotencyRouteKey"})
@DataPlugin(name = "DuplicateDataChecker")
public class ExternalActionFact {

  public static final String STATUS_EXECUTING = "EXECUTING";
  public static final String STATUS_UNKNOWN = "ACTION_UNKNOWN";
  public static final String STATUS_SUCCEEDED = "SUCCEEDED";
  public static final String STATUS_REJECTED = "REJECTED";
  public static final String STATUS_CONFLICT = "CONFLICT";

  public static final String PHASE_NOT_STARTED = "NOT_STARTED";
  public static final String PHASE_IN_FLIGHT = "IN_FLIGHT";
  public static final String PHASE_REPLIED = "REPLIED";

  public static final String TARGET_TASK = "TASK";
  public static final String TARGET_INSTANCE = "INSTANCE";
  public static final String TARGET_RESULT = "RESULT";

  @Field
  @PrimaryKey
  private String id;

  @Field(size = 96)
  private String activeRouteKey;

  @Field(size = 96)
  private String idempotencyRouteKey;

  @Field(size = 96)
  private String requestFingerprint;

  @Field
  private String targetType;

  @Field
  private String targetLocalId;

  @Field
  private String sourceState;

  @Field
  private String targetStatus;

  @Field(nullable = true)
  private String outcome;

  @Field(nullable = true)
  private String eventType;

  @Field
  private String canonicalAction;

  @Field
  private String adapterId;

  @Field
  private String connectionId;

  @Field
  private Integer mappingVersion;

  @Field(rawType = "longtext")
  private String commandJson;

  /** Trusted task ownership captured before the remote command, retained for local recovery. */
  @Field(rawType = "longtext", nullable = true)
  private String taskFactJson;

  @Field(rawType = "longtext", nullable = true)
  private String resultJson;

  @Field(nullable = true)
  private Boolean accepted;

  @Field
  private String status;

  @Field
  private String remoteCallPhase;

  @Field(nullable = true)
  private String claimToken;

  @Field(nullable = true)
  private LocalDateTime claimUntil;

  @Field(rawType = "longtext", nullable = true)
  private String lastError;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  /** Accepted reply time; claim renewal and recovery must not change the occurrence time. */
  @Field(nullable = true)
  private LocalDateTime repliedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getActiveRouteKey() { return activeRouteKey; }
  public void setActiveRouteKey(String activeRouteKey) { this.activeRouteKey = activeRouteKey; }
  public String getIdempotencyRouteKey() { return idempotencyRouteKey; }
  public void setIdempotencyRouteKey(String idempotencyRouteKey) { this.idempotencyRouteKey = idempotencyRouteKey; }
  public String getRequestFingerprint() { return requestFingerprint; }
  public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
  public String getTargetType() { return targetType; }
  public void setTargetType(String targetType) { this.targetType = targetType; }
  public String getTargetLocalId() { return targetLocalId; }
  public void setTargetLocalId(String targetLocalId) { this.targetLocalId = targetLocalId; }
  public String getSourceState() { return sourceState; }
  public void setSourceState(String sourceState) { this.sourceState = sourceState; }
  public String getTargetStatus() { return targetStatus; }
  public void setTargetStatus(String targetStatus) { this.targetStatus = targetStatus; }
  public String getOutcome() { return outcome; }
  public void setOutcome(String outcome) { this.outcome = outcome; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getCanonicalAction() { return canonicalAction; }
  public void setCanonicalAction(String canonicalAction) { this.canonicalAction = canonicalAction; }
  public String getAdapterId() { return adapterId; }
  public void setAdapterId(String adapterId) { this.adapterId = adapterId; }
  public String getConnectionId() { return connectionId; }
  public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
  public Integer getMappingVersion() { return mappingVersion; }
  public void setMappingVersion(Integer mappingVersion) { this.mappingVersion = mappingVersion; }
  public String getCommandJson() { return commandJson; }
  public void setCommandJson(String commandJson) { this.commandJson = commandJson; }
  public String getTaskFactJson() { return taskFactJson; }
  public void setTaskFactJson(String taskFactJson) { this.taskFactJson = taskFactJson; }
  public String getResultJson() { return resultJson; }
  public void setResultJson(String resultJson) { this.resultJson = resultJson; }
  public Boolean getAccepted() { return accepted; }
  public void setAccepted(Boolean accepted) { this.accepted = accepted; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getRemoteCallPhase() { return remoteCallPhase; }
  public void setRemoteCallPhase(String remoteCallPhase) { this.remoteCallPhase = remoteCallPhase; }
  public String getClaimToken() { return claimToken; }
  public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
  public LocalDateTime getClaimUntil() { return claimUntil; }
  public void setClaimUntil(LocalDateTime claimUntil) { this.claimUntil = claimUntil; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
  public LocalDateTime getRepliedAt() { return repliedAt; }
  public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }
}
