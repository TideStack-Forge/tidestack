package com.ouroboros.businessmodel.action.audit;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(fullName = "ouroboros.BusinessActionExecutionAudit",
    rawName = "t_business_action_execution_audit",
    label = "业务 Action 执行审计")
public class BusinessActionExecutionAuditModel {
  @Field
  @PrimaryKey
  private String id;

  @Field(nullable = true)
  private String parentExecutionId;

  @Field
  private String modelFullName;

  @Field
  private String modelRevision;

  @Field
  private String actionName;

  @Field(nullable = true)
  private String executionAuthorityName;

  @Field
  private String implementationRef;

  @Field
  private String transactionResult;

  @Field(nullable = true)
  private String failureType;

  @Field(nullable = true)
  private String actorRef;

  @Field(nullable = true)
  private String targetModelFullName;

  @Field(nullable = true)
  private String targetId;

  @Field(rawType = "longtext")
  private String writeSetJson;

  @Field(rawType = "longtext")
  private String eventIdsJson;

  @Field
  private LocalDateTime startedAt;

  @Field(nullable = true)
  private LocalDateTime completedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getParentExecutionId() {
    return parentExecutionId;
  }

  public void setParentExecutionId(String parentExecutionId) {
    this.parentExecutionId = parentExecutionId;
  }

  public String getModelFullName() {
    return modelFullName;
  }

  public void setModelFullName(String modelFullName) {
    this.modelFullName = modelFullName;
  }

  public String getModelRevision() {
    return modelRevision;
  }

  public void setModelRevision(String modelRevision) {
    this.modelRevision = modelRevision;
  }

  public String getActionName() {
    return actionName;
  }

  public void setActionName(String actionName) {
    this.actionName = actionName;
  }

  public String getExecutionAuthorityName() { return executionAuthorityName; }

  public void setExecutionAuthorityName(String executionAuthorityName) { this.executionAuthorityName = executionAuthorityName; }

  public String getImplementationRef() {
    return implementationRef;
  }

  public void setImplementationRef(String implementationRef) {
    this.implementationRef = implementationRef;
  }

  public String getTransactionResult() {
    return transactionResult;
  }

  public void setTransactionResult(String transactionResult) {
    this.transactionResult = transactionResult;
  }

  public String getFailureType() {
    return failureType;
  }

  public void setFailureType(String failureType) {
    this.failureType = failureType;
  }

  public String getActorRef() {
    return actorRef;
  }

  public void setActorRef(String actorRef) {
    this.actorRef = actorRef;
  }

  public String getTargetModelFullName() {
    return targetModelFullName;
  }

  public void setTargetModelFullName(String targetModelFullName) {
    this.targetModelFullName = targetModelFullName;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  public String getWriteSetJson() {
    return writeSetJson;
  }

  public void setWriteSetJson(String writeSetJson) {
    this.writeSetJson = writeSetJson;
  }

  public String getEventIdsJson() {
    return eventIdsJson;
  }

  public void setEventIdsJson(String eventIdsJson) {
    this.eventIdsJson = eventIdsJson;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime completedAt) {
    this.completedAt = completedAt;
  }
}
