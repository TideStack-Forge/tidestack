package com.ouroboros.documentmanagement.service;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.service.DocumentLibraryService.LibraryStatistics;

import java.util.List;
import java.util.Optional;

/**
 * 档案库后台管理门面服务
 *
 * <p><b>职责边界</b>：从模块管理员的角度提供档案库的后台管理功能，包括：
 * <ul>
 *   <li>查询所有档案库（包括已删除、已禁用、已锁定）</li>
 *   <li>系统级所有权转移（不验证权限）</li>
 *   <li>系统级管理员管理（不验证权限）</li>
 *   <li>配额管理</li>
 *   <li>档案库状态管理（启用/禁用、锁定/解锁）</li>
 *   <li>删除操作（软删除、硬删除、恢复）</li>
 *   <li>权限查询和管理</li>
 *   <li>统计和监控</li>
 * </ul>
 *
 * <p><b>权限控制</b>：
 * <ul>
 *   <li><b>所有操作均不验证权限</b>，因为模块管理员属于系统级管理员</li>
 *   <li>方法参数中的 operatorId 仅用于审计追踪，不用于权限验证</li>
 *   <li>可以对任何档案库执行任何操作，包括修改所有者、管理员、权限等</li>
 * </ul>
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>所有方法都包含 operatorId 参数用于审计日志记录</li>
 *   <li>提供完整的档案库生命周期管理能力</li>
 *   <li>支持批量操作以提高管理效率</li>
 *   <li>协调调用底层服务完成复杂的管理操作</li>
 * </ul>
 */
public interface DocumentLibraryAdminFacade {

  // ==================== 档案库查询 ====================

  /**
   * 查询所有档案库
   *
   * <p>业务规则：
   * <ul>
   *   <li>返回所有档案库，包括已删除、已禁用、已锁定的档案库</li>
   *   <li>不进行权限验证</li>
   * </ul>
   *
   * @return 所有档案库列表
   */
  List<DocumentLibrary> listAllLibraries();

  /**
   * 查询所有档案库（包含过滤条件）
   *
   * <p>业务规则：
   * <ul>
   *   <li>根据过滤条件返回档案库列表</li>
   *   <li>可以按状态（启用/禁用）、锁定状态、删除状态进行过滤</li>
   * </ul>
   *
   * @param includeDeleted 是否包含已删除的档案库
   * @param includeDisabled 是否包含已禁用的档案库
   * @param includeLocked 是否包含已锁定的档案库
   * @return 符合条件的档案库列表
   */
  List<DocumentLibrary> listLibraries(
      boolean includeDeleted, boolean includeDisabled, boolean includeLocked);

  /**
   * 根据所有者查询档案库
   *
   * @param ownerId 所有者用户ID
   * @return 该所有者的档案库列表
   */
  List<DocumentLibrary> listLibrariesByOwner(String ownerId);

  /**
   * 根据管理员查询档案库
   *
   * @param adminId 管理员用户ID
   * @return 该管理员管理的档案库列表
   */
  List<DocumentLibrary> listLibrariesByAdmin(String adminId);

  /**
   * 根据ID获取档案库详情
   *
   * <p>业务规则：
   * <ul>
   *   <li>返回完整的档案库信息</li>
   *   <li>即使档案库已删除、已禁用或已锁定，也能查询到</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @return 档案库，如果不存在则返回 empty
   */
  Optional<DocumentLibrary> getLibrary(String libraryId);

  /**
   * 根据名称获取档案库
   *
   * @param name 档案库名称
   * @return 档案库，如果不存在则返回 empty
   */
  Optional<DocumentLibrary> getLibraryByName(String name);

  // ==================== 所有权和管理员管理（系统级）====================

