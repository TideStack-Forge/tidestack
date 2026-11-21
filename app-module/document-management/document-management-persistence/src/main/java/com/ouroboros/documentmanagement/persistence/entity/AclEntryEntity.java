package com.ouroboros.documentmanagement.persistence.entity;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限条目实体
 */
@Model(label = "权限条目", rawName = "doc_acl_entry")
public class AclEntryEntity {

  @PrimaryKey(generator = "snowflake")
  @Field(label = "ID")
  private String id;

  @Field(label = "资源ID", size = 100, nullable = false)
  private String resourceId;

  @Field(label = "资源类型", size = 50, nullable = false)
  private String resourceType;  // LIBRARY, DIRECTORY, or DOCUMENT

  @Field(label = "文档库ID", size = 100, nullable = false)
  private String libraryId;

  @Field(label = "主体标识", size = 255, nullable = false)
  private String principal;

  @Field(label = "主体类型", size = 50, nullable = false)
  private String principalType;

  @Field(label = "权限集合", nullable = false)
  private List<String> permissions = new ArrayList<>();

  // Getters and Setters

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public String getLibraryId() {
    return libraryId;
  }

  public void setLibraryId(String libraryId) {
    this.libraryId = libraryId;
  }

  public String getPrincipal() {
    return principal;
  }

  public void setPrincipal(String principal) {
    this.principal = principal;
  }

  public String getPrincipalType() {
    return principalType;
  }

  public void setPrincipalType(String principalType) {
    this.principalType = principalType;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(List<String> permissions) {
    this.permissions = permissions;
  }
}
