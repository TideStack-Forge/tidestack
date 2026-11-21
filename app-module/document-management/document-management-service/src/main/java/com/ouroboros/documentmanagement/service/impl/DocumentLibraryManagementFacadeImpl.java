package com.ouroboros.documentmanagement.service.impl;

import com.ouroboros.documentmanagement.exception.PermissionDeniedException;
import com.ouroboros.documentmanagement.exception.ResourceNotFoundException;
import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.service.ACLService;
import com.ouroboros.documentmanagement.service.DocumentLibraryManagementFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryService;
import com.ouroboros.documentmanagement.service.DocumentLibraryService.LibraryStatistics;
import com.ouroboros.documentmanagement.service.PermissionAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 档案库管理门面服务实现类
 *
 * <p>面向档案库所有者和管理员，提供档案库管理功能。
 *
 * <p>设计要点：
 * <ul>
 *   <li>所有者拥有全部管理权限</li>
 *   <li>管理员只能管理访问权限（AclEntry）</li>
 *   <li>所有方法进行权限验证</li>
 *   <li>协调调用 DocumentLibraryService、ACLService、PermissionAdminService</li>
 * </ul>
 */
@Service("documentLibraryManagementFacade")
public class DocumentLibraryManagementFacadeImpl implements DocumentLibraryManagementFacade {

  private final DocumentLibraryService documentLibraryService;
  private final ACLService aclService;
  private final PermissionAdminService permissionAdminService;
  private final LibraryNodeRepository nodeRepository;

  public DocumentLibraryManagementFacadeImpl(
      DocumentLibraryService documentLibraryService,
      ACLService aclService,
      PermissionAdminService permissionAdminService,
      LibraryNodeRepository nodeRepository) {
    this.documentLibraryService = documentLibraryService;
    this.aclService = aclService;
    this.permissionAdminService = permissionAdminService;
    this.nodeRepository = nodeRepository;
  }

  // ==================== 档案库创建和查询 ====================

  @Override
  @Transactional
  public DocumentLibrary createLibrary(
      String name, String displayName, String description, String userId) {
    // 创建档案库对象
    DocumentLibrary library = DocumentLibrary.builder()
        .name(name)
        .displayName(displayName)
        .description(description)
        .owner(userId)
        .createdBy(userId)
        .build();

    // 创建档案库并自动创建根目录
    return documentLibraryService.createLibrary(library, true);
  }

  @Override
  public List<DocumentLibrary> listOwnedLibraries(String userId) {
    return documentLibraryService.listLibrariesByOwner(userId);
  }

  @Override
  public List<DocumentLibrary> listManagedLibraries(String userId) {
    return documentLibraryService.listLibrariesByAdmin(userId);
  }

  @Override
  public List<DocumentLibrary> listAccessibleLibraries(String userId) {
    return documentLibraryService.listAccessibleLibraries(userId);
  }

  @Override
  public Optional<DocumentLibrary> getLibrary(String libraryId, String userId) {
    Optional<DocumentLibrary> libraryOpt = documentLibraryService.getLibraryById(libraryId);
    if (!libraryOpt.isPresent()) {
      return Optional.empty();
    }

    DocumentLibrary library = libraryOpt.get();

    // 验证用户是否有权限访问
    if (canManageLibrary(libraryId, userId)) {
      return Optional.of(library);
    }

    // 检查是否有档案库的访问权限
    PermissionSet permissions =
        aclService.getEffectivePermissions(libraryId, ResourceType.LIBRARY, userId);
    if (!permissions.isEmpty()) {
      return Optional.of(library);
    }

    return Optional.empty();
  }

  @Override
  public Optional<DocumentLibrary> getLibraryByName(String name, String userId) {
    Optional<DocumentLibrary> libraryOpt = documentLibraryService.getLibraryByName(name);
    if (!libraryOpt.isPresent()) {
      return Optional.empty();
    }

    DocumentLibrary library = libraryOpt.get();
    // 复用 getLibrary 的权限验证逻辑
    return getLibrary(library.getId(), userId);
  }

  // ==================== 基本信息管理（所有者操作）====================

  @Override
  public DocumentLibrary updateLibraryInfo(
      String libraryId, String name, String displayName, String description, String userId) {
    // 1. 验证所有者权限
    ensureOwner(libraryId, userId);

    // 2. 获取现有档案库
    DocumentLibrary library =
        documentLibraryService
            .getLibraryById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 3. 更新信息（只更新非 null 的字段）
    DocumentLibrary.Builder builder = library.toBuilder()
        .updatedBy(userId);

    if (name != null) {
      builder.name(name);
    }
    if (displayName != null) {
      builder.displayName(displayName);
    }
    if (description != null) {
      builder.description(description);
    }

    DocumentLibrary updatedLibrary = builder.build();

    // 4. 保存更新
    return documentLibraryService.updateLibrary(updatedLibrary, userId);
  }

  @Override
  public DocumentLibrary updateLibraryQuota(
      String libraryId, Long maxSizeLimit, Long maxFileCountLimit, String userId) {
    // 1. 验证所有者权限
    ensureOwner(libraryId, userId);

    // 2. 更新配额
    return documentLibraryService.updateQuota(libraryId, maxSizeLimit, maxFileCountLimit, userId);
  }

  @Override
  public DocumentLibrary setTrashEnabled(String libraryId, boolean enabled, String userId) {
    // 1. 验证所有者权限
    ensureOwner(libraryId, userId);

    // 2. 更新回收站设置
    if (enabled) {
      return documentLibraryService.enableTrash(libraryId, userId);
    } else {
      return documentLibraryService.disableTrash(libraryId, userId);
    }
  }

