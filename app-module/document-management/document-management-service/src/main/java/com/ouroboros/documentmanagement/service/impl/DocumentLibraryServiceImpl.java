package com.ouroboros.documentmanagement.service.impl;

import com.ouroboros.documentmanagement.exception.DuplicateNameException;
import com.ouroboros.documentmanagement.exception.InvalidOperationException;
import com.ouroboros.documentmanagement.exception.ResourceNotFoundException;
import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.repository.LibraryRepository;
import com.ouroboros.documentmanagement.service.ACLService;
import com.ouroboros.documentmanagement.service.DocumentLibraryService;
import com.ouroboros.documentmanagement.service.LibraryContentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档库服务实现类
 *
 * <p>负责文档库的生命周期管理，包括创建、查询、更新、删除、管理员管理、统计等功能。
 *
 * <p>设计要点：
 * <ul>
 *   <li>通过 LibraryRepository 管理文档库元数据</li>
 *   <li>通过 LibraryNodeRepository 统计文档和目录数量</li>
 *   <li>通过 LibraryContentService 创建根目录</li>
 *   <li>不进行权限验证（由门面服务负责）</li>
 * </ul>
 */
@Service("documentLibraryService")
public class DocumentLibraryServiceImpl implements DocumentLibraryService {

  private final LibraryRepository libraryRepository;
  private final LibraryNodeRepository nodeRepository;
  private final AclEntryRepository aclEntryRepository;
  private final LibraryContentService contentService;
  private final ACLService aclService;

  public DocumentLibraryServiceImpl(
      LibraryRepository libraryRepository,
      LibraryNodeRepository nodeRepository,
      AclEntryRepository aclEntryRepository,
      LibraryContentService contentService,
      ACLService aclService) {
    this.libraryRepository = libraryRepository;
    this.nodeRepository = nodeRepository;
    this.aclEntryRepository = aclEntryRepository;
    this.contentService = contentService;
    this.aclService = aclService;
  }

  // ==================== 创建和查询 ====================

  @Override
  public DocumentLibrary createLibrary(DocumentLibrary documentLibrary) {
    return createLibrary(documentLibrary, true);
  }

  @Override
  public DocumentLibrary createLibrary(DocumentLibrary documentLibrary, boolean createRootDirectory) {
    // 1. 验证名称唯一性
    if (libraryRepository.existsByName(documentLibrary.getName())) {
      throw new DuplicateNameException(documentLibrary.getName());
    }

    // 2. 添加文档库
    DocumentLibrary savedLibrary = libraryRepository.add(documentLibrary);

    // 3. 如果需要，创建根目录
    if (createRootDirectory) {
      contentService.createDirectory(
          savedLibrary.getId(),
          // parentId 为 null 表示根目录
          null,
          // 目录名称，使用文档库名称
          documentLibrary.getName(),
          savedLibrary.getCreatedBy());
    }

    return savedLibrary;
  }

  @Override
  public Optional<DocumentLibrary> getLibraryById(String libraryId) {
    return libraryRepository.findById(libraryId);
  }

  @Override
  public Optional<DocumentLibrary> getLibraryByName(String libraryName) {
    return libraryRepository.findByName(libraryName);
  }

  @Override
  public List<DocumentLibrary> listLibraries() {
    return listLibraries(false);
  }

  @Override
  public List<DocumentLibrary> listLibraries(boolean includeDeleted) {
    return libraryRepository.findAll(includeDeleted);
  }

  @Override
  public List<DocumentLibrary> listLibrariesByOwner(String userId) {
    return libraryRepository.findByOwner(userId);
  }

  @Override
  public List<DocumentLibrary> listLibrariesByAdmin(String userId) {
    return libraryRepository.findByAdmin(userId);
  }

