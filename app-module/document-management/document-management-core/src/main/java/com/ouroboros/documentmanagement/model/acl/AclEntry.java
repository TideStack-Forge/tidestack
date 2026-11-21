package com.ouroboros.documentmanagement.model.acl;

/**
 * 权限条目，表示某个主体对某个资源的权限
 */
public interface AclEntry {
  /**
   * 获取权限条目ID
   */
  String getId();

  /**
   * 获取资源ID（文档库、目录或文档的ID）
   */
  String getResourceId();

  /**
   * 获取资源类型
   */
  ResourceType getResourceType();

  /**
   * 获取文档库ID
   */
  String getLibraryId();

  /**
   * 获取授权对象标识
   */
  String getPrincipal();

  /**
   * 获取授权对象类型
   */
  String getPrincipalType();

  /**
   * 获取权限集合
   */
  PermissionSet getPermissions();

  /**
   * 创建 Builder
   */
  static Builder builder() {
    return AclEntryImpl.builder();
  }

  /**
   * 从当前对象创建 Builder（用于更新）
   */
  Builder toBuilder();

  /**
   * ACL 条目 Builder 接口
   */
  interface Builder {
    Builder id(String id);
    Builder resourceId(String resourceId);
    Builder resourceType(ResourceType resourceType);
    Builder libraryId(String libraryId);
    Builder principal(String principal);
    Builder principalType(String principalType);
    Builder permissions(PermissionSet permissions);
    AclEntry build();
  }
}
