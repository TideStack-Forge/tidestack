package com.ouroboros.organizationstructure.directory;

import java.time.LocalDateTime;
import com.ouroboros.data.annotation.*;

@Model(rawName = "t_org_external_binding", label = "DirectoryBinding")
@UniqueConstraint(name = "uk_org_ext_identity", fields = {"externalRouteKey"}, nullsDistinct = true)
@UniqueConstraint(name = "uk_org_ext_local", fields = {"localRouteKey"}, nullsDistinct = true)
@UniqueConstraint(name = "uk_org_ext_sync_owner", fields = {"syncOwnerKey"}, nullsDistinct = true)
@AuditFields
public class DirectoryBinding {
  @Field
  @PrimaryKey
  private String id;

  @Field
  private String connectionId;

  @Field
  private String objectType;

  @Field(size = 256)
  private String externalId;

  @Field
  private String localId;

  @Field(nullable = true, size = 64)
  private String externalRouteKey;

  @Field(nullable = true, size = 64)
  private String localRouteKey;

  @Field(nullable = true)
  private String syncOwnerKey;

  @Field(nullable = true)
  private String scopeId;

  @Field(nullable = true)
  private String managedFields;

  @Field
  private Boolean active;

  @Field(rawType = "longtext", nullable = true)
  private String appointmentIds;

  @Field
  private Long version;

  @Field
  private LocalDateTime updatedAt;

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
  public String getObjectType() { return objectType; }
  public void setObjectType(String value) { objectType = value; }
  public String getExternalId() { return externalId; }
  public void setExternalId(String value) { externalId = value; }
  public String getLocalId() { return localId; }
  public void setLocalId(String value) { localId = value; }
  public String getExternalRouteKey() { return externalRouteKey; }
  public void setExternalRouteKey(String value) { externalRouteKey = value; }
  public String getLocalRouteKey() { return localRouteKey; }
  public void setLocalRouteKey(String value) { localRouteKey = value; }
  public String getScopeId() { return scopeId; }
  public void setScopeId(String value) { scopeId = value; }
  public String getSyncOwnerKey() { return syncOwnerKey; }
  public void setSyncOwnerKey(String value) { syncOwnerKey = value; }
  public String getManagedFields() { return managedFields; }
  public void setManagedFields(String value) { managedFields = value; }
  public Boolean getActive() { return active; }
  public void setActive(Boolean value) { active = value; }
  public String getAppointmentIds() { return appointmentIds; }
  public void setAppointmentIds(String value) { appointmentIds = value; }
  public Long getVersion() { return version; }
  public void setVersion(Long value) { version = value; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