  @Override
  public List<DocumentLibrary> listAccessibleLibraries(String userId) {
    // 用户可访问的文档库 = 拥有的 + 管理的 + 有权限的
    // 使用 LinkedHashSet 保持顺序并去重（基于 libraryId）
    Set<String> accessibleLibraryIds = new LinkedHashSet<>();

    // 1. 拥有的文档库
    List<DocumentLibrary> ownedLibraries = listLibrariesByOwner(userId);
    for (DocumentLibrary library : ownedLibraries) {
      accessibleLibraryIds.add(library.getId());
    }

    // 2. 管理的文档库
    List<DocumentLibrary> managedLibraries = listLibrariesByAdmin(userId);
    for (DocumentLibrary library : managedLibraries) {
      accessibleLibraryIds.add(library.getId());
    }

    // 3. 有权限的文档库（通过 ACLService 查询）
    // 注意：权限现在存储在根目录上，从权限条目的 libraryId 字段直接获取
    List<AclEntry> userAclEntries = aclService.getUserRelatedAclEntries(userId);

    // 从权限条目中直接提取文档库ID（无需查询节点）
    Set<String> permittedLibraryIds =
        userAclEntries.stream()
            .map(AclEntry::getLibraryId) // 直接获取 libraryId 字段
            .collect(Collectors.toSet());

    accessibleLibraryIds.addAll(permittedLibraryIds);

    // 4. 根据 ID 集合查询文档库对象
    return accessibleLibraryIds.stream()
        .map(libraryRepository::findById)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(library -> !library.isDeleted()) // 过滤掉已删除的文档库
        .collect(Collectors.toList());
  }

  // ==================== 更新操作 ====================

  @Override
  public DocumentLibrary updateLibrary(DocumentLibrary documentLibrary, String updatedBy) {
    // 1. 验证文档库存在
    Optional<DocumentLibrary> existingOpt = libraryRepository.findById(documentLibrary.getId());
    if (!existingOpt.isPresent()) {
      throw new ResourceNotFoundException("文档库", documentLibrary.getId());
    }

    // 2. 如果名称有变更，验证新名称的唯一性
    DocumentLibrary existing = existingOpt.get();
    if (!existing.getName().equals(documentLibrary.getName())) {
      if (libraryRepository.existsByNameAndIdNot(documentLibrary.getName(), documentLibrary.getId())) {
        throw new DuplicateNameException(documentLibrary.getName());
      }
    }

    // 3. 保存更新
    return libraryRepository.update(documentLibrary);
  }

  @Override
  public DocumentLibrary updateQuota(
      String libraryId, Long maxSizeLimit, Long maxFileCountLimit, String updatedBy) {
    // 1. 获取文档库
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    // 2. 更新配额
    DocumentLibrary updatedLibrary = library.toBuilder()
        .maxSizeLimit(maxSizeLimit)
        .maxFileCountLimit(maxFileCountLimit)
        .updatedBy(updatedBy)
        .build();

    // 3. 更新
    return libraryRepository.update(updatedLibrary);
  }

  @Override
  public DocumentLibrary enableLibrary(String libraryId, String updatedBy) {
    return updateLibraryStatus(libraryId, false, null, updatedBy);
  }

  @Override
  public DocumentLibrary disableLibrary(String libraryId, String updatedBy) {
    return updateLibraryStatus(libraryId, true, null, updatedBy);
  }

  @Override
  public DocumentLibrary lockLibrary(String libraryId, String updatedBy) {
    return updateLibraryStatus(libraryId, null, true, updatedBy);
  }

  @Override
  public DocumentLibrary unlockLibrary(String libraryId, String updatedBy) {
    return updateLibraryStatus(libraryId, null, false, updatedBy);
  }

  @Override
  public DocumentLibrary enableTrash(String libraryId, String updatedBy) {
    return updateTrashStatus(libraryId, true, updatedBy);
  }

  @Override
  public DocumentLibrary disableTrash(String libraryId, String updatedBy) {
    return updateTrashStatus(libraryId, false, updatedBy);
  }

  /**
   * 更新文档库状态（启用/禁用、锁定/解锁）
   */
  private DocumentLibrary updateLibraryStatus(
      String libraryId, Boolean disabled, Boolean locked, String updatedBy) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    DocumentLibrary.Builder builder = library.toBuilder()
        .updatedBy(updatedBy);

    if (disabled != null) {
      builder.disabled(disabled);
    }
    if (locked != null) {
      builder.locked(locked);
    }