  /**
   * 转移档案库所有权（系统级操作）
   *
   * <p>业务规则：
   * <ul>
   *   <li><b>不验证操作者权限</b></li>
   *   <li>直接将档案库所有权转移给新用户</li>
   *   <li>可选择是否将原所有者保留为管理员</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param newOwnerId 新所有者用户ID
   * @param keepOriginalOwnerAsAdmin 原所有者是否保留为管理员
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary transferOwnership(
      String libraryId, String newOwnerId, boolean keepOriginalOwnerAsAdmin, String operatorId);

  /**
   * 添加档案库管理员（系统级操作）
   *
   * <p>业务规则：
   * <ul>
   *   <li><b>不验证操作者权限</b></li>
   *   <li>直接添加管理员</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param adminUserId 要添加的管理员用户ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary addAdmin(String libraryId, String adminUserId, String operatorId);

  /**
   * 移除档案库管理员（系统级操作）
   *
   * @param libraryId 档案库ID
   * @param adminUserId 要移除的管理员用户ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary removeAdmin(String libraryId, String adminUserId, String operatorId);

  /**
   * 批量添加管理员（系统级操作）
   *
   * @param libraryId 档案库ID
   * @param adminUserIds 要添加的管理员用户ID列表
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String operatorId);

  /**
   * 批量移除管理员（系统级操作）
   *
   * @param libraryId 档案库ID
   * @param adminUserIds 要移除的管理员用户ID列表
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String operatorId);

  /**
   * 设置档案库所有者（系统级操作）
   *
   * <p>业务规则：
   * <ul>
   *   <li>直接设置新的所有者，不保留原所有者</li>
   *   <li>与 transferOwnership 的区别是更加直接，不考虑原所有者的保留</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param newOwnerId 新所有者用户ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary setOwner(String libraryId, String newOwnerId, String operatorId);

  /**
   * 批量设置管理员（系统级操作）
   *
   * <p>业务规则：
   * <ul>
   *   <li>替换现有的所有管理员</li>
   *   <li>先清空现有管理员，再设置新的管理员列表</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param adminUserIds 新的管理员用户ID列表
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary setAdmins(String libraryId, List<String> adminUserIds, String operatorId);

  /**
   * 获取档案库的所有管理员列表
   *
   * @param libraryId 档案库ID
   * @return 管理员用户ID列表
   */
  List<String> listAdmins(String libraryId);

  // ==================== 配额管理 ====================

  /**
   * 更新档案库配额限制
   *
   * <p>业务规则：
   * <ul>
   *   <li>可以设置或更新最大存储空间和最大文件数量</li>
   *   <li>null 表示不限制</li>
   *   <li>可以设置为 0 来禁止上传</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param maxSizeLimit 最大存储空间（字节），null 表示不限制
   * @param maxFileCountLimit 最大文件数量，null 表示不限制
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary updateQuota(
      String libraryId, Long maxSizeLimit, Long maxFileCountLimit, String operatorId);

  /**
   * 移除档案库配额限制（设置为不限制）
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary removeQuota(String libraryId, String operatorId);

  /**
   * 批量更新多个档案库的配额
   *
   * @param libraryIds 档案库ID列表
   * @param maxSizeLimit 最大存储空间（字节）
   * @param maxFileCountLimit 最大文件数量
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库列表
   */
  List<DocumentLibrary> batchUpdateQuota(
      List<String> libraryIds, Long maxSizeLimit, Long maxFileCountLimit, String operatorId);

  // ==================== 档案库状态管理 ====================

  /**
   * 启用档案库
   *
   * <p>业务规则：
   * <ul>
   *   <li>设置档案库为启用状态</li>
   *   <li>启用后用户可以正常访问</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary enableLibrary(String libraryId, String operatorId);

  /**
   * 禁用档案库
   *
   * <p>业务规则：
   * <ul>
   *   <li>设置档案库为禁用状态</li>
   *   <li>禁用后除管理员外，其他用户无法访问</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary disableLibrary(String libraryId, String operatorId);

  /**
   * 锁定档案库
   *
   * <p>业务规则：
   * <ul>
   *   <li>锁定后档案库变为只读</li>
   *   <li>不能上传、删除、修改文件</li>
   *   <li>可以查看和下载</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary lockLibrary(String libraryId, String operatorId);

  /**
   * 解锁档案库
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary unlockLibrary(String libraryId, String operatorId);

  /**
   * 启用回收站功能
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary enableTrash(String libraryId, String operatorId);

  /**
   * 禁用回收站功能
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary disableTrash(String libraryId, String operatorId);

  /**
   * 批量启用档案库
   *
   * @param libraryIds 档案库ID列表
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库列表
   */
  List<DocumentLibrary> batchEnableLibraries(List<String> libraryIds, String operatorId);

