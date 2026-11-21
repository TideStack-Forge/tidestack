package com.ouroboros.documentmanagement.service;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.service.DocumentLibraryService.LibraryStatistics;

import java.util.List;
import java.util.Optional;

/**
 * 档案库管理门面服务
 *
 * <p><b>职责边界</b>：从档案库所有者和管理员的角度提供档案库管理功能，包括：
 * <ul>
 *   <li>创建档案库（任何有权限的用户）</li>
 *   <li>查询档案库信息</li>
 *   <li>管理档案库基本信息（所有者操作）</li>
 *   <li>管理档案库管理员（所有者操作）</li>
 *   <li>转移所有权（所有者操作）</li>
 *   <li>管理档案库访问权限（所有者和管理员都可以）</li>
 *   <li>查询统计信息</li>
 * </ul>
 *
 * <p><b>权限控制</b>：
 * <ul>
 *   <li>所有者拥有所有管理权限</li>
 *   <li>管理员只能管理访问权限（添加/修改/删除 AclEntry）</li>
 *   <li>管理员不能修改基本信息、不能添加/删除管理员、不能转移所有权</li>
 * </ul>
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>方法命名体现业务含义和角色职责</li>
 *   <li>所有方法都包含 userId 参数用于权限验证</li>
 *   <li>明确区分所有者操作和管理员操作</li>
 *   <li>协调调用底层服务（DocumentLibraryService、PermissionAdminService、ACLService）</li>
 * </ul>
 */
public interface DocumentLibraryManagementFacade {

  // ==================== 档案库创建和查询 ====================

  /**
   * 创建档案库
   *
   * <p>业务规则：
   * <ul>
   *   <li>任何有权限的用户都可以创建档案库</li>
   *   <li>创建者自动成为所有者（owner）</li>
   *   <li>验证档案库名称是否已存在</li>
   *   <li>自动创建根目录</li>
   * </ul>
   *
   * @param name 档案库名称
   * @param displayName 档案库显示名称
   * @param description 档案库描述
   * @param userId 创建用户ID
   * @return 创建的档案库
   */
  DocumentLibrary createLibrary(
      String name, String displayName, String description, String userId);

  /**
   * 查询用户拥有的档案库列表（作为所有者）
   *
   * @param userId 用户ID
   * @return 档案库列表
   */
  List<DocumentLibrary> listOwnedLibraries(String userId);

  /**
   * 查询用户管理的档案库列表（作为管理员）
   *
   * @param userId 用户ID
   * @return 档案库列表
   */
  List<DocumentLibrary> listManagedLibraries(String userId);

  /**
   * 查询用户可访问的所有档案库（所有者 + 管理员 + 有权限访问）
   *
   * @param userId 用户ID
   * @return 档案库列表
   */
  List<DocumentLibrary> listAccessibleLibraries(String userId);

  /**
   * 根据ID获取档案库详情
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否是所有者或管理员</li>
   *   <li>或者验证用户是否有档案库的访问权限</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param userId 用户ID
   * @return 档案库，如果不存在或无权限则返回 empty
   */
  Optional<DocumentLibrary> getLibrary(String libraryId, String userId);

  /**
   * 根据名称获取档案库
   *
   * @param name 档案库名称
   * @param userId 用户ID
   * @return 档案库，如果不存在或无权限则返回 empty
   */
  Optional<DocumentLibrary> getLibraryByName(String name, String userId);

  // ==================== 基本信息管理（所有者操作）====================

  /**
   * 更新档案库基本信息（所有者操作）
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否是所有者</li>
   *   <li>可更新：名称、显示名称、描述</li>
   *   <li>不可更改：所有者、创建时间</li>
   *   <li>验证新名称是否与其他档案库冲突</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param name 新名称（null 表示不更新）
   * @param displayName 新显示名称（null 表示不更新）
   * @param description 新描述（null 表示不更新）
   * @param userId 操作用户ID（必须是所有者）
   * @return 更新后的档案库
   */
  DocumentLibrary updateLibraryInfo(
      String libraryId, String name, String displayName, String description, String userId);

  /**
   * 更新档案库配额限制（所有者操作）
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否是所有者</li>
   *   <li>更新最大存储空间和最大文件数量限制</li>
   *   <li>null 表示不限制</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param maxSizeLimit 最大存储空间（字节），null 表示不限制
   * @param maxFileCountLimit 最大文件数量，null 表示不限制
   * @param userId 操作用户ID（必须是所有者）
   * @return 更新后的档案库
   */
  DocumentLibrary updateLibraryQuota(
      String libraryId, Long maxSizeLimit, Long maxFileCountLimit, String userId);