    return libraryRepository.update(builder.build());
  }

  /**
   * 更新回收站状态
   */
  private DocumentLibrary updateTrashStatus(String libraryId, boolean enabled, String updatedBy) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    DocumentLibrary updatedLibrary = library.toBuilder()
        .trashEnabled(enabled)
        .updatedBy(updatedBy)
        .build();

    return libraryRepository.update(updatedLibrary);
  }

  // ==================== 管理员管理 ====================

  @Override
  public DocumentLibrary addAdmin(String libraryId, String adminUserId, String updatedBy) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    // 验证是否已是管理员
    if (library.getAdminIds().contains(adminUserId)) {
      throw new IllegalArgumentException("用户已经是管理员: " + adminUserId);
    }

    DocumentLibrary updatedLibrary = library.toBuilder()
        .addAdmin(adminUserId)
        .updatedBy(updatedBy)
        .build();

    return libraryRepository.update(updatedLibrary);
  }

  @Override
  public DocumentLibrary removeAdmin(String libraryId, String adminUserId, String updatedBy) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    // 验证是否是管理员
    if (!library.getAdminIds().contains(adminUserId)) {
      throw new IllegalArgumentException("用户不是管理员: " + adminUserId);
    }

    DocumentLibrary updatedLibrary = library.toBuilder()
        .removeAdmin(adminUserId)
        .updatedBy(updatedBy)
        .build();

    return libraryRepository.update(updatedLibrary);
  }

  @Override
  public DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String updatedBy) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    DocumentLibrary.Builder builder = library.toBuilder()
        .updatedBy(updatedBy);

    for (String adminUserId : adminUserIds) {
      if (!library.getAdminIds().contains(adminUserId)) {
        builder.addAdmin(adminUserId);
      }
    }

    return libraryRepository.update(builder.build());
  }

  @Override
  public DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String updatedBy) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    DocumentLibrary.Builder builder = library.toBuilder()
        .updatedBy(updatedBy);

    for (String adminUserId : adminUserIds) {
      builder.removeAdmin(adminUserId);
    }

    return libraryRepository.update(builder.build());
  }

  @Override
  public boolean isAdmin(String libraryId, String userId) {
    Optional<DocumentLibrary> libraryOpt = libraryRepository.findById(libraryId);
    return libraryOpt.map(library -> library.getAdminIds().contains(userId)).orElse(false);
  }

  @Override
  public boolean isOwner(String libraryId, String userId) {
    Optional<DocumentLibrary> libraryOpt = libraryRepository.findById(libraryId);
    return libraryOpt.map(library -> library.getOwner().equals(userId)).orElse(false);
  }

  // ==================== 删除操作 ====================

  @Override
  public DocumentLibrary softDeleteLibrary(String libraryId, String deletedBy) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    DocumentLibrary updatedLibrary = library.toBuilder()
        .deleted(true)
        .updatedBy(deletedBy)
        .build();

    return libraryRepository.update(updatedLibrary);
  }

  @Override
  public DocumentLibrary restoreLibrary(String libraryId, String restoredBy) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    DocumentLibrary updatedLibrary = library.toBuilder()
        .deleted(false)
        .updatedBy(restoredBy)
        .build();

    return libraryRepository.update(updatedLibrary);
  }

  @Override
  @Transactional
  public void hardDeleteLibrary(String libraryId) {
    // 1. 验证文档库存在且已被软删除
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    if (!library.isDeleted()) {
      throw new InvalidOperationException("硬删除文档库", "文档库必须先软删除才能硬删除");
    }

    // 2. 删除所有权限条目
    aclEntryRepository.deleteByLibrary(libraryId);

    // 3. 删除所有节点（目录和文档）及物理文件
    // 查找根目录并递归删除（会删除所有子节点和物理文件）
    Optional<DirectoryNode> rootDirectoryOpt = nodeRepository.findRootDirectory(libraryId);
    if (rootDirectoryOpt.isPresent()) {
      DirectoryNode rootDirectory = rootDirectoryOpt.get();
      // 使用 LibraryContentService 删除根目录（会递归删除所有子节点和物理文件）
      contentService.deleteDirectory(libraryId, rootDirectory.getId(), library.getUpdatedBy());
    }

    // 4. 删除文档库元数据
    libraryRepository.deleteById(libraryId);
  }

  // ==================== 统计信息 ====================

  @Override
  public LibraryStatistics getStatistics(String libraryId) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    // 统计文档数量
    long documentCount = nodeRepository.countDocumentsByLibraryId(libraryId);

    // 统计目录数量（不包括根目录）
    long directoryCount = nodeRepository.countByLibraryId(libraryId) - documentCount - 1;

    // 统计已使用空间
    long usedSpace = nodeRepository.sumDocumentSizeByLibraryId(libraryId);

    return new LibraryStatisticsImpl(
        libraryId,
        documentCount,
        directoryCount,
        usedSpace,
        library.getMaxSizeLimit(),
        library.getMaxFileCountLimit());
  }

  @Override
  public boolean isQuotaExceeded(String libraryId) {
    LibraryStatistics stats = getStatistics(libraryId);
    return stats.isSpaceExceeded() || stats.isFileCountExceeded();
  }

  @Override
  public boolean canUploadFile(String libraryId, long fileSize) {
    DocumentLibrary library =
        libraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    LibraryStatistics stats = getStatistics(libraryId);

    // 检查文件数量限制
    if (library.getMaxFileCountLimit() != null
        && library.getMaxFileCountLimit() > 0
        && stats.getDocumentCount() >= library.getMaxFileCountLimit()) {
      return false;
    }

    // 检查存储空间限制
    return library.getMaxSizeLimit() == null
        || library.getMaxSizeLimit() <= 0
        || (stats.getUsedSpace() + fileSize) <= library.getMaxSizeLimit();
  }

  @Override
  public boolean isNameAvailable(String name) {
    return !libraryRepository.existsByName(name);
  }

  @Override
  public boolean isNameAvailable(String name, String excludeId) {
    return !libraryRepository.existsByNameAndIdNot(name, excludeId);
  }

  /**
   * 文档库统计信息实现类
   */
  private static class LibraryStatisticsImpl implements LibraryStatistics {
    private final String libraryId;
    private final long documentCount;
    private final long directoryCount;
    private final long usedSpace;
    private final Long maxSizeLimit;
    private final Long maxFileCountLimit;

    public LibraryStatisticsImpl(
        String libraryId,
        long documentCount,
        long directoryCount,
        long usedSpace,
        Long maxSizeLimit,
        Long maxFileCountLimit) {
      this.libraryId = libraryId;
      this.documentCount = documentCount;
      this.directoryCount = directoryCount;
      this.usedSpace = usedSpace;
      this.maxSizeLimit = maxSizeLimit;
      this.maxFileCountLimit = maxFileCountLimit;
    }

    @Override
    public String getLibraryId() {
      return libraryId;
    }

    @Override
    public long getDocumentCount() {
      return documentCount;
    }

    @Override
    public long getDirectoryCount() {
      return directoryCount;
    }

    @Override
    public long getUsedSpace() {
      return usedSpace;
    }

    @Override
    public Long getMaxSizeLimit() {
      return maxSizeLimit;
    }

    @Override
    public Long getMaxFileCountLimit() {
      return maxFileCountLimit;
    }

    @Override
    public double getSpaceUsageRate() {
      if (maxSizeLimit == null || maxSizeLimit == 0) {
        return 0.0;
      }
      return (double) usedSpace / maxSizeLimit;
    }

    @Override
    public double getFileCountUsageRate() {
      if (maxFileCountLimit == null || maxFileCountLimit == 0) {
        return 0.0;
      }
      return (double) documentCount / maxFileCountLimit;
    }

    @Override
    public boolean isSpaceExceeded() {
      return maxSizeLimit != null && usedSpace > maxSizeLimit;
    }

    @Override
    public boolean isFileCountExceeded() {
      return maxFileCountLimit != null && documentCount > maxFileCountLimit;
    }
  }
}
