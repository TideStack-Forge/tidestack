package com.ouroboros.documentmanagement.service;

import com.ouroboros.documentmanagement.model.DocumentLibrary;

import java.util.List;
import java.util.Optional;

/**
 * 文档库服务接口
 *
 * <p>职责：文档库的生命周期管理，包括创建、查询、更新、删除等操作
 *
 * <p>设计要点：
 * <ul>
 *   <li>原子化操作：每个方法都是独立的、完整的业务操作</li>
 *   <li>不涉及权限检查：权限检查由 ACLService 负责</li>
 *   <li>不涉及文件操作：文件操作由 LibraryContentService 和 StorageService 负责</li>
 *   <li>自动创建根目录：创建文档库时可选择是否自动创建根目录</li>
 * </ul>
 */
public interface DocumentLibraryService {

  // ==================== 创建和查询 ====================

  /**
   * 创建文档库
   *
   * <p>业务规则：
   * <ul>
   *   <li>创建者自动成为拥有者（owner）</li>
   *   <li>文档库名称必须唯一</li>
   *   <li>自动生成文档库ID</li>
   *   <li>自动设置创建时间和创建人</li>
   * </ul>
   *
   * @param documentLibrary 文档库对象（包含名称、描述、配额限制等）
   * @return 创建后的文档库（包含生成的ID）
   * @throws IllegalArgumentException 如果名称已存在或参数无效
   */
  DocumentLibrary createLibrary(DocumentLibrary documentLibrary);

  /**
   * 创建文档库并自动创建根目录
   *
   * @param documentLibrary 文档库对象
   * @param createRootDirectory 是否自动创建根目录
   * @return 创建后的文档库
   * @throws IllegalArgumentException 如果名称已存在或参数无效
   */
  DocumentLibrary createLibrary(DocumentLibrary documentLibrary, boolean createRootDirectory);

  /**
   * 根据ID获取文档库
   *
   * @param libraryId 文档库ID
   * @return 文档库，如果不存在则返回 empty
   */
  Optional<DocumentLibrary> getLibraryById(String libraryId);

  /**
   * 根据名称获取文档库
   *
   * @param libraryName 文档库名称
   * @return 文档库，如果不存在则返回 empty
   */
  Optional<DocumentLibrary> getLibraryByName(String libraryName);

  /**
   * 列出所有文档库（不包括已删除）
   *
   * @return 文档库列表
   */
  List<DocumentLibrary> listLibraries();

  /**
   * 列出所有文档库
   *
   * @param includeDeleted 是否包括已删除的文档库
   * @return 文档库列表
   */
  List<DocumentLibrary> listLibraries(boolean includeDeleted);

  /**
   * 列出用户拥有的文档库（作为 owner）
   *
   * @param userId 用户ID
   * @return 文档库列表
   */
  List<DocumentLibrary> listLibrariesByOwner(String userId);

  /**
   * 列出用户作为管理员的文档库
   *
   * @param userId 用户ID
   * @return 文档库列表
   */
  List<DocumentLibrary> listLibrariesByAdmin(String userId);

  /**
   * 列出用户可访问的文档库（拥有者 + 管理员 + 有权限访问）
   *
   * @param userId 用户ID
   * @return 文档库列表
   */
  List<DocumentLibrary> listAccessibleLibraries(String userId);

  // ==================== 更新操作 ====================

  /**
   * 更新文档库基本信息
   *
   * <p>可更新内容：
   * <ul>
   *   <li>名称（name、displayName）</li>
   *   <li>描述（description）</li>
   *   <li>配额限制（maxSizeLimit、maxFileCountLimit）</li>
   *   <li>启用状态（isDisabled）</li>
   *   <li>锁定状态（isLocked）</li>
   *   <li>回收站开关（isTrashEnabled）</li>
   * </ul>
   *
   * <p>不可更改内容：
   * <ul>
   *   <li>拥有者（owner）</li>
   *   <li>创建时间（createdAt）</li>
   *   <li>创建人（createdBy）</li>
   * </ul>
   *
   * @param documentLibrary 更新后的文档库对象（必须包含ID）
   * @param updatedBy 更新人ID
   * @return 更新后的文档库
   * @throws IllegalArgumentException 如果ID不存在或参数无效
   */
  DocumentLibrary updateLibrary(DocumentLibrary documentLibrary, String updatedBy);

  /**
   * 更新文档库配额限制
   *
   * @param libraryId 文档库ID
   * @param maxSizeLimit 最大存储空间（字节），null 表示不限制
   * @param maxFileCountLimit 最大文件数量，null 表示不限制
   * @param updatedBy 更新人ID
   * @return 更新后的文档库
   */
  DocumentLibrary updateQuota(String libraryId, Long maxSizeLimit, Long maxFileCountLimit, String updatedBy);

  /**
   * 启用文档库
   *
   * @param libraryId 文档库ID
   * @param updatedBy 更新人ID
   * @return 更新后的文档库
   */
  DocumentLibrary enableLibrary(String libraryId, String updatedBy);

  /**
   * 禁用文档库
   *
   * @param libraryId 文档库ID
   * @param updatedBy 更新人ID
   * @return 更新后的文档库
   */
  DocumentLibrary disableLibrary(String libraryId, String updatedBy);

  /**
   * 锁定文档库（禁止所有修改操作，只读）
   *
   * @param libraryId 文档库ID
   * @param updatedBy 更新人ID
   * @return 更新后的文档库
   */
  DocumentLibrary lockLibrary(String libraryId, String updatedBy);

