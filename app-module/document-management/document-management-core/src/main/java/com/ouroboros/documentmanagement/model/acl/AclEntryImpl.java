package com.ouroboros.documentmanagement.model.acl;

import java.util.Objects;

/**
 * ACL 权限条目实现类（不可变对象）
 */
public class AclEntryImpl implements AclEntry {

  private final String id;
  private final String resourceId;
  private final ResourceType resourceType;
  private final String libraryId;
  private final String principal;
  private final String principalType;
  private final PermissionSet permissions;

  private AclEntryImpl(Builder builder) {
    this.id = builder.id;
    this.resourceId = builder.resourceId;
    this.resourceType = builder.resourceType;
    this.libraryId = builder.libraryId;
    this.principal = builder.principal;
    this.principalType = builder.principalType;
    this.permissions = builder.permissions;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getResourceId() {
    return resourceId;
  }

  @Override
  public ResourceType getResourceType() {
    return resourceType;
  }

  @Override
  public String getLibraryId() {
    return libraryId;
  }

  @Override
  public String getPrincipal() {
    return principal;
  }

  @Override
  public String getPrincipalType() {
    return principalType;
  }

  @Override
  public PermissionSet getPermissions() {
    return permissions;
  }

  /**
   * 创建 Builder
   */
  public static AclEntry.Builder builder() {
    return new Builder();
  }

  /**
   * 从现有对象创建 Builder（用于更新）
   */
  @Override
  public AclEntry.Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AclEntryImpl aclEntry = (AclEntryImpl) o;
    return Objects.equals(id, aclEntry.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "AclEntryImpl{" +
        "id='" + id + '\'' +
        ", resourceId='" + resourceId + '\'' +
        ", resourceType=" + resourceType +
        ", principal='" + principal + '\'' +
        ", principalType='" + principalType + '\'' +
        ", permissions=" + permissions +
        '}';
  }

  /**
   * ACL 条目 Builder 实现
   */
  public static class Builder implements AclEntry.Builder {
    private String id;
    private String resourceId;
    private ResourceType resourceType;
    private String libraryId;
    private String principal;
    private String principalType;
    private PermissionSet permissions;

    private Builder() {}

    private Builder(AclEntryImpl aclEntry) {
      this.id = aclEntry.id;
      this.resourceId = aclEntry.resourceId;
      this.resourceType = aclEntry.resourceType;
      this.libraryId = aclEntry.libraryId;
      this.principal = aclEntry.principal;
      this.principalType = aclEntry.principalType;
      this.permissions = aclEntry.permissions;
    }

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder resourceId(String resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder resourceType(ResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder libraryId(String libraryId) {
      this.libraryId = libraryId;
      return this;
    }

    public Builder principal(String principal) {
      this.principal = principal;
      return this;
    }

    public Builder principalType(String principalType) {
      this.principalType = principalType;
      return this;
    }

    public Builder permissions(PermissionSet permissions) {
      this.permissions = permissions;
      return this;
    }

    /**
     * 构建 AclEntry 对象
     */
    @Override
    public AclEntry build() {
      // 基本验证
      Objects.requireNonNull(resourceId, "resourceId 不能为 null");
      Objects.requireNonNull(resourceType, "resourceType 不能为 null");
      Objects.requireNonNull(libraryId, "libraryId 不能为 null");
      Objects.requireNonNull(principal, "principal 不能为 null");
      Objects.requireNonNull(principalType, "principalType 不能为 null");
      Objects.requireNonNull(permissions, "permissions 不能为 null");

      return new AclEntryImpl(this);
    }
  }
}
