package com.ouroboros.businessmodel.event.store;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/** One durable delivery responsibility per immutable fact and consumer subscription. */
@Model(fullName = "ouroboros.BusinessEventDelivery", rawName = "t_business_event_delivery", label = "业务 Event 投递")
@UniqueConstraint(name = "uk_business_event_delivery", fields = {"eventId", "consumerModelFullName", "subscriptionName"})
public class BusinessEventDeliveryModel {

  @Field
  @PrimaryKey
  private String id;

  @Field
  private String eventId;

  @Field
  private String consumerModelFullName;

  @Field
  private String subscriptionName;

  @Field(nullable = true)
  private String matchedRevision;

  @Field(nullable = true)
  private String attemptRevision;

  @Field(nullable = true)
  private String targetRevision;

  @Field(nullable = true)
  private String implementationRef;

  @Field(nullable = true)
  private String implementationVersion;

  @Field
  private String executionMode;

  @Field(nullable = true)
  private String user;

  @Field
  private String status;

  @Field
  private Integer attempt;

  @Field
  private Integer retryCount;

  @Field(nullable = true)
  private String claimOwner;

  @Field(nullable = true)
  private String claimToken;

  @Field(nullable = true)
  private LocalDateTime claimUntil;

  @Field(nullable = true)
  private LocalDateTime nextAttemptAt;

  @Field(nullable = true)
  private String lastError;

  @Field
  private LocalDateTime createdAt;

  @Field
  private LocalDateTime updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getConsumerModelFullName() {
    return consumerModelFullName;
  }

  public void setConsumerModelFullName(String consumerModelFullName) {
    this.consumerModelFullName = consumerModelFullName;
  }

  public String getSubscriptionName() {
    return subscriptionName;
  }

  public void setSubscriptionName(String subscriptionName) {
    this.subscriptionName = subscriptionName;
  }

  public String getUser() { return user; }
  public void setUser(String value) { user = value; }

  public String getExecutionMode() { return executionMode; }
  public void setExecutionMode(String value) { executionMode = value; }

  public String getStatus() {
    return status;
  }

  public String getMatchedRevision() { return matchedRevision; }
  public void setMatchedRevision(String value) { matchedRevision = value; }
  public String getAttemptRevision() { return attemptRevision; }
  public void setAttemptRevision(String value) { attemptRevision = value; }
  public String getTargetRevision() { return targetRevision; }
  public void setTargetRevision(String value) { targetRevision = value; }
  public String getImplementationRef() { return implementationRef; }
  public void setImplementationRef(String value) { implementationRef = value; }
  public String getImplementationVersion() { return implementationVersion; }
  public void setImplementationVersion(String value) { implementationVersion = value; }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
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

  public LocalDateTime getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
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
