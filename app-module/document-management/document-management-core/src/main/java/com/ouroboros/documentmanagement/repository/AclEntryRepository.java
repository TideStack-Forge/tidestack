package com.ouroboros.documentmanagement.repository;

import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.ResourceType;

import java.util.List;
import java.util.Optional;

/**
 * 权限条目持久化接口
 *
 * <p>负责 ACL（Access Control List）条目的存储和检索，管理资源与主体之间的权限关系。
 *
 * <p>设计说明：
 * <ul>
 *   <li>支持按资源查询权限条目</li>
 *   <li>支持按主体查询权限条目</li>
 *   <li>提供批量操作支持</li>
 *   <li>权限条目独立于资源存储，便于权限管理</li>
 * </ul>
 */
public interface AclEntryRepository {

  /**
   * 保存权限条目
   *
   * @param entry 权限条目
   * @return 保存后的权限条目
   */
  AclEntry save(AclEntry entry);

  /**
   * 添加权限条目（直接插入）
   *
   * @param entry 权限条目
   * @return 添加后的权限条目
   */
  AclEntry add(AclEntry entry);

  /**
   * 更新权限条目（直接更新）
   *
   * @param entry 权限条目
   * @return 更新后的权限条目
   */
  AclEntry update(AclEntry entry);

  /**
   * 批量保存权限条目
   *
   * @param entries 权限条目列表
   * @return 保存后的权限条目列表
   */
  List<AclEntry> saveAll(List<AclEntry> entries);

  /**
   * 根据ID查询权限条目
   *
   * @param id 权限条目ID
   * @return 权限条目，如果不存在则返回 empty
   */
  Optional<AclEntry> findById(String id);

  /**
   * 查询指定资源的所有权限条目
   *
   * @param resourceId 资源ID
   * @param resourceType 资源类型
   * @return 权限条目列表
   */
  List<AclEntry> findByResource(String resourceId, ResourceType resourceType);

  /**
   * 查询指定资源和主体的权限条目
   *
   * @param resourceId 资源ID
   * @param resourceType 资源类型
   * @param principal 主体标识
   * @param principalType 主体类型
   * @return 权限条目，如果不存在则返回 empty
   */
  Optional<AclEntry> findByResourceAndPrincipal(
      String resourceId, ResourceType resourceType, String principal, String principalType);

  /**
   * 查询指定主体的所有权限条目
   *
   * @param principal 主体标识
   * @param principalType 主体类型
   * @return 权限条目列表
   */
  List<AclEntry> findByPrincipal(String principal, String principalType);

  /**
   * 查询指定主体在某种资源类型上的所有权限条目
   *
   * @param principal 主体标识
   * @param principalType 主体类型
   * @param resourceType 资源类型
   * @return 权限条目列表
   */
  List<AclEntry> findByPrincipalAndResourceType(
      String principal, String principalType, ResourceType resourceType);

  /**
   * 查询文档库及其所有节点的权限条目
   *
   * @param libraryId 文档库ID
   * @return 权限条目列表
   */
  List<AclEntry> findByLibrary(String libraryId);

  /**
   * 查询用户在指定文档库的权限条目
   *
   * @param libraryId 文档库ID
   * @param principal 主体标识
   * @param principalType 主体类型
   * @return 权限条目列表
   */
  List<AclEntry> findByLibraryAndPrincipal(String libraryId, String principal, String principalType);

  /**
   * 检查指定资源和主体的权限条目是否存在
   *
   * @param resourceId 资源ID
   * @param resourceType 资源类型
   * @param principal 主体标识
   * @param principalType 主体类型
   * @return 如果存在返回 true
   */
  boolean existsByResourceAndPrincipal(
      String resourceId, ResourceType resourceType, String principal, String principalType);

  /**
   * 删除权限条目（物理删除）
   *
   * @param id 权限条目ID
   */
  void deleteById(String id);

  /**
   * 批量删除权限条目（物理删除）
   *
   * @param ids 权限条目ID列表
   */
  void deleteByIds(List<String> ids);

  /**
   * 删除指定资源的所有权限条目
   *
   * @param resourceId 资源ID
   * @param resourceType 资源类型
   */
  void deleteByResource(String resourceId, ResourceType resourceType);

  /**
   * 删除指定资源和主体的权限条目
   *
   * @param resourceId 资源ID
   * @param resourceType 资源类型
   * @param principal 主体标识
   * @param principalType 主体类型
   */
  void deleteByResourceAndPrincipal(
      String resourceId, ResourceType resourceType, String principal, String principalType);

  /**
   * 删除指定主体的所有权限条目
   *
   * @param principal 主体标识
   * @param principalType 主体类型
   */
  void deleteByPrincipal(String principal, String principalType);

  /**
   * 删除文档库及其所有节点的权限条目
   *
   * @param libraryId 文档库ID
   */
  void deleteByLibrary(String libraryId);

  /**
   * 统计指定资源的权限条目数量
   *
   * @param resourceId 资源ID
   * @param resourceType 资源类型
   * @return 权限条目数量
   */
  long countByResource(String resourceId, ResourceType resourceType);

  /**
   * 统计指定主体的权限条目数量
   *
   * @param principal 主体标识
   * @param principalType 主体类型
   * @return 权限条目数量
   */
  long countByPrincipal(String principal, String principalType);
}