  /**
   * 批量禁用档案库
   *
   * @param libraryIds 档案库ID列表
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库列表
   */
  List<DocumentLibrary> batchDisableLibraries(List<String> libraryIds, String operatorId);

  // ==================== 删除操作 ====================

  /**
   * 软删除档案库
   *
   * <p>业务规则：
   * <ul>
   *   <li>标记档案库为已删除状态</li>
   *   <li>不物理删除数据</li>
   *   <li>可以通过 restoreLibrary 恢复</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库
   */
  DocumentLibrary softDeleteLibrary(String libraryId, String operatorId);

  /**
   * 恢复已删除的档案库
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   * @return 恢复后的档案库
   */
  DocumentLibrary restoreLibrary(String libraryId, String operatorId);

  /**
   * 硬删除档案库
   *
   * <p>业务规则：
   * <ul>
   *   <li><b>物理删除档案库及其所有数据</b></li>
   *   <li>删除档案库元数据</li>
   *   <li>删除所有目录和文档节点</li>
   *   <li>删除所有权限条目</li>
   *   <li>删除所有存储的文件</li>
   *   <li><b>此操作不可恢复</b></li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   */
  void hardDeleteLibrary(String libraryId, String operatorId);

  /**
   * 批量软删除档案库
   *
   * @param libraryIds 档案库ID列表
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的档案库列表
   */
  List<DocumentLibrary> batchSoftDeleteLibraries(List<String> libraryIds, String operatorId);

  /**
   * 批量恢复档案库
   *
   * @param libraryIds 档案库ID列表
   * @param operatorId 操作者ID（用于审计）
   * @return 恢复后的档案库列表
   */
  List<DocumentLibrary> batchRestoreLibraries(List<String> libraryIds, String operatorId);

  /**
   * 批量硬删除档案库
   *
   * <p><b>警告：此操作不可恢复</b>
   *
   * @param libraryIds 档案库ID列表
   * @param operatorId 操作者ID（用于审计）
   */
  void batchHardDeleteLibraries(List<String> libraryIds, String operatorId);

  /**
   * 清理已软删除的档案库
   *
   * <p>业务规则：
   * <ul>
   *   <li>查找所有已软删除超过指定天数的档案库</li>
   *   <li>执行硬删除操作</li>
   *   <li>用于定期清理任务</li>
   * </ul>
   *
   * @param retentionDays 保留天数，超过此天数的软删除档案库将被硬删除
   * @param operatorId 操作者ID（用于审计）
   * @return 被清理的档案库数量
   */
  int cleanupDeletedLibraries(int retentionDays, String operatorId);

  // ==================== 权限查询和管理 ====================

  /**
   * 查询档案库的所有访问权限条目
   *
   * @param libraryId 档案库ID
   * @return 权限条目列表
   */
  List<AclEntry> getLibraryPermissions(String libraryId);

  /**
   * 为档案库授予访问权限
   *
   * <p>业务规则：
   * <ul>
   *   <li>添加或更新权限条目</li>
   *   <li>如果已存在，则合并权限</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param principal 主体标识
   * @param principalType 主体类型
   * @param permissions 要授予的权限集合
   * @param operatorId 操作者ID（用于审计）
   * @return 创建或更新的权限条目
   */
  AclEntry grantLibraryPermission(
      String libraryId,
      String principal,
      String principalType,
      PermissionSet permissions,
      String operatorId);

  /**
   * 更新档案库的访问权限
   *
   * <p>业务规则：
   * <ul>
   *   <li>更新权限条目（替换权限，而不是合并）</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param principal 主体标识
   * @param principalType 主体类型
   * @param permissions 新的权限集合
   * @param operatorId 操作者ID（用于审计）
   * @return 更新后的权限条目
   */
  AclEntry updateLibraryPermission(
      String libraryId,
      String principal,
      String principalType,
      PermissionSet permissions,
      String operatorId);

