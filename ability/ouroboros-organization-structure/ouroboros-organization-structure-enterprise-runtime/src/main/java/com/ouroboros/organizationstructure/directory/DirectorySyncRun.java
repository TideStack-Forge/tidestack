package com.ouroboros.organizationstructure.directory;

import java.time.LocalDateTime;
import com.ouroboros.data.annotation.*;

@Model(rawName = "t_org_directory_run", label = "DirectorySyncRun")
@AuditFields
public class DirectorySyncRun {
  @Field
  @PrimaryKey
  private String id;

  @Field
  private String connectionId;

  @Field
  private String connectionRevision;

  @Field
  private String scopeId;

  @Field
  private Long scopeVersion;

  @Field
  private String status;

  @Field
  private Boolean complete;

  @Field(rawType = "longtext")
  private String snapshotJson;

  @Field
  private String baseline;

  @Field(rawType = "longtext", nullable = true)
  private String decisionsJson;

  @Field
  private LocalDateTime createdAt;

  @Field(nullable = true)
  private LocalDateTime appliedAt;

  @Field(nullable = true)
  private LocalDateTime snapshotClearedAt;

  @Field(nullable = true)
  private LocalDateTime detailsClearedAt;

  public LocalDateTime getSnapshotClearedAt() { return snapshotClearedAt; }
  public void setSnapshotClearedAt(LocalDateTime value) { snapshotClearedAt = value; }
  public LocalDateTime getDetailsClearedAt() { return detailsClearedAt; }
  public void setDetailsClearedAt(LocalDateTime value) { detailsClearedAt = value; }

  public String getDecisionsJson() { return decisionsJson; }
  public void setDecisionsJson(String value) { decisionsJson = value; }
  @Field(nullable = true)
  private String createdBy;

  @Field(nullable = true)
  private String updatedBy;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String value) { createdBy = value; }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String value) { updatedBy = value; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getConnectionId() { return connectionId; }
  public void setConnectionId(String value) { connectionId = value; }
  public String getConnectionRevision() { return connectionRevision; }
  public void setConnectionRevision(String value) { connectionRevision = value; }
  public String getScopeId() { return scopeId; }
  public void setScopeId(String value) { scopeId = value; }
  public Long getScopeVersion() { return scopeVersion; }
  public void setScopeVersion(Long value) { scopeVersion = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public Boolean getComplete() { return complete; }
  public void setComplete(Boolean value) { complete = value; }
  public String getSnapshotJson() { return snapshotJson; }
  public void setSnapshotJson(String value) { snapshotJson = value; }
  public String getBaseline() { return baseline; }
  public void setBaseline(String value) { baseline = value; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime value) { createdAt = value; }
  public LocalDateTime getAppliedAt() { return appliedAt; }
  public void setAppliedAt(LocalDateTime value) { appliedAt = value; }
}
