package com.ouroboros.documentmanagement.webapp.dto;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.documentmanagement.model.acl.*;

/**
 * ACL 权限条目 DTO，包含主体显示名称
 */
public class AclEntryDTO {
  private String id;
  private String resourceId;
  private ResourceType resourceType;
  private String principalType;
  private String principalTypeDisplayName;
  private String principal;
  private String principalDisplayName;
  private List<String> permissions;
  private List<String> permissionDisplayNames;

  public AclEntryDTO() {}

  // Getters
  public String getId() {
    return id;
  }

  public String getResourceId() {
    return resourceId;
  }

  public ResourceType getResourceType() {
    return resourceType;
  }

  public String getPrincipalType() {
    return principalType;
  }

  public String getPrincipalTypeDisplayName() {
    return principalTypeDisplayName;
  }

  public String getPrincipal() {
    return principal;
  }

  public String getPrincipalDisplayName() {
    return principalDisplayName;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public List<String> getPermissionDisplayNames() {
    return permissionDisplayNames;
  }

  // Fluent setters
  public AclEntryDTO setId(String id) {
    this.id = id;
    return this;
  }

  public AclEntryDTO setResourceId(String resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public AclEntryDTO setResourceType(ResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public AclEntryDTO setPrincipalType(String principalType) {
    this.principalType = principalType;
    return this;
  }

  public AclEntryDTO setPrincipalTypeDisplayName(String principalTypeDisplayName) {
    this.principalTypeDisplayName = principalTypeDisplayName;
    return this;
  }

  public AclEntryDTO setPrincipal(String principal) {
    this.principal = principal;
    return this;
  }

  public AclEntryDTO setPrincipalDisplayName(String principalDisplayName) {
    this.principalDisplayName = principalDisplayName;
    return this;
  }

  public AclEntryDTO setPermissions(PermissionSet permissions) {
    this.permissions = permissions.getPermissions().stream()
        .map(Permission::getCode)
        .collect(Collectors.toList());
    this.permissionDisplayNames = permissions.getPermissions().stream()
        .map(Permission::getDisplayName)
        .collect(Collectors.toList());
    return this;
  }

  /**
   * 从 AclEntry 创建 AclEntryDTO
   *
   * @param entry ACL 条目
   * @return ACL 条目 DTO
   */
  public static AclEntryDTO fromAclEntry(AclEntry entry) {
    Optional<PrincipalType> type = PrincipalTypeProvider.get(entry.getPrincipalType());
    String principalTypeDisplayName = type.map(PrincipalType::getDisplayName).orElse(null);
    String principalDisplayName = type.map(PrincipalType::getResolver)
        .map(resolver -> resolver.getDisplayName(entry.getPrincipal()))
        .orElse(null);

    return new AclEntryDTO()
        .setId(entry.getId())
        .setResourceId(entry.getResourceId())
        .setResourceType(entry.getResourceType())
        .setPrincipalType(entry.getPrincipalType())
        .setPrincipalTypeDisplayName(principalTypeDisplayName)
        .setPrincipal(entry.getPrincipal())
        .setPrincipalDisplayName(principalDisplayName)
        .setPermissions(entry.getPermissions());
  }
}
