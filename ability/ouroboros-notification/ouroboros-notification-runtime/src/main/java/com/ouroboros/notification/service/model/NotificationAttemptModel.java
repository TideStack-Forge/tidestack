package com.ouroboros.notification.service.model;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/** Durable attempts on the existing notification service; no independent delivery queue. */
@Model(rawName = "t_notification_attempt", label = "通知发送尝试")
public class NotificationAttemptModel {
  @Field(size = 64)
  @PrimaryKey
  private String id;

  @Field(size = 255)
  private String unitId;

  @Field()
  private Integer attempt;

  @Field()
  private LocalDateTime startedAt;

  @Field()
  private LocalDateTime deadlineAt;

  @Field(nullable = true)
  private LocalDateTime completedAt;

  @Field(size = 32)
  private String outcome;

  @Field(nullable = true, size = 1024)
  private String diagnostic;

  @Field(nullable = true, size = 255)
  private String resolvedBy;

  @Field(nullable = true)
  private LocalDateTime resolvedAt;

  @Field(nullable = true, size = 2048)
  private String resolutionEvidence;

  @Field(nullable = true, size = 32)
  private String resolvedOutcome;

  public String getResolvedOutcome() { return resolvedOutcome; }

  public void setResolvedOutcome(String resolvedOutcome) { this.resolvedOutcome = resolvedOutcome; }

  public String getId() { return id; }

  public void setId(String id) { this.id = id; }

  public String getUnitId() { return unitId; }

  public void setUnitId(String unitId) { this.unitId = unitId; }

  public Integer getAttempt() { return attempt; }

  public void setAttempt(Integer attempt) { this.attempt = attempt; }

  public LocalDateTime getStartedAt() { return startedAt; }

  public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

  public LocalDateTime getDeadlineAt() { return deadlineAt; }

  public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }

  public LocalDateTime getCompletedAt() { return completedAt; }

  public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

  public String getOutcome() { return outcome; }

  public void setOutcome(String outcome) { this.outcome = outcome; }

  public String getDiagnostic() { return diagnostic; }

  public void setDiagnostic(String diagnostic) { this.diagnostic = diagnostic; }

  public String getResolvedBy() { return resolvedBy; }

  public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

  public LocalDateTime getResolvedAt() { return resolvedAt; }

  public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

  public String getResolutionEvidence() { return resolutionEvidence; }

  public void setResolutionEvidence(String resolutionEvidence) { this.resolutionEvidence = resolutionEvidence; }

}