  /**
   * 撤销档案库的访问权限
   *
   * @param libraryId 档案库ID
   * @param principal 主体标识
   * @param principalType 主体类型
   * @param operatorId 操作者ID（用于审计）
   */
  void revokeLibraryPermission(
      String libraryId, String principal, String principalType, String operatorId);

  /**
   * 批量授予档案库访问权限
   *
   * @param libraryId 档案库ID
   * @param aclEntries 权限条目列表（包含 principal、principalType、permissions）
   * @param operatorId 操作者ID（用于审计）
   * @return 创建或更新的权限条目列表
   */
  List<AclEntry> batchGrantLibraryPermissions(
      String libraryId, List<AclEntry> aclEntries, String operatorId);

  /**
   * 批量撤销档案库访问权限
   *
   * @param libraryId 档案库ID
   * @param principals 主体标识列表（格式：principalType:principal）
   * @param operatorId 操作者ID（用于审计）
   */
  void batchRevokeLibraryPermissions(
      String libraryId, List<String> principals, String operatorId);

  /**
   * 清空档案库的所有访问权限
   *
   * <p>业务规则：
   * <ul>
   *   <li>删除档案库的所有权限条目</li>
   *   <li>不影响所有者和管理员的访问</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param operatorId 操作者ID（用于审计）
   */
  void clearLibraryPermissions(String libraryId, String operatorId);

  /**
   * 查询用户在所有档案库中的权限
   *
   * <p>业务规则：
   * <ul>
   *   <li>返回指定用户作为主体的所有权限条目</li>
   *   <li>用于审计和权限分析</li>
   * </ul>
   *
   * @param userId 用户ID
   * @return 权限条目列表
   */
  List<AclEntry> getUserPermissions(String userId);

  /**
   * 撤销用户在所有档案库中的权限
   *
   * <p>业务规则：
   * <ul>
   *   <li>删除指定用户作为主体的所有权限条目</li>
   *   <li>用于用户离职或权限回收场景</li>
   * </ul>
   *
   * @param userId 用户ID
   * @param operatorId 操作者ID（用于审计）
   */
  void revokeAllUserPermissions(String userId, String operatorId);

  // ==================== 统计和监控 ====================

  /**
   * 获取档案库统计信息
   *
   * @param libraryId 档案库ID
   * @return 统计信息
   */
  LibraryStatistics getLibraryStatistics(String libraryId);

  /**
   * 获取所有档案库的汇总统计信息
   *
   * @return 全局统计信息
   */
  GlobalStatistics getGlobalStatistics();

  /**
   * 获取配额超限的档案库列表
   *
   * @return 配额超限的档案库列表
   */
  List<DocumentLibrary> listQuotaExceededLibraries();

  /**
   * 获取已禁用的档案库列表
   *
   * @return 已禁用的档案库列表
   */
  List<DocumentLibrary> listDisabledLibraries();

  /**
   * 获取已锁定的档案库列表
   *
   * @return 已锁定的档案库列表
   */
  List<DocumentLibrary> listLockedLibraries();

  /**
   * 获取已软删除的档案库列表
   *
   * @return 已软删除的档案库列表
   */
  List<DocumentLibrary> listDeletedLibraries();

  /**
   * 全局统计信息
   */
  interface GlobalStatistics {
    /**
     * 档案库总数
     */
    long getTotalLibraryCount();

    /**
     * 启用的档案库数量
     */
    long getEnabledLibraryCount();

    /**
     * 禁用的档案库数量
     */
    long getDisabledLibraryCount();

    /**
     * 锁定的档案库数量
     */
    long getLockedLibraryCount();

    /**
     * 已删除的档案库数量
     */
    long getDeletedLibraryCount();

    /**
     * 文档总数
     */
    long getTotalDocumentCount();

    /**
     * 目录总数
     */
    long getTotalDirectoryCount();

    /**
     * 总存储空间使用量（字节）
     */
    long getTotalUsedSpace();

    /**
     * 配额超限的档案库数量
     */
    long getQuotaExceededLibraryCount();
  }
}
