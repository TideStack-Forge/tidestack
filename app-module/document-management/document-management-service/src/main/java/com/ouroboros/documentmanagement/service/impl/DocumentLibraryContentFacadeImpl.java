package com.ouroboros.documentmanagement.service.impl;

import com.ouroboros.documentmanagement.exception.InvalidOperationException;
import com.ouroboros.documentmanagement.exception.PermissionDeniedException;
import com.ouroboros.documentmanagement.exception.QuotaExceededException;
import com.ouroboros.documentmanagement.exception.ResourceNotFoundException;
import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.NodeType;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.service.ACLService;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryService;
import com.ouroboros.documentmanagement.service.LibraryContentService;
import com.ouroboros.documentmanagement.service.PermissionAdminService;
import com.ouroboros.util.Trees;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 档案库内容管理和使用门面服务实现类
 *
 * <p>面向档案库使用者，提供内容管理和使用功能，所有操作都进行权限验证。
 *
 * <p>设计要点：
 * <ul>
 *   <li>所有操作都进行权限验证</li>
 *   <li>使用 ACLService 计算有效权限</li>
 *   <li>协调调用底层服务完成业务操作</li>
 * </ul>
 */
@Service("documentLibraryContentFacade")
public class DocumentLibraryContentFacadeImpl implements DocumentLibraryContentFacade {
  private final static Logger logger = LoggerFactory.getLogger(DocumentLibraryContentFacadeImpl.class);

  private final LibraryContentService contentService;
  private final ACLService aclService;
  private final PermissionAdminService permissionAdminService;
  private final DocumentLibraryService libraryService;
  private final LibraryNodeRepository nodeRepository;
  private final AclEntryRepository aclEntryRepository;

  public DocumentLibraryContentFacadeImpl(
      LibraryContentService contentService,
      ACLService aclService,
      PermissionAdminService permissionAdminService,
      DocumentLibraryService libraryService,
      LibraryNodeRepository nodeRepository,
      AclEntryRepository aclEntryRepository) {
    this.contentService = contentService;
    this.aclService = aclService;
    this.permissionAdminService = permissionAdminService;
    this.libraryService = libraryService;
    this.nodeRepository = nodeRepository;
    this.aclEntryRepository = aclEntryRepository;
  }

  // ==================== 目录浏览和文件列表 ====================

  @Override
  public Optional<DirectoryNode> getRootDirectory(String libraryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "获取根目录");

    // 验证档案库访问权限
    if (!hasLibraryPermission(libraryId, userId, Permission.VIEW)) {
      return Optional.empty();
    }