  // ==================== 管理员管理（所有者操作）====================

  @Override
  public DocumentLibrary addAdmin(String libraryId, String adminUserId, String userId) {
    // 1. 验证所有者权限
    ensureOwner(libraryId, userId);

    // 2. 添加管理员
    return documentLibraryService.addAdmin(libraryId, adminUserId, userId);
  }

  @Override
  public DocumentLibrary removeAdmin(String libraryId, String adminUserId, String userId) {
    // 1. 验证所有者权限
    ensureOwner(libraryId, userId);

    // 2. 移除管理员
    return documentLibraryService.removeAdmin(libraryId, adminUserId, userId);
  }

  @Override
  public DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String userId) {
    // 1. 验证所有者权限
    ensureOwner(libraryId, userId);

    // 2. 批量添加管理员
    return documentLibraryService.addAdmins(libraryId, adminUserIds, userId);
  }

  @Override
  public DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String userId) {
    // 1. 验证所有者权限
    ensureOwner(libraryId, userId);

    // 2. 批量移除管理员
    return documentLibraryService.removeAdmins(libraryId, adminUserIds, userId);
  }

  @Override
  public List<String> listAdmins(String libraryId, String userId) {
    // 1. 验证管理权限（所有者或管理员）
    ensureCanManageLibrary(libraryId, userId);

    // 2. 获取档案库
    DocumentLibrary library =
        documentLibraryService
            .getLibraryById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 3. 返回管理员列表
    return library.getAdminIds();
  }

  // ==================== 所有权转移（所有者操作）====================

  @Override
  @Transactional
  public DocumentLibrary transferOwnership(
      String libraryId, String newOwnerId, boolean keepAsAdmin, String userId) {
    // 1. 验证所有者权限
    ensureOwner(libraryId, userId);

    // 2. 获取档案库
    DocumentLibrary library =
        documentLibraryService
            .getLibraryById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 3. 转移所有权
    DocumentLibrary updatedLibrary = library.toBuilder()
        .owner(newOwnerId)
        .updatedBy(userId)
        .build();

    // 4. 保存更新
    updatedLibrary = documentLibraryService.updateLibrary(updatedLibrary, userId);

    // 5. 如果需要保留原所有者为管理员
    if (keepAsAdmin && !updatedLibrary.getAdminIds().contains(userId)) {
      updatedLibrary = documentLibraryService.addAdmin(updatedLibrary.getId(), userId, userId);
    }

    return updatedLibrary;
  }

  // ==================== 访问权限管理（所有者和管理员都可以）====================

  @Override
  public List<AclEntry> getLibraryPermissions(String libraryId, String userId) {
    // 1. 验证管理权限（所有者或管理员）
    ensureCanManageLibrary(libraryId, userId);

    // 2. 获取根目录并查询其权限条目
    return nodeRepository
        .findRootDirectory(libraryId)
        .map(rootDir -> permissionAdminService.getPermissions(rootDir.getId(), ResourceType.DIRECTORY))
        .orElse(new ArrayList<>());
  }

  // ==================== 角色和权限检查 ====================

  @Override
  public boolean isOwner(String libraryId, String userId) {
    return documentLibraryService.isOwner(libraryId, userId);
  }

  @Override
  public boolean isAdmin(String libraryId, String userId) {
    return documentLibraryService.isAdmin(libraryId, userId);
  }

  @Override
  public boolean canManageLibrary(String libraryId, String userId) {
    return isOwner(libraryId, userId) || isAdmin(libraryId, userId);
  }

  // ==================== 统计信息 ====================

  @Override
  public LibraryStatistics getLibraryStatistics(String libraryId, String userId) {
    // 1. 验证管理权限（所有者或管理员）
    ensureCanManageLibrary(libraryId, userId);

    // 2. 获取统计信息
    return documentLibraryService.getStatistics(libraryId);
  }

  @Override
  public boolean isQuotaExceeded(String libraryId, String userId) {
    // 1. 验证管理权限（所有者或管理员）
    ensureCanManageLibrary(libraryId, userId);

    // 2. 检查配额
    return documentLibraryService.isQuotaExceeded(libraryId);
  }

  @Override
  public boolean canUploadFile(String libraryId, long fileSize, String userId) {
    // 1. 验证管理权限（所有者或管理员）
    ensureCanManageLibrary(libraryId, userId);

    // 2. 检查是否可以上传
    return documentLibraryService.canUploadFile(libraryId, fileSize);
  }

  // ==================== 辅助方法 ====================

  /**
   * 确保用户是档案库所有者
   *
   * @param libraryId 档案库ID
   * @param userId 用户ID
   * @throws PermissionDeniedException 如果用户不是所有者
   */
  private void ensureOwner(String libraryId, String userId) {
    if (!isOwner(libraryId, userId)) {
      throw new PermissionDeniedException(
          userId, libraryId, ResourceType.LIBRARY, Permission.MANAGE);
    }
  }

  /**
   * 确保用户可以管理档案库（所有者或管理员）
   *
   * @param libraryId 档案库ID
   * @param userId 用户ID
   * @throws PermissionDeniedException 如果用户既不是所有者也不是管理员
   */
  private void ensureCanManageLibrary(String libraryId, String userId) {
    if (!canManageLibrary(libraryId, userId)) {
      throw new PermissionDeniedException(
          userId, libraryId, ResourceType.LIBRARY, Permission.MANAGE);
    }
  }
}