  /**
   * 解锁文档库
   *
   * @param libraryId 文档库ID
   * @param updatedBy 更新人ID
   * @return 更新后的文档库
   */
  DocumentLibrary unlockLibrary(String libraryId, String updatedBy);

  /**
   * 启用回收站功能
   *
   * @param libraryId 文档库ID
   * @param updatedBy 更新人ID
   * @return 更新后的文档库
   */
  DocumentLibrary enableTrash(String libraryId, String updatedBy);

  /**
   * 禁用回收站功能
   *
   * @param libraryId 文档库ID
   * @param updatedBy 更新人ID
   * @return 更新后的文档库
   */
  DocumentLibrary disableTrash(String libraryId, String updatedBy);

  // ==================== 管理员管理 ====================

  /**
   * 添加文档库管理员
   *
   * @param libraryId 文档库ID
   * @param adminUserId 管理员用户ID
   * @param updatedBy 操作人ID
   * @return 更新后的文档库
   * @throws IllegalArgumentException 如果用户已经是管理员
   */
  DocumentLibrary addAdmin(String libraryId, String adminUserId, String updatedBy);

  /**
   * 移除文档库管理员
   *
   * @param libraryId 文档库ID
   * @param adminUserId 管理员用户ID
   * @param updatedBy 操作人ID
   * @return 更新后的文档库
   * @throws IllegalArgumentException 如果用户不是管理员
   */
  DocumentLibrary removeAdmin(String libraryId, String adminUserId, String updatedBy);

  /**
   * 批量添加管理员
   *
   * @param libraryId 文档库ID
   * @param adminUserIds 管理员用户ID列表
   * @param updatedBy 操作人ID
   * @return 更新后的文档库
   */
  DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String updatedBy);

  /**
   * 批量移除管理员
   *
   * @param libraryId 文档库ID
   * @param adminUserIds 管理员用户ID列表
   * @param updatedBy 操作人ID
   * @return 更新后的文档库
   */
  DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String updatedBy);

  /**
   * 检查用户是否是文档库管理员
   *
   * @param libraryId 文档库ID
   * @param userId 用户ID
   * @return 如果是管理员返回 true
   */
  boolean isAdmin(String libraryId, String userId);

  /**
   * 检查用户是否是文档库拥有者
   *
   * @param libraryId 文档库ID
   * @param userId 用户ID
   * @return 如果是拥有者返回 true
   */
  boolean isOwner(String libraryId, String userId);

  // ==================== 删除操作 ====================

  /**
   * 软删除文档库（标记为已删除，不实际删除数据）
   *
   * @param libraryId 文档库ID
   * @param deletedBy 删除人ID
   * @return 更新后的文档库
   */
  DocumentLibrary softDeleteLibrary(String libraryId, String deletedBy);

  /**
   * 恢复已删除的文档库
   *
   * @param libraryId 文档库ID
   * @param restoredBy 恢复人ID
   * @return 更新后的文档库
   */
  DocumentLibrary restoreLibrary(String libraryId, String restoredBy);

  /**
   * 硬删除文档库（物理删除，包括所有文档和权限数据）
   *
   * <p>警告：此操作不可恢复，会删除：
   * <ul>
   *   <li>文档库元数据</li>
   *   <li>所有目录和文档节点</li>
   *   <li>所有文件内容（通过 StorageService）</li>
   *   <li>所有权限条目</li>
   * </ul>
   *
   * @param libraryId 文档库ID
   * @throws IllegalStateException 如果文档库未被软删除
   */
  void hardDeleteLibrary(String libraryId);

  // ==================== 统计信息 ====================

  /**
   * 获取文档库统计信息
   *
   * @param libraryId 文档库ID
   * @return 统计信息
   */
  LibraryStatistics getStatistics(String libraryId);

  /**
   * 检查文档库配额是否超限
   *
   * @param libraryId 文档库ID
   * @return 如果超限返回 true
   */
  boolean isQuotaExceeded(String libraryId);

  /**
   * 检查是否可以上传指定大小的文件
   *
   * @param libraryId 文档库ID
   * @param fileSize 文件大小（字节）
   * @return 如果可以上传返回 true
   */
  boolean canUploadFile(String libraryId, long fileSize);

  /**
   * 检查文档库名称是否可用
   *
   * @param name 文档库名称
   * @return 如果名称可用返回 true
   */
  boolean isNameAvailable(String name);

  /**
   * 检查文档库名称是否可用（排除指定ID）
   *
   * @param name 文档库名称
   * @param excludeId 要排除的文档库ID
   * @return 如果名称可用返回 true
   */
  boolean isNameAvailable(String name, String excludeId);

  /**
   * 文档库统计信息
   */
  interface LibraryStatistics {
    /** 文档库ID */
    String getLibraryId();

    /** 文档总数 */
    long getDocumentCount();

    /** 目录总数 */
    long getDirectoryCount();

    /** 已使用存储空间（字节） */
    long getUsedSpace();

    /** 最大存储空间限制（字节），null 表示不限制 */
    Long getMaxSizeLimit();

    /** 最大文件数量限制，null 表示不限制 */
    Long getMaxFileCountLimit();

    /** 存储空间使用率（0.0 - 1.0），无限制时返回 0.0 */
    double getSpaceUsageRate();

    /** 文件数量使用率（0.0 - 1.0），无限制时返回 0.0 */
    double getFileCountUsageRate();

    /** 是否超过存储空间限制 */
    boolean isSpaceExceeded();

    /** 是否超过文件数量限制 */
    boolean isFileCountExceeded();
  }
}
