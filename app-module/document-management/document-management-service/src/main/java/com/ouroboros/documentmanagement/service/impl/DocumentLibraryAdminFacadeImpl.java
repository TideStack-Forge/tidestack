package com.ouroboros.documentmanagement.service.impl;

import com.ouroboros.documentmanagement.exception.ResourceNotFoundException;
import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.repository.LibraryRepository;
import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryService;
import com.ouroboros.documentmanagement.service.DocumentLibraryService.LibraryStatistics;
import com.ouroboros.documentmanagement.service.PermissionAdminService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 档案库后台管理门面服务实现类
 *
 * <p>面向系统管理员，提供档案库后台管理功能，所有操作均不进行权限验证。
 *
 * <p>设计要点：
 * <ul>
 *   <li>所有操作不进行权限验证</li>
 *   <li>operatorId 仅用于审计追踪</li>
 *   <li>支持批量操作</li>
 *   <li>协调调用 DocumentLibraryService、PermissionAdminService</li>
 * </ul>
 */
@Service("documentLibraryAdminFacade")
public class DocumentLibraryAdminFacadeImpl implements DocumentLibraryAdminFacade {

  private final static Logger logger = LoggerFactory.getLogger(DocumentLibraryAdminFacadeImpl.class);

  private final DocumentLibraryService documentLibraryService;
  private final PermissionAdminService permissionAdminService;
  private final LibraryRepository libraryRepository;
  private final LibraryNodeRepository nodeRepository;
  private final AclEntryRepository aclEntryRepository;

  public DocumentLibraryAdminFacadeImpl(
      DocumentLibraryService documentLibraryService,
      PermissionAdminService permissionAdminService,
      LibraryRepository libraryRepository,
      LibraryNodeRepository nodeRepository,
      AclEntryRepository aclEntryRepository) {
    this.documentLibraryService = documentLibraryService;
    this.permissionAdminService = permissionAdminService;
    this.libraryRepository = libraryRepository;
    this.nodeRepository = nodeRepository;
    this.aclEntryRepository = aclEntryRepository;
  }

  // ==================== 档案库查询 ====================

  @Override
  public List<DocumentLibrary> listAllLibraries() {
    return documentLibraryService.listLibraries(true);
  }

  @Override
  public List<DocumentLibrary> listLibraries(
      boolean includeDeleted, boolean includeDisabled, boolean includeLocked) {
    List<DocumentLibrary> allLibraries = documentLibraryService.listLibraries(includeDeleted);

    return allLibraries.stream()
        .filter(library -> includeDisabled || !library.isDisabled())
        .filter(library -> includeLocked || !library.isLocked())
        .collect(Collectors.toList());
  }

  @Override
  public List<DocumentLibrary> listLibrariesByOwner(String ownerId) {
    return documentLibraryService.listLibrariesByOwner(ownerId);
  }

  @Override
  public List<DocumentLibrary> listLibrariesByAdmin(String adminId) {
    return documentLibraryService.listLibrariesByAdmin(adminId);
  }

  @Override
  public Optional<DocumentLibrary> getLibrary(String libraryId) {
    return documentLibraryService.getLibraryById(libraryId);
  }

  @Override
  public Optional<DocumentLibrary> getLibraryByName(String name) {
    return documentLibraryService.getLibraryByName(name);
  }

  // ==================== 所有权和管理员管理（系统级）====================

  @Override
  public DocumentLibrary transferOwnership(
      String libraryId, String newOwnerId, boolean keepOriginalOwnerAsAdmin, String operatorId) {
    DocumentLibrary library =
        documentLibraryService
            .getLibraryById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    String originalOwnerId = library.getOwner();

    // 转移所有权
    DocumentLibrary updatedLibrary = library.toBuilder()
        .owner(newOwnerId)
        .updatedBy(operatorId)
        .build();

    updatedLibrary = documentLibraryService.updateLibrary(updatedLibrary, operatorId);

    // 如果需要，将原所有者保留为管理员
    if (keepOriginalOwnerAsAdmin && !updatedLibrary.getAdminIds().contains(originalOwnerId)) {
      updatedLibrary = documentLibraryService.addAdmin(libraryId, originalOwnerId, operatorId);
    }

    return updatedLibrary;
  }

  @Override
  public DocumentLibrary addAdmin(String libraryId, String adminUserId, String operatorId) {
    return documentLibraryService.addAdmin(libraryId, adminUserId, operatorId);
  }