  /**
   * 启用/禁用回收站功能（所有者操作）
   *
   * @param libraryId 档案库ID
   * @param enabled true 为启用，false 为禁用
   * @param userId 操作用户ID（必须是所有者）
   * @return 更新后的档案库
   */
  DocumentLibrary setTrashEnabled(String libraryId, boolean enabled, String userId);

  // ==================== 管理员管理（所有者操作）====================

  /**
   * 添加档案库管理员（所有者操作）
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否是所有者</li>
   *   <li>添加新的管理员</li>
   *   <li>管理员可以管理档案库的访问权限</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param adminUserId 要添加的管理员用户ID
   * @param userId 操作用户ID（必须是所有者）
   * @return 更新后的档案库
   */
  DocumentLibrary addAdmin(String libraryId, String adminUserId, String userId);

  /**
   * 移除档案库管理员（所有者操作）
   *
   * @param libraryId 档案库ID
   * @param adminUserId 要移除的管理员用户ID
   * @param userId 操作用户ID（必须是所有者）
   * @return 更新后的档案库
   */
  DocumentLibrary removeAdmin(String libraryId, String adminUserId, String userId);

  /**
   * 批量添加管理员（所有者操作）
   *
   * @param libraryId 档案库ID
   * @param adminUserIds 要添加的管理员用户ID列表
   * @param userId 操作用户ID（必须是所有者）
   * @return 更新后的档案库
   */
  DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String userId);

  /**
   * 批量移除管理员（所有者操作）
   *
   * @param libraryId 档案库ID
   * @param adminUserIds 要移除的管理员用户ID列表
   * @param userId 操作用户ID（必须是所有者）
   * @return 更新后的档案库
   */
  DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String userId);

  /**
   * 获取档案库的所有管理员列表
   *
   * @param libraryId 档案库ID
   * @param userId 查询用户ID
   * @return 管理员用户ID列表
   */
  List<String> listAdmins(String libraryId, String userId);

  // ==================== 所有权转移（所有者操作）====================

  /**
   * 转移档案库所有权（所有者操作）
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否是当前所有者</li>
   *   <li>将所有权转移给新用户</li>
   *   <li>原所有者自动成为管理员（可选）</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param newOwnerId 新所有者用户ID
   * @param keepAsAdmin 原所有者是否保留为管理员
   * @param userId 操作用户ID（必须是当前所有者）
   * @return 更新后的档案库
   */
  DocumentLibrary transferOwnership(
      String libraryId, String newOwnerId, boolean keepAsAdmin, String userId);

  // ==================== 访问权限管理（所有者和管理员都可以）====================

  /**
   * 查询档案库的所有访问权限条目
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否是所有者或管理员</li>
   *   <li>返回档案库级别的所有权限条目</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param userId 查询用户ID（所有者或管理员）
   * @return 权限条目列表
   */
  List<AclEntry> getLibraryPermissions(String libraryId, String userId);

  // ==================== 角色和权限检查 ====================

  /**
   * 检查用户是否是档案库所有者
   *
   * @param libraryId 档案库ID
   * @param userId 用户ID
   * @return 如果是所有者返回 true
   */
  boolean isOwner(String libraryId, String userId);

  /**
   * 检查用户是否是档案库管理员
   *
   * @param libraryId 档案库ID
   * @param userId 用户ID
   * @return 如果是管理员返回 true
   */
  boolean isAdmin(String libraryId, String userId);

  /**
   * 检查用户是否可以管理档案库（所有者或管理员）
   *
   * @param libraryId 档案库ID
   * @param userId 用户ID
   * @return 如果可以管理返回 true
   */
  boolean canManageLibrary(String libraryId, String userId);

  // ==================== 统计信息 ====================

  /**
   * 获取档案库统计信息
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否是所有者或管理员</li>
   *   <li>返回文档数、目录数、存储空间使用、配额等统计信息</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param userId 查询用户ID
   * @return 统计信息
   */
  LibraryStatistics getLibraryStatistics(String libraryId, String userId);

  /**
   * 检查档案库配额是否超限
   *
   * @param libraryId 档案库ID
   * @param userId 查询用户ID
   * @return 如果超限返回 true
   */
  boolean isQuotaExceeded(String libraryId, String userId);

  /**
   * 检查是否可以上传指定大小的文件
   *
   * @param libraryId 档案库ID
   * @param fileSize 文件大小（字节）
   * @param userId 查询用户ID
   * @return 如果可以上传返回 true
   */
  boolean canUploadFile(String libraryId, long fileSize, String userId);
}