    return nodeRepository.findRootDirectory(libraryId);
  }

  @Override
  public List<LibraryNode> browseDirectory(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "浏览目录");

    // 验证目录访问权限
    ensureDirectoryAccessPermission(libraryId, directoryId, userId);

    // 获取所有子节点
    List<LibraryNode> allNodes = nodeRepository.findByParentId(directoryId);

    // 过滤出用户有权限访问的节点
    return allNodes.stream()
        .filter(node -> hasNodeAccessPermission(node, userId))
        .collect(Collectors.toList());
  }

  @Override
  public List<DirectoryNode> listSubdirectories(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "列出子目录");

    // 验证目录访问权限
    if (!hasDirectoryAccessPermission(libraryId, directoryId, userId)) {
      return new ArrayList<>();
    }

    List<DirectoryNode> allSubdirs = contentService.listDirectories(libraryId, directoryId);

    // 过滤出用户有权限访问的目录
    return allSubdirs.stream()
        .filter(dir -> hasNodeAccessPermission(dir, userId))
        .collect(Collectors.toList());
  }

  @Override
  public List<DocumentNode> listDocuments(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "列出文档");

    // 验证目录访问权限
    if (!hasDirectoryAccessPermission(libraryId, directoryId, userId)) {
      return new ArrayList<>();
    }

    List<DocumentNode> allDocs = contentService.listDocuments(libraryId, directoryId);

    // 过滤出用户有权限访问的文档
    return allDocs.stream()
        .filter(doc -> hasNodeAccessPermission(doc, userId))
        .collect(Collectors.toList());
  }

  @Override
  public List<LibraryNode> getNodePath(String libraryId, String nodeId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "获取节点路径");

    // 查询路径
    List<LibraryNode> path = nodeRepository.findPathToNode(nodeId);

    if (path.isEmpty()) {
      return path;
    }

    // 验证用户对目标节点有访问权限
    LibraryNode targetNode = path.get(path.size() - 1);
    if (!hasNodeAccessPermission(targetNode, userId)) {
      throw new PermissionDeniedException("您没有访问该节点的权限");
    }

    return path;
  }

  @Override
  public Optional<DirectoryNode> getDirectoryTree(String libraryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "获取目录树");

    // 递归构建目录树
    DirectoryNode tree = buildDirectoryTree(libraryId, userId);

    return Optional.ofNullable(tree);
  }

  @Override
  public Optional<DirectoryNode> getDirectoryTree(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "获取目录树");

    if (!hasDirectoryAccessPermission(libraryId, directoryId, userId)) {
      return Optional.empty();
    }

    var root = getDirectoryTree(libraryId, directoryId, userId);
    if (!root.isPresent()) {
      return Optional.empty();
    }
    var branch = Trees.findOne(root.get(),
        node -> node.getChildren().stream()
            .filter(DirectoryNode.class::isInstance)
            .map(DirectoryNode.class::cast)
            .collect(Collectors.toList()),
        node -> StringUtils.equals(node.getId(), directoryId));
    return branch;
  }

  /**
   * 递归构建目录树（只包含用户有权限访问的目录）
   */
  private DirectoryNode buildDirectoryTree(String libraryId, String userId) {
    var dirs = nodeRepository.findDirectoriesByLibraryId(libraryId);
    var accessibleDirs = dirs.stream()
        .filter(dir -> hasNodeAccessPermission(dir, userId))
        .collect(Collectors.toList());
    if (accessibleDirs.isEmpty()) {
      return null;
    }
    var builder = Trees.<DirectoryNode, DirectoryNode>builder()
        .roots(() -> accessibleDirs.stream()
            .filter(dir -> StringUtils.isEmpty(dir.getParentId()))
            .collect(Collectors.toList()))
        .childrenGetter(dir -> accessibleDirs.stream()
            .filter(node -> StringUtils.equals(node.getParentId(), dir.getId()))
            .collect(Collectors.toList()))
        .mapper((dir, children) -> DirectoryNode.builder()
            .id(dir.getId())
            .name(dir.getName())
            .description(dir.getDescription())
            .libraryId(dir.getLibraryId())
            .createdBy(dir.getCreatedBy())
            .createdAt(dir.getCreatedAt())
            .updatedBy(dir.getUpdatedBy())
            .updatedAt(dir.getUpdatedAt())
            .aclEntries(dir.getAclEntries())
            .children((List<LibraryNode>)(List<?>)children)
            .build());
    var tree = builder.build();
    if (tree.isEmpty()) {
      logger.warn("目录树构建结果异常，缺少根目录，请检查数据");
      return null;
    }
    else if (tree.size() > 1) {
      logger.warn("目录树构建结果异常，具有多个根目录，请检查数据");
    }
    return tree.get(0);
  }

  // ==================== 文件查询和搜索 ====================

  @Override
  public Optional<DocumentNode> getDocument(String libraryId, String documentId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "查看文档");

    Optional<DocumentNode> docOpt = contentService.getDocument(libraryId, documentId);

    if (!docOpt.isPresent()) {
      return Optional.empty();
    }

    DocumentNode doc = docOpt.get();

    // 验证访问权限
    if (!hasNodeAccessPermission(doc, userId)) {
      return Optional.empty();
    }

    return Optional.of(doc);
  }

  @Override
  public Optional<DirectoryNode> getDirectory(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "查看目录");

    Optional<DirectoryNode> dirOpt = contentService.getDirectory(libraryId, directoryId);

    if (!dirOpt.isPresent()) {
      return Optional.empty();
    }

    DirectoryNode dir = dirOpt.get();

    // 验证访问权限
    if (!hasNodeAccessPermission(dir, userId)) {
      return Optional.empty();
    }

    return Optional.of(dir);
  }

  @Override
  public List<DocumentNode> searchDocuments(String libraryId, String namePattern, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "搜索文档");

    // 验证档案库访问权限
    if (!hasLibraryPermission(libraryId, userId, Permission.VIEW)) {
      return new ArrayList<>();
    }

    // 查询所有匹配的文档
    List<DocumentNode> allDocs = nodeRepository.findDocumentsByLibraryId(libraryId);

    // 过滤：名称匹配 + 权限检查
    return allDocs.stream()
        .filter(doc -> matchesPattern(doc.getName(), namePattern))
        .filter(doc -> hasNodeAccessPermission(doc, userId))
        .collect(Collectors.toList());
  }

  @Override
  public List<DocumentNode> searchDocumentsInDirectory(
      String libraryId, String directoryId, String namePattern, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "搜索文档");

    // 验证目录访问权限
    ensureDirectoryPermission(libraryId, directoryId, userId, Permission.VIEW);

    // 递归查询目录及其子目录下的所有文档
    List<DocumentNode> allDocs = findDocumentsRecursively(directoryId);

    // 过滤：名称匹配 + 权限检查
    return allDocs.stream()
        .filter(doc -> matchesPattern(doc.getName(), namePattern))
        .filter(doc -> hasNodeAccessPermission(doc, userId))
        .collect(Collectors.toList());
  }

  /**
   * 递归查找目录及其子目录下的所有文档
   */
  private List<DocumentNode> findDocumentsRecursively(String directoryId) {
    List<DocumentNode> result = new ArrayList<>();

    // 添加当前目录下的文档
    result.addAll(nodeRepository.findDocumentsByParentId(directoryId));

    // 递归处理子目录
    List<DirectoryNode> subdirs = nodeRepository.findDirectoriesByParentId(directoryId);
    for (DirectoryNode subdir : subdirs) {
      result.addAll(findDocumentsRecursively(subdir.getId()));
    }

    return result;
  }

  /**
   * 简单的通配符匹配（支持 * 和 ?）
   */
  private boolean matchesPattern(String name, String pattern) {
    // 将通配符模式转换为正则表达式
    String regex = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".");

    return name.matches(regex);
  }

  // ==================== 文件查看和下载 ====================

  @Override
  public Optional<InputStream> previewDocument(String libraryId, String documentId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "预览文档");

    // 验证 VIEW 权限
    if (!hasDocumentPermission(libraryId, documentId, userId, Permission.VIEW)) {
      return Optional.empty();
    }

    return contentService.getDocumentContent(libraryId, documentId);
  }

  @Override
  public Optional<InputStream> downloadDocument(String libraryId, String documentId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "下载文档");

    // 验证 DOWNLOAD 权限
    if (!hasDocumentPermission(libraryId, documentId, userId, Permission.DOWNLOAD)) {
      return Optional.empty();
    }

    return contentService.getDocumentContent(libraryId, documentId);
  }

  // ==================== 文件上传 ====================

  @Override
  public DocumentNode uploadFile(
      String libraryId,
      String directoryId,
      String fileName,
      String contentType,
      long fileSize,
      InputStream inputStream,
      String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "上传文件");

    // 验证目录的 CREATE_FILE 权限
    ensureDirectoryPermission(libraryId, directoryId, userId, Permission.CREATE_FILE);

    // 验证档案库配额
    if (!libraryService.canUploadFile(libraryId, fileSize)) {
      throw new QuotaExceededException("档案库配额已满，无法上传文件");
    }

    // 上传文件
    return contentService.uploadDocument(
        libraryId, directoryId, fileName, inputStream, fileSize, contentType, userId);
  }

  // ==================== 目录创建 ====================

  @Override
  public DirectoryNode createDirectory(
      String libraryId, String parentDirectoryId, String directoryName, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "创建目录");

    // 验证父目录的 CREATE_DIR 权限
    ensureDirectoryPermission(libraryId, parentDirectoryId, userId, Permission.CREATE_DIR);

    return contentService.createDirectory(libraryId, parentDirectoryId, directoryName, userId);
  }

  // ==================== 移动和重命名 ====================

  @Override
  public DocumentNode moveDocument(
      String libraryId, String documentId, String targetDirectoryId, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "移动文档");

    // 验证源文件的 RENAME_MOVE 权限
    ensureDocumentPermission(libraryId, documentId, userId, Permission.RENAME_MOVE);

    // 验证目标目录的 CREATE_FILE 权限
    ensureDirectoryPermission(libraryId, targetDirectoryId, userId, Permission.CREATE_FILE);

    contentService.moveDocument(libraryId, documentId, targetDirectoryId, userId);

    // 返回更新后的文档节点
    return contentService
        .getDocument(libraryId, documentId)
        .orElseThrow(() -> new ResourceNotFoundException("文档", documentId));
  }

  @Override
  public DirectoryNode moveDirectory(
      String libraryId, String directoryId, String targetDirectoryId, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "移动目录");

    // 强制逻辑控制：不允许移动根目录
    Optional<DirectoryNode> rootOpt = nodeRepository.findRootDirectory(libraryId);
    if (rootOpt.isPresent() && rootOpt.get().getId().equals(directoryId)) {
      throw new InvalidOperationException("移动目录", "不能移动根目录");
    }

    // 获取源目录信息，确定其父目录
    DirectoryNode sourceDirectory = contentService
        .getDirectory(libraryId, directoryId)
        .orElseThrow(() -> new ResourceNotFoundException("目录", directoryId));

    // 验证用户在源目录的父目录是否有 RENAME_MOVE 权限
    String sourceParentId = sourceDirectory.getParentId();
    if (sourceParentId != null) {
      ensureDirectoryPermission(libraryId, sourceParentId, userId, Permission.RENAME_MOVE);
    }

    // 验证目标目录的 CREATE_DIR 权限
    ensureDirectoryPermission(libraryId, targetDirectoryId, userId, Permission.CREATE_DIR);

    // 验证不能移动到自己的子目录下
    if (nodeRepository.isDescendantOf(directoryId, targetDirectoryId)) {
      throw new InvalidOperationException("移动目录", "不能将目录移动到自己的子目录下");
    }

    contentService.moveDirectory(libraryId, directoryId, targetDirectoryId, userId);

    // 返回更新后的目录节点
    return contentService
        .getDirectory(libraryId, directoryId)
        .orElseThrow(() -> new ResourceNotFoundException("目录", directoryId));
  }

  @Override
  public DocumentNode renameDocument(String libraryId, String documentId, String newName, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "重命名文档");

    // 验证文件的 RENAME_MOVE 权限
    ensureDocumentPermission(libraryId, documentId, userId, Permission.RENAME_MOVE);

    contentService.renameDocument(libraryId, documentId, newName, userId);

    // 返回更新后的文档节点
    return contentService
        .getDocument(libraryId, documentId)
        .orElseThrow(() -> new ResourceNotFoundException("文档", documentId));
  }

  @Override
  public DirectoryNode renameDirectory(String libraryId, String directoryId, String newName, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "重命名目录");

    // 验证目录的 RENAME_MOVE 权限
    ensureDirectoryPermission(libraryId, directoryId, userId, Permission.RENAME_MOVE);

    contentService.renameDirectory(libraryId, directoryId, newName, userId);

    // 返回更新后的目录节点
    return contentService
        .getDirectory(libraryId, directoryId)
        .orElseThrow(() -> new ResourceNotFoundException("目录", directoryId));
  }

  // ==================== 删除操作 ====================

  @Override
  public void deleteDocument(String libraryId, String documentId, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "删除文档");

    // 验证文件的 DELETE_FILE 权限
    ensureDocumentPermission(libraryId, documentId, userId, Permission.DELETE_FILE);

    contentService.deleteDocument(libraryId, documentId, userId);
  }

  @Override
  public void deleteDirectory(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "删除目录");

    // 强制逻辑控制：不允许删除根目录
    Optional<DirectoryNode> rootOpt = nodeRepository.findRootDirectory(libraryId);
    if (rootOpt.isPresent() && rootOpt.get().getId().equals(directoryId)) {
      throw new InvalidOperationException("删除目录", "不能删除根目录");
    }

    // 获取目录信息，确定其父目录
    DirectoryNode directory = contentService
        .getDirectory(libraryId, directoryId)
        .orElseThrow(() -> new ResourceNotFoundException("目录", directoryId));

    // 验证用户在父目录是否有 DELETE_DIR 权限
    String parentId = directory.getParentId();
    if (parentId != null) {
      ensureDirectoryPermission(libraryId, parentId, userId, Permission.DELETE_DIR);
    }

    contentService.deleteDirectory(libraryId, directoryId, userId);
  }

  // ==================== 权限查询 ====================

  @Override
  public PermissionSet getDocumentPermissions(String libraryId, String documentId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "查看文档权限");

    return aclService.getEffectivePermissions(documentId, ResourceType.DOCUMENT, userId);
  }

  @Override
  public PermissionSet getDirectoryPermissions(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "查看目录权限");

    return aclService.getEffectivePermissions(directoryId, ResourceType.DIRECTORY, userId);
  }

  @Override
  public boolean hasDocumentPermission(
      String libraryId, String documentId, String userId, Permission permission) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "检查文档权限");

    // ACLService 已经处理了所有者和管理员的权限，直接调用即可
    return aclService.hasPermission(documentId, ResourceType.DOCUMENT, userId, permission);
  }

  @Override
  public boolean hasDirectoryPermission(
      String libraryId, String directoryId, String userId, Permission permission) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "检查目录权限");

    // ACLService 已经处理了所有者和管理员的权限，直接调用即可
    return aclService.hasPermission(directoryId, ResourceType.DIRECTORY, userId, permission);
  }

  // ==================== 权限管理（需要 MANAGE 权限）====================

  @Override
  public List<AclEntry> getDocumentAclEntries(String libraryId, String documentId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "查看文档ACL");

    // 验证 MANAGE 权限
    ensureDocumentPermission(libraryId, documentId, userId, Permission.MANAGE);

    return aclEntryRepository.findByResource(documentId, ResourceType.DOCUMENT);
  }

  @Override
  public List<AclEntry> getDirectoryAclEntries(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "查看目录ACL");

    // 验证 MANAGE 权限
    ensureDirectoryPermission(libraryId, directoryId, userId, Permission.MANAGE);

    return aclEntryRepository.findByResource(directoryId, ResourceType.DIRECTORY);
  }

  @Override
  public AclEntry grantDocumentPermission(
      String libraryId,
      String documentId,
      String principal,
      String principalType,
      PermissionSet permissions,
      String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "授予文档权限");

    // 验证 MANAGE 权限
    ensureDocumentPermission(libraryId, documentId, userId, Permission.MANAGE);

    return permissionAdminService.setPermission(
        libraryId, documentId, ResourceType.DOCUMENT, principal, principalType, permissions);
  }

  @Override
  public AclEntry grantDirectoryPermission(
      String libraryId,
      String directoryId,
      String principal,
      String principalType,
      PermissionSet permissions,
      String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "授予目录权限");

    // 验证 MANAGE 权限
    ensureDirectoryPermission(libraryId, directoryId, userId, Permission.MANAGE);

    return permissionAdminService.setPermission(
        libraryId, directoryId, ResourceType.DIRECTORY, principal, principalType, permissions);
  }

  @Override
  public void revokeDocumentPermission(
      String libraryId, String documentId, String principal, String principalType, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "撤销文档权限");

    // 验证 MANAGE 权限
    ensureDocumentPermission(libraryId, documentId, userId, Permission.MANAGE);

    permissionAdminService.removePermission(
        documentId, ResourceType.DOCUMENT, principal, principalType);
  }

  @Override
  public void revokeDirectoryPermission(
      String libraryId, String directoryId, String principal, String principalType, String userId) {
    // 状态检查：确保文档库可修改
    ensureLibraryModifiable(libraryId, "撤销目录权限");

    // 验证 MANAGE 权限
    ensureDirectoryPermission(libraryId, directoryId, userId, Permission.MANAGE);

    permissionAdminService.removePermission(
        directoryId, ResourceType.DIRECTORY, principal, principalType);
  }

  // ==================== 统计查询 ====================

  @Override
  public DirectoryStatistics getDirectoryStatistics(String libraryId, String directoryId, String userId) {
    // 状态检查：确保文档库未被禁用
    ensureLibraryNotDisabled(libraryId, "获取目录统计");

    // 验证目录访问权限
    ensureDirectoryPermission(libraryId, directoryId, userId, Permission.VIEW);

    // 统计直接子节点
    long subdirectoryCount = nodeRepository.findDirectoriesByParentId(directoryId).size();
    long documentCount = nodeRepository.findDocumentsByParentId(directoryId).size();

    // 递归统计
    long totalSubdirectoryCount = countSubdirectoriesRecursively(directoryId);
    long totalDocumentCount = nodeRepository.countDocumentsRecursively(directoryId);
    long totalSize = nodeRepository.sumDocumentSizeRecursively(directoryId);

    return new DirectoryStatisticsImpl(
        directoryId,
        subdirectoryCount,
        documentCount,
        totalSubdirectoryCount,
        totalDocumentCount,
        totalSize);
  }

  /**
   * 递归统计子目录数量
   */
  private long countSubdirectoriesRecursively(String directoryId) {
    List<DirectoryNode> subdirs = nodeRepository.findDirectoriesByParentId(directoryId);
    long count = subdirs.size();

    for (DirectoryNode subdir : subdirs) {
      count += countSubdirectoriesRecursively(subdir.getId());
    }

    return count;
  }

  // ==================== 辅助方法 ====================

  /**
   * 检查用户是否有档案库权限
   */
  private boolean hasLibraryPermission(String libraryId, String userId, Permission permission) {
    // ACLService 已经处理了所有者和管理员的权限，直接调用即可
    return aclService.hasPermission(libraryId, ResourceType.LIBRARY, userId, permission);
  }

  /**
   * 检查用户是否有节点访问权限
   */
  private boolean hasNodeAccessPermission(LibraryNode node, String userId) {
    // ACLService 已经处理了所有者和管理员的权限，直接调用即可
    // 对于目录，需要有 VIEW 或 NAVIGATE 权限；对于文档，需要有 VIEW 权限
    if (node.getType() == NodeType.DIRECTORY) {
      // 目录需要 VIEW 或 NAVIGATE 权限之一
      return aclService.hasAnyPermission(node.getId(), ResourceType.DIRECTORY, userId, Permission.VIEW, Permission.NAVIGATE);
    } else {
      // 文档只需要 VIEW 权限
      return aclService.hasPermission(node.getId(), ResourceType.DOCUMENT, userId, Permission.VIEW);
    }
  }

  /**
   * 确保用户有文档权限，否则抛出异常
   */
  private void ensureDocumentPermission(
      String libraryId, String documentId, String userId, Permission permission) {
    // ACLService 已经处理了所有者和管理员的权限，直接调用即可
    if (!hasDocumentPermission(libraryId, documentId, userId, permission)) {
      throw new PermissionDeniedException("您没有对该文档执行此操作的权限: " + permission.getDisplayName());
    }
  }

  /**
   * 检查用户是否有目录访问权限（VIEW 或 NAVIGATE）
   */
  private boolean hasDirectoryAccessPermission(String libraryId, String directoryId, String userId) {
    // ACLService 已经处理了所有者和管理员的权限，直接调用即可
    return aclService.hasAnyPermission(directoryId, ResourceType.DIRECTORY, userId, Permission.VIEW, Permission.NAVIGATE);
  }

  /**
   * 确保用户有目录访问权限（VIEW 或 NAVIGATE），否则抛出异常
   */
  private void ensureDirectoryAccessPermission(String libraryId, String directoryId, String userId) {
    // ACLService 已经处理了所有者和管理员的权限，直接调用即可
    if (!hasDirectoryAccessPermission(libraryId, directoryId, userId)) {
      throw new PermissionDeniedException("您没有访问该目录的权限");
    }
  }

  /**
   * 确保用户有目录权限，否则抛出异常
   */
  private void ensureDirectoryPermission(
      String libraryId, String directoryId, String userId, Permission permission) {
    // ACLService 已经处理了所有者和管理员的权限，直接调用即可
    if (!hasDirectoryPermission(libraryId, directoryId, userId, permission)) {
      throw new PermissionDeniedException("您没有对该目录执行此操作的权限: " + permission.getDisplayName());
    }
  }

  /**
   * 确保文档库未被禁用，否则抛出异常
   *
   * @param libraryId 文档库ID
   * @param operation 操作名称（用于错误消息）
   * @throws ResourceNotFoundException 如果文档库不存在
   * @throws InvalidOperationException 如果文档库已被禁用
   */
  private void ensureLibraryNotDisabled(String libraryId, String operation) {
    DocumentLibrary library = libraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    if (library.isDisabled()) {
      throw new InvalidOperationException(operation, "文档库已被禁用，无法进行任何操作");
    }
  }

  /**
   * 确保文档库未被锁定，否则抛出异常
   *
   * @param libraryId 文档库ID
   * @param operation 操作名称（用于错误消息）
   * @throws ResourceNotFoundException 如果文档库不存在
   * @throws InvalidOperationException 如果文档库已被锁定
   */
  private void ensureLibraryNotLocked(String libraryId, String operation) {
    DocumentLibrary library = libraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    if (library.isLocked()) {
      throw new InvalidOperationException(operation, "文档库已被锁定，不允许修改操作");
    }
  }

  /**
   * 确保文档库可修改（既未禁用也未锁定），否则抛出异常
   *
   * <p>此方法会一次性检查 disabled 和 locked 两个状态，避免重复查询文档库。
   *
   * @param libraryId 文档库ID
   * @param operation 操作名称（用于错误消息）
   * @throws ResourceNotFoundException 如果文档库不存在
   * @throws InvalidOperationException 如果文档库已被禁用或锁定
   */
  private void ensureLibraryModifiable(String libraryId, String operation) {
    DocumentLibrary library = libraryService
        .getLibraryById(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("文档库", libraryId));

    // 优先检查 disabled（更严格的状态）
    if (library.isDisabled()) {
      throw new InvalidOperationException(operation, "文档库已被禁用，无法进行任何操作");
    }

    // 然后检查 locked
    if (library.isLocked()) {
      throw new InvalidOperationException(operation, "文档库已被锁定，不允许修改操作");
    }
  }

  /**
   * 目录统计信息实现类
   */
  private static class DirectoryStatisticsImpl implements DirectoryStatistics {
    private final String directoryId;
    private final long subdirectoryCount;
    private final long documentCount;
    private final long totalSubdirectoryCount;
    private final long totalDocumentCount;
    private final long totalSize;

    public DirectoryStatisticsImpl(
        String directoryId,
        long subdirectoryCount,
        long documentCount,
        long totalSubdirectoryCount,
        long totalDocumentCount,
        long totalSize) {
      this.directoryId = directoryId;
      this.subdirectoryCount = subdirectoryCount;
      this.documentCount = documentCount;
      this.totalSubdirectoryCount = totalSubdirectoryCount;
      this.totalDocumentCount = totalDocumentCount;
      this.totalSize = totalSize;
    }

    @Override
    public String getDirectoryId() {
      return directoryId;
    }

    @Override
    public long getSubdirectoryCount() {
      return subdirectoryCount;
    }

    @Override
    public long getDocumentCount() {
      return documentCount;
    }

    @Override
    public long getTotalSubdirectoryCount() {
      return totalSubdirectoryCount;
    }

    @Override
    public long getTotalDocumentCount() {
      return totalDocumentCount;
    }

    @Override
    public long getTotalSize() {
      return totalSize;
    }
  }
}