  @Override
  public DocumentLibrary removeAdmin(String libraryId, String adminUserId, String operatorId) {
    return documentLibraryService.removeAdmin(libraryId, adminUserId, operatorId);
  }

  @Override
  public DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String operatorId) {
    return documentLibraryService.addAdmins(libraryId, adminUserIds, operatorId);
  }

  @Override
  public DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String operatorId) {
    return documentLibraryService.removeAdmins(libraryId, adminUserIds, operatorId);
  }

  @Override
  public DocumentLibrary setOwner(String libraryId, String newOwnerId, String operatorId) {
    DocumentLibrary library =
        documentLibraryService
            .getLibraryById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    DocumentLibrary updatedLibrary = library.toBuilder()
        .owner(newOwnerId)
        .updatedBy(operatorId)
        .build();

    return documentLibraryService.updateLibrary(updatedLibrary, operatorId);
  }

  @Override
  public DocumentLibrary setAdmins(String libraryId, List<String> adminUserIds, String operatorId) {
    DocumentLibrary library =
        documentLibraryService
            .getLibraryById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 先清空现有管理员
    DocumentLibrary.Builder builder = library.toBuilder()
        .updatedBy(operatorId);

    // 清空管理员列表
    for (String adminId : library.getAdminIds()) {
      builder.removeAdmin(adminId);
    }

    // 添加新的管理员
    for (String adminId : adminUserIds) {
      builder.addAdmin(adminId);
    }

    DocumentLibrary updatedLibrary = builder.build();
    return documentLibraryService.updateLibrary(updatedLibrary, operatorId);
  }

  @Override
  public List<String> listAdmins(String libraryId) {
    DocumentLibrary library =
        documentLibraryService
            .getLibraryById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    return new ArrayList<>(library.getAdminIds());
  }

  // ==================== 配额管理 ====================

  @Override
  public DocumentLibrary updateQuota(
      String libraryId, Long maxSizeLimit, Long maxFileCountLimit, String operatorId) {
    return documentLibraryService.updateQuota(libraryId, maxSizeLimit, maxFileCountLimit, operatorId);
  }

  @Override
  public DocumentLibrary removeQuota(String libraryId, String operatorId) {
    return documentLibraryService.updateQuota(libraryId, null, null, operatorId);
  }

  @Override
  public List<DocumentLibrary> batchUpdateQuota(
      List<String> libraryIds, Long maxSizeLimit, Long maxFileCountLimit, String operatorId) {
    List<DocumentLibrary> updatedLibraries = new ArrayList<>();

    for (String libraryId : libraryIds) {
      try {
        DocumentLibrary updated =
            documentLibraryService.updateQuota(libraryId, maxSizeLimit, maxFileCountLimit, operatorId);
        updatedLibraries.add(updated);
      } catch (ResourceNotFoundException e) {
        logger.debug("批量更新配额时跳过不存在的档案库: {}", libraryId, e);
      }
    }

    return updatedLibraries;
  }

  // ==================== 档案库状态管理 ====================

  @Override
  public DocumentLibrary enableLibrary(String libraryId, String operatorId) {
    return documentLibraryService.enableLibrary(libraryId, operatorId);
  }

  @Override
  public DocumentLibrary disableLibrary(String libraryId, String operatorId) {
    return documentLibraryService.disableLibrary(libraryId, operatorId);
  }

  @Override
  public DocumentLibrary lockLibrary(String libraryId, String operatorId) {
    return documentLibraryService.lockLibrary(libraryId, operatorId);
  }

  @Override
  public DocumentLibrary unlockLibrary(String libraryId, String operatorId) {
    return documentLibraryService.unlockLibrary(libraryId, operatorId);
  }

  @Override
  public DocumentLibrary enableTrash(String libraryId, String operatorId) {
    return documentLibraryService.enableTrash(libraryId, operatorId);
  }

  @Override
  public DocumentLibrary disableTrash(String libraryId, String operatorId) {
    return documentLibraryService.disableTrash(libraryId, operatorId);
  }

  @Override
  public List<DocumentLibrary> batchEnableLibraries(List<String> libraryIds, String operatorId) {
    List<DocumentLibrary> updatedLibraries = new ArrayList<>();

    for (String libraryId : libraryIds) {
      try {
        DocumentLibrary updated = documentLibraryService.enableLibrary(libraryId, operatorId);
        updatedLibraries.add(updated);
      } catch (ResourceNotFoundException e) {
        logger.debug("批量启用时跳过不存在的档案库: {}", libraryId, e);
      }
    }

    return updatedLibraries;
  }

  @Override
  public List<DocumentLibrary> batchDisableLibraries(List<String> libraryIds, String operatorId) {
    List<DocumentLibrary> updatedLibraries = new ArrayList<>();

    for (String libraryId : libraryIds) {
      try {
        DocumentLibrary updated = documentLibraryService.disableLibrary(libraryId, operatorId);
        updatedLibraries.add(updated);
      } catch (ResourceNotFoundException e) {
        logger.debug("批量禁用时跳过不存在的档案库: {}", libraryId, e);
      }
    }

    return updatedLibraries;
  }

  // ==================== 删除操作 ====================

  @Override
  public DocumentLibrary softDeleteLibrary(String libraryId, String operatorId) {
    return documentLibraryService.softDeleteLibrary(libraryId, operatorId);
  }

  @Override
  public DocumentLibrary restoreLibrary(String libraryId, String operatorId) {
    return documentLibraryService.restoreLibrary(libraryId, operatorId);
  }

  @Override
  public void hardDeleteLibrary(String libraryId, String operatorId) {
    documentLibraryService.hardDeleteLibrary(libraryId);
  }

  @Override
  public List<DocumentLibrary> batchSoftDeleteLibraries(List<String> libraryIds, String operatorId) {
    List<DocumentLibrary> deletedLibraries = new ArrayList<>();

    for (String libraryId : libraryIds) {
      try {
        DocumentLibrary deleted = documentLibraryService.softDeleteLibrary(libraryId, operatorId);
        deletedLibraries.add(deleted);
      } catch (ResourceNotFoundException e) {
        logger.debug("批量软删除时跳过不存在的档案库: {}", libraryId, e);
      }
    }

    return deletedLibraries;
  }

  @Override
  public List<DocumentLibrary> batchRestoreLibraries(List<String> libraryIds, String operatorId) {
    List<DocumentLibrary> restoredLibraries = new ArrayList<>();

    for (String libraryId : libraryIds) {
      try {
        DocumentLibrary restored = documentLibraryService.restoreLibrary(libraryId, operatorId);
        restoredLibraries.add(restored);
      } catch (ResourceNotFoundException e) {
        logger.debug("批量恢复时跳过不存在的档案库: {}", libraryId, e);
      }
    }

    return restoredLibraries;
  }

  @Override
  public void batchHardDeleteLibraries(List<String> libraryIds, String operatorId) {
    for (String libraryId : libraryIds) {
      try {
        documentLibraryService.hardDeleteLibrary(libraryId);
      } catch (ResourceNotFoundException e) {
        logger.debug("批量硬删除时跳过不存在的档案库: {}", libraryId, e);
      }
    }
  }

  @Override
  public int cleanupDeletedLibraries(int retentionDays, String operatorId) {
    // 计算截止时间
    LocalDateTime cutoffTime = LocalDateTime.ofInstant(Instant.now().minus(retentionDays, ChronoUnit.DAYS), ZoneId.systemDefault());

    // 查询所有已删除的档案库
    List<DocumentLibrary> allLibraries = documentLibraryService.listLibraries(true);

    int cleanedCount = 0;

    for (DocumentLibrary library : allLibraries) {
      if (library.isDeleted() && library.getUpdatedAt() != null) {
        // 如果已删除且更新时间早于截止时间，则硬删除
        if (library.getUpdatedAt().isBefore(cutoffTime)) {
          try {
            documentLibraryService.hardDeleteLibrary(library.getId());
            cleanedCount++;
          } catch (Exception e) {
            logger.warn("清理已删除档案库失败: {}", library.getId(), e);
          }
        }
      }
    }

    return cleanedCount;
  }

  // ==================== 权限查询和管理 ====================

  @Override
  public List<AclEntry> getLibraryPermissions(String libraryId) {
    // 验证档案库存在
    documentLibraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 获取根目录并查询其权限
    return nodeRepository
        .findRootDirectory(libraryId)
        .map(rootDir -> aclEntryRepository.findByResource(rootDir.getId(), ResourceType.DIRECTORY))
        .orElse(new ArrayList<>());
  }

  @Override
  public AclEntry grantLibraryPermission(
      String libraryId,
      String principal,
      String principalType,
      PermissionSet permissions,
      String operatorId) {
    // 验证档案库存在
    documentLibraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 获取根目录并设置其权限
    String rootDirId =
        nodeRepository
            .findRootDirectory(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("根目录", libraryId))
            .getId();

    return permissionAdminService.setPermission(
        libraryId, rootDirId, ResourceType.DIRECTORY, principal, principalType, permissions);
  }

  @Override
  public AclEntry updateLibraryPermission(
      String libraryId,
      String principal,
      String principalType,
      PermissionSet permissions,
      String operatorId) {
    // 验证档案库存在
    documentLibraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 获取根目录并更新其权限
    String rootDirId =
        nodeRepository
            .findRootDirectory(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("根目录", libraryId))
            .getId();

    return permissionAdminService.setPermission(
        libraryId, rootDirId, ResourceType.DIRECTORY, principal, principalType, permissions);
  }

  @Override
  public void revokeLibraryPermission(
      String libraryId, String principal, String principalType, String operatorId) {
    // 验证档案库存在
    documentLibraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 获取根目录并撤销其权限
    String rootDirId =
        nodeRepository
            .findRootDirectory(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("根目录", libraryId))
            .getId();

    permissionAdminService.removePermission(
        rootDirId, ResourceType.DIRECTORY, principal, principalType);
  }

  @Override
  public List<AclEntry> batchGrantLibraryPermissions(
      String libraryId, List<AclEntry> aclEntries, String operatorId) {
    // 验证档案库存在
    documentLibraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 获取根目录
    String rootDirId =
        nodeRepository
            .findRootDirectory(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("根目录", libraryId))
            .getId();

    List<AclEntry> grantedEntries = new ArrayList<>();

    for (AclEntry entry : aclEntries) {
      AclEntry granted =
          permissionAdminService.setPermission(
              libraryId,
              rootDirId,
              ResourceType.DIRECTORY,
              entry.getPrincipal(),
              entry.getPrincipalType(),
              entry.getPermissions());
      grantedEntries.add(granted);
    }

    return grantedEntries;
  }

  @Override
  public void batchRevokeLibraryPermissions(
      String libraryId, List<String> principals, String operatorId) {
    // 验证档案库存在
    documentLibraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 获取根目录
    String rootDirId =
        nodeRepository
            .findRootDirectory(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("根目录", libraryId))
            .getId();

    for (String principal : principals) {
      // 解析 principalType:principal 格式
      String[] parts = principal.split(":", 2);
      if (parts.length == 2) {
        String principalType = parts[0];
        String principalId = parts[1];
        permissionAdminService.removePermission(
            rootDirId, ResourceType.DIRECTORY, principalId, principalType);
      }
    }
  }

  @Override
  public void clearLibraryPermissions(String libraryId, String operatorId) {
    // 验证档案库存在
    documentLibraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("档案库", libraryId));

    // 获取根目录并清空其权限
    String rootDirId =
        nodeRepository
            .findRootDirectory(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("根目录", libraryId))
            .getId();

    aclEntryRepository.deleteByResource(rootDirId, ResourceType.DIRECTORY);
  }

  @Override
  public List<AclEntry> getUserPermissions(String userId) {
    // 查询用户作为主体的所有权限条目
    // 这里假设 userId 对应的 principalType 是 "USER"
    return aclEntryRepository.findByPrincipal(userId, "USER");
  }

  @Override
  public void revokeAllUserPermissions(String userId, String operatorId) {
    // 删除用户作为主体的所有权限条目
    aclEntryRepository.deleteByPrincipal(userId, "USER");
  }

  // ==================== 统计和监控 ====================

  @Override
  public LibraryStatistics getLibraryStatistics(String libraryId) {
    return documentLibraryService.getStatistics(libraryId);
  }

  @Override
  public GlobalStatistics getGlobalStatistics() {
    List<DocumentLibrary> allLibraries = documentLibraryService.listLibraries(true);

    long totalLibraryCount = allLibraries.size();
    long enabledLibraryCount = allLibraries.stream().filter(lib -> !lib.isDisabled()).count();
    long disabledLibraryCount = allLibraries.stream().filter(DocumentLibrary::isDisabled).count();
    long lockedLibraryCount = allLibraries.stream().filter(DocumentLibrary::isLocked).count();
    long deletedLibraryCount = allLibraries.stream().filter(DocumentLibrary::isDeleted).count();

    long totalDocumentCount = 0;
    long totalDirectoryCount = 0;
    long totalUsedSpace = 0;
    long quotaExceededLibraryCount = 0;

    for (DocumentLibrary library : allLibraries) {
      if (!library.isDeleted()) {
        LibraryStatistics stats = documentLibraryService.getStatistics(library.getId());
        totalDocumentCount += stats.getDocumentCount();
        totalDirectoryCount += stats.getDirectoryCount();
        totalUsedSpace += stats.getUsedSpace();

        if (stats.isSpaceExceeded() || stats.isFileCountExceeded()) {
          quotaExceededLibraryCount++;
        }
      }
    }

    return new GlobalStatisticsImpl(
        totalLibraryCount,
        enabledLibraryCount,
        disabledLibraryCount,
        lockedLibraryCount,
        deletedLibraryCount,
        totalDocumentCount,
        totalDirectoryCount,
        totalUsedSpace,
        quotaExceededLibraryCount);
  }

  @Override
  public List<DocumentLibrary> listQuotaExceededLibraries() {
    List<DocumentLibrary> allLibraries = documentLibraryService.listLibraries(false);

    return allLibraries.stream()
        .filter(library -> documentLibraryService.isQuotaExceeded(library.getId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<DocumentLibrary> listDisabledLibraries() {
    List<DocumentLibrary> allLibraries = documentLibraryService.listLibraries(false);

    return allLibraries.stream()
        .filter(DocumentLibrary::isDisabled)
        .collect(Collectors.toList());
  }

  @Override
  public List<DocumentLibrary> listLockedLibraries() {
    List<DocumentLibrary> allLibraries = documentLibraryService.listLibraries(false);

    return allLibraries.stream()
        .filter(DocumentLibrary::isLocked)
        .collect(Collectors.toList());
  }

  @Override
  public List<DocumentLibrary> listDeletedLibraries() {
    List<DocumentLibrary> allLibraries = documentLibraryService.listLibraries(true);

    return allLibraries.stream()
        .filter(DocumentLibrary::isDeleted)
        .collect(Collectors.toList());
  }

  /**
   * 全局统计信息实现类
   */
  private static class GlobalStatisticsImpl implements GlobalStatistics {
    private final long totalLibraryCount;
    private final long enabledLibraryCount;
    private final long disabledLibraryCount;
    private final long lockedLibraryCount;
    private final long deletedLibraryCount;
    private final long totalDocumentCount;
    private final long totalDirectoryCount;
    private final long totalUsedSpace;
    private final long quotaExceededLibraryCount;

    public GlobalStatisticsImpl(
        long totalLibraryCount,
        long enabledLibraryCount,
        long disabledLibraryCount,
        long lockedLibraryCount,
        long deletedLibraryCount,
        long totalDocumentCount,
        long totalDirectoryCount,
        long totalUsedSpace,
        long quotaExceededLibraryCount) {
      this.totalLibraryCount = totalLibraryCount;
      this.enabledLibraryCount = enabledLibraryCount;
      this.disabledLibraryCount = disabledLibraryCount;
      this.lockedLibraryCount = lockedLibraryCount;
      this.deletedLibraryCount = deletedLibraryCount;
      this.totalDocumentCount = totalDocumentCount;
      this.totalDirectoryCount = totalDirectoryCount;
      this.totalUsedSpace = totalUsedSpace;
      this.quotaExceededLibraryCount = quotaExceededLibraryCount;
    }

    @Override
    public long getTotalLibraryCount() {
      return totalLibraryCount;
    }

    @Override
    public long getEnabledLibraryCount() {
      return enabledLibraryCount;
    }

    @Override
    public long getDisabledLibraryCount() {
      return disabledLibraryCount;
    }

    @Override
    public long getLockedLibraryCount() {
      return lockedLibraryCount;
    }

    @Override
    public long getDeletedLibraryCount() {
      return deletedLibraryCount;
    }

    @Override
    public long getTotalDocumentCount() {
      return totalDocumentCount;
    }

    @Override
    public long getTotalDirectoryCount() {
      return totalDirectoryCount;
    }

    @Override
    public long getTotalUsedSpace() {
      return totalUsedSpace;
    }

    @Override
    public long getQuotaExceededLibraryCount() {
      return quotaExceededLibraryCount;
    }
  }
}
