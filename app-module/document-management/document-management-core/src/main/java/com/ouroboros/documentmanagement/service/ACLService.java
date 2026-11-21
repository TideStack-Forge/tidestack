package com.ouroboros.documentmanagement.service;

import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.ResourceType;

import java.util.List;

/**
 * 权限控制服务接口，提供权限计算和权限检查功能
 */
public interface ACLService {
  /**
   * 检查用户对资源的有效权限（考虑继承）
   *
   * @param resourceId   资源ID
   * @param resourceType 资源类型
   * @param userId       用户ID
   * @return 有效权限集合
   */
  PermissionSet getEffectivePermissions(String resourceId, ResourceType resourceType,
                                        String userId);

  /**
   * 检查用户是否有指定权限
   *
   * @param resourceId   资源ID
   * @param resourceType 资源类型
   * @param userId       用户ID
   * @param permission   要检查的权限
   * @return 是否有权限
   */
  boolean hasPermission(String resourceId, ResourceType resourceType,
                        String userId, Permission permission);

  /**
   * 检查用户是否有任一指定权限
   *
   * @param resourceId   资源ID
   * @param resourceType 资源类型
   * @param userId       用户ID
   * @param permissions  要检查的权限列表
   * @return 是否有任一权限
   */
  boolean hasAnyPermission(String resourceId, ResourceType resourceType,
                           String userId, Permission... permissions);

  /**
   * 检查用户是否有所有指定权限
   *
   * @param resourceId   资源ID
   * @param resourceType 资源类型
   * @param userId       用户ID
   * @param permissions  要检查的权限列表
   * @return 是否有所有权限
   */
  boolean hasAllPermissions(String resourceId, ResourceType resourceType,
                            String userId, Permission... permissions);

  /**
   * 获取用户所有相关的权限条目
   *
   * <p>该方法会遍历所有的 PrincipalType，通过每个 PrincipalType 的 resolver
   * 获取用户所属的所有 principal（如用户所属的所有组、角色等），然后查询这些
   * principal 的所有权限条目。
   *
   * @param userId 用户ID
   * @return 该用户相关的所有权限条目列表（包含直接授予用户的权限以及通过组、角色等间接获得的权限）
   */
  List<AclEntry> getUserRelatedAclEntries(String userId);
}
