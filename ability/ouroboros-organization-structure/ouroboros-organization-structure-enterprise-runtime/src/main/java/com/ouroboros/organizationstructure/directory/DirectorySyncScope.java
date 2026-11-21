package com.ouroboros.organizationstructure.directory;

import java.time.LocalDateTime;
import com.ouroboros.data.annotation.*;

@Model(rawName = "t_org_directory_scope", label = "DirectorySyncScope")
@UniqueConstraint(name = "uk_org_directory_source", fields = {"sourceRouteKey"}, nullsDistinct = true)
@AuditFields
public class DirectorySyncScope {
  @Field
  @PrimaryKey
  private String id;

  @Field
  private String connectionId;

  @Field(size = 256)
  private String externalRootId;

  @Field(nullable = true, size = 64)
  private String sourceRouteKey;

  @Field
  private Boolean enabled;

  @Field
  private Boolean syncMobile;

  @Field
  private Boolean syncEmail;

  @Field
  private Long version;

  @Field
  private LocalDateTime updatedAt;

  public String getSourceRouteKey() { return sourceRouteKey; }
  public void setSourceRouteKey(String value) { sourceRouteKey = value; }
  @Field(nullable = true)
  private String createdBy;

  @Field(nullable = true)
  private String updatedBy;

  @Field(nullable = true)
  private LocalDateTime createdAt;

  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String value) { createdBy = value; }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String value) { updatedBy = value; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime value) { createdAt = value; }
  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getConnectionId() { return connectionId; }
  public void setConnectionId(String value) { connectionId = value; }
  public String getExternalRootId() { return externalRootId; }
  public void setExternalRootId(String value) { externalRootId = value; }
  public Boolean getEnabled() { return enabled; }
  public void setEnabled(Boolean value) { enabled = value; }
  public Boolean getSyncMobile() { return syncMobile; }
  public void setSyncMobile(Boolean value) { syncMobile = value; }
  public Boolean getSyncEmail() { return syncEmail; }
  public void setSyncEmail(Boolean value) { syncEmail = value; }
  public Long getVersion() { return version; }
  public void setVersion(Long value) { version = value; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
