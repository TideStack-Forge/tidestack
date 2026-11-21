package com.ouroboros.documentmanagement.service.impl;

import com.ouroboros.documentmanagement.exception.CircularReferenceException;
import com.ouroboros.documentmanagement.exception.DuplicateNameException;
import com.ouroboros.documentmanagement.exception.ResourceNotFoundException;
import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.service.LibraryContentService;
import com.ouroboros.documentmanagement.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文档库内容服务实现类
 *
 * <p>负责文档和目录的原子化 CRUD 操作，通过 LibraryNodeRepository 管理元数据，
 * 通过 StorageService 管理文件内容。
 *
 * <p>设计要点：
 * <ul>
 *   <li>元数据与文件分离：元数据通过 Repository，文件通过 StorageService</li>
 *   <li>原子化操作：每个方法完成单一、完整的业务操作</li>
 *   <li>名称唯一性：同一父目录下名称必须唯一</li>
 *   <li>递归删除：删除目录时递归删除所有子节点和文件</li>
 * </ul>
 */
@Service("libraryContentService")
public class LibraryContentServiceImpl implements LibraryContentService {

  private final LibraryNodeRepository nodeRepository;
  private final StorageService storageService;
  private final DocumentContentCleanupService cleanup;

  public LibraryContentServiceImpl(
      LibraryNodeRepository nodeRepository, StorageService storageService, DocumentContentCleanupService cleanup) {
    this.nodeRepository = nodeRepository;
    this.storageService = storageService;
    this.cleanup = cleanup;
  }

  // ==================== 目录操作 ====================

  @Override
  public DirectoryNode createDirectory(
      String libraryId, String parentId, String name, String createdBy) {
    // 1. 验证名称唯一性（同一文档库的同一父目录下）
    if (nodeRepository.existsByLibraryIdAndParentIdAndName(libraryId, parentId, name)) {
      throw new DuplicateNameException(name, parentId);
    }

    // 2. 如果有父目录，验证父目录存在
    if (parentId != null) {
      Optional<DirectoryNode> parentOpt = nodeRepository.findDirectoryById(parentId);
      if (!parentOpt.isPresent()) {
        throw new ResourceNotFoundException("父目录", parentId);
      }
    }

    // 3. 创建目录节点
    DirectoryNode directory = DirectoryNode.builder()
        .libraryId(libraryId)
        .parentId(parentId)
        .name(name)
        .createdBy(createdBy)
        .build();

    // 4. 添加
    return (DirectoryNode) nodeRepository.add(directory);
  }

  @Override
  public Optional<DirectoryNode> getDirectory(String libraryId, String directoryId) {
    return nodeRepository.findDirectoryById(directoryId);
  }

  @Override
  public List<DirectoryNode> listDirectories(String libraryId, String directoryId) {
    return nodeRepository.findDirectoriesByParentId(directoryId);
  }

  @Override
  public List<DocumentNode> listDocuments(String libraryId, String directoryId) {
    return nodeRepository.findDocumentsByParentId(directoryId);
  }

  @Override
  public List<DirectoryNode> getDirectoryPath(String libraryId, String directoryId) {
    List<LibraryNode> nodePath = nodeRepository.findPathToNode(directoryId);
    List<DirectoryNode> directoryPath = new ArrayList<>();
    for (LibraryNode node : nodePath) {
      if (node instanceof DirectoryNode) {
        directoryPath.add((DirectoryNode) node);
      }
    }
    return directoryPath;
  }

  // ==================== 文档操作 ====================

  @Override
  @Transactional
  public DocumentNode uploadDocument(
      String libraryId,
      String directoryId,
      String name,
      InputStream inputStream,
      Long size,
      String mimeType,
      String uploadedBy) {
    cleanup.requireTransaction();
    // 1. 验证目录存在
    Optional<DirectoryNode> directoryOpt = nodeRepository.findDirectoryById(directoryId);
    if (!directoryOpt.isPresent()) {
      throw new ResourceNotFoundException("目录", directoryId);
    }

    // 2. 处理文件名重复：如果重名，自动添加 (1)、(2) 等后缀
    String uniqueName = generateUniqueFileName(libraryId, directoryId, name);

    // 3. 存储文件到 StorageService
    StorageService.StorageResult storageResult = storageService.store(inputStream, size, mimeType);
    cleanup.compensateOnRollback(storageResult.getStoragePath());

    // 4. 创建文档节点元数据
    DocumentNode document = DocumentNode.builder()
        .libraryId(libraryId)
        .parentId(directoryId)
        .name(uniqueName)
        .storagePath(storageResult.getStoragePath())
        .size(storageResult.getSize())
        .mimeType(mimeType)
        .createdBy(uploadedBy)
        .build();

    // 5. 添加元数据
    return (DocumentNode) nodeRepository.add(document);
  }

  /**
   * 生成唯一的文件名，如果文件名已存在，则添加 (1)、(2) 等后缀
   *
   * @param libraryId 文档库ID
   * @param directoryId 目录ID
   * @param originalName 原始文件名
   * @return 唯一的文件名
   */
  private String generateUniqueFileName(String libraryId, String directoryId, String originalName) {
    String baseName = originalName;
    String extension = "";

    // 分离文件名和扩展名
    int lastDotIndex = originalName.lastIndexOf('.');
    if (lastDotIndex > 0 && lastDotIndex < originalName.length() - 1) {
      baseName = originalName.substring(0, lastDotIndex);
      extension = originalName.substring(lastDotIndex); // 包含 "."
    }

    // 如果原始文件名不存在，直接返回
    if (!nodeRepository.existsByLibraryIdAndParentIdAndName(libraryId, directoryId, originalName)) {
      return originalName;
    }

    // 尝试添加数字后缀，从 (1) 开始
    int counter = 1;
    String newName;
    do {
      newName = baseName + " (" + counter + ")" + extension;
      counter++;
    } while (nodeRepository.existsByLibraryIdAndParentIdAndName(libraryId, directoryId, newName));

    return newName;
  }

  @Override
  public Optional<DocumentNode> getDocument(String libraryId, String documentId) {
    return nodeRepository.findDocumentById(documentId);
  }

  @Override
  public Optional<InputStream> getDocumentContent(String libraryId, String documentId) {
    // 1. 获取文档元数据
    Optional<DocumentNode> documentOpt = nodeRepository.findDocumentById(documentId);
    if (!documentOpt.isPresent()) {
      return Optional.empty();
    }

    DocumentNode document = documentOpt.get();

    // 2. 从 StorageService 获取文件内容
    Optional<StorageService.RetrievedFile> retrievedFileOpt =
        storageService.retrieve(document.getStoragePath());

    // 3. 返回输入流
    return retrievedFileOpt.map(StorageService.RetrievedFile::getInputStream);
  }

  // ==================== 删除操作 ====================

  @Override
  @Transactional
  public void deleteDocument(String libraryId, String documentId, String deletedBy) {
    // 1. 获取文档元数据
    Optional<DocumentNode> documentOpt = nodeRepository.findDocumentById(documentId);
    if (!documentOpt.isPresent()) {
      throw new ResourceNotFoundException("文档", documentId);
    }

    DocumentNode document = documentOpt.get();

    // Persist cleanup responsibility in the same transaction as the node deletion.
    cleanup.afterDeletion(document.getStoragePath());

    // 3. 删除元数据
    nodeRepository.deleteById(documentId);
  }

  @Override
  @Transactional
  public void deleteDirectory(String libraryId, String directoryId, String deletedBy) {
    // 1. 验证目录存在
    Optional<DirectoryNode> directoryOpt = nodeRepository.findDirectoryById(directoryId);
    if (!directoryOpt.isPresent()) {
      throw new ResourceNotFoundException("目录", directoryId);
    }

    // 2. 递归删除所有子节点
    deleteDirectoryRecursively(directoryId);
  }

  /**
   * 递归删除目录及其所有子节点
   *
   * @param directoryId 目录ID
   */
  private void deleteDirectoryRecursively(String directoryId) {
    // 1. 获取所有子节点
    List<LibraryNode> children = nodeRepository.findByParentId(directoryId);

    // 2. 递归删除每个子节点
    for (LibraryNode child : children) {
      if (child instanceof DirectoryNode) {
        // 递归删除子目录
        deleteDirectoryRecursively(child.getId());
      } else if (child instanceof DocumentNode) {
        // 删除文档
        DocumentNode doc = (DocumentNode) child;
        cleanup.afterDeletion(doc.getStoragePath());
        nodeRepository.deleteById(doc.getId());
      }
    }

    // 3. 删除目录本身
    nodeRepository.deleteById(directoryId);
  }

  // ==================== 移动和重命名 ====================

  @Override
  public void moveDocument(
      String libraryId, String documentId, String targetDirectoryId, String movedBy) {
    // 1. 获取文档
    DocumentNode document =
        nodeRepository
            .findDocumentById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("文档", documentId));

    // 2. 验证目标目录存在
    Optional<DirectoryNode> targetDirOpt = nodeRepository.findDirectoryById(targetDirectoryId);
    if (!targetDirOpt.isPresent()) {
      throw new ResourceNotFoundException("目标目录", targetDirectoryId);
    }

    // 3. 验证目标位置名称不冲突（同一文档库的同一目录下）
    if (nodeRepository.existsByLibraryIdAndParentIdAndNameAndIdNot(
        libraryId, targetDirectoryId, document.getName(), documentId)) {
      throw new DuplicateNameException(document.getName(), targetDirectoryId);
    }

    // 4. 更新父目录
    DocumentNode updatedDocument = document.toBuilder()
        .parentId(targetDirectoryId)
        .updatedBy(movedBy)
        .build();

    // 5. 更新
    nodeRepository.update(updatedDocument);
  }

  @Override
  public void moveDirectory(
      String libraryId, String directoryId, String targetDirectoryId, String movedBy) {
    // 1. 获取目录
    DirectoryNode directory =
        nodeRepository
            .findDirectoryById(directoryId)
            .orElseThrow(() -> new ResourceNotFoundException("目录", directoryId));

    // 2. 验证目标目录存在
    Optional<DirectoryNode> targetDirOpt = nodeRepository.findDirectoryById(targetDirectoryId);
    if (!targetDirOpt.isPresent()) {
      throw new ResourceNotFoundException("目标目录", targetDirectoryId);
    }

    // 3. 验证不会产生循环移动（目标目录不能是源目录的子孙）
    if (nodeRepository.isDescendantOf(targetDirectoryId, directoryId)) {
      throw new CircularReferenceException(directoryId, targetDirectoryId);
    }

    // 4. 验证目标位置名称不冲突（同一文档库的同一目录下）
    if (nodeRepository.existsByLibraryIdAndParentIdAndNameAndIdNot(
        libraryId, targetDirectoryId, directory.getName(), directoryId)) {
      throw new DuplicateNameException(directory.getName(), targetDirectoryId);
    }

    // 5. 更新父目录
    DirectoryNode updatedDirectory = directory.toBuilder()
        .parentId(targetDirectoryId)
        .updatedBy(movedBy)
        .build();

    // 6. 更新
    nodeRepository.update(updatedDirectory);
  }

  @Override
  public void renameDocument(String libraryId, String documentId, String newName, String renamedBy) {
    // 1. 获取文档
    DocumentNode document =
        nodeRepository
            .findDocumentById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("文档", documentId));

    // 2. 验证新名称不冲突（同一文档库的同一父目录下）
    if (nodeRepository.existsByLibraryIdAndParentIdAndNameAndIdNot(
        libraryId, document.getParentId(), newName, documentId)) {
      throw new DuplicateNameException(newName, document.getParentId());
    }

    // 3. 更新名称
    DocumentNode updatedDocument = document.toBuilder()
        .name(newName)
        .updatedBy(renamedBy)
        .build();

    // 4. 更新
    nodeRepository.update(updatedDocument);
  }

  @Override
  public void renameDirectory(
      String libraryId, String directoryId, String newName, String renamedBy) {
    // 1. 获取目录
    DirectoryNode directory =
        nodeRepository
            .findDirectoryById(directoryId)
            .orElseThrow(() -> new ResourceNotFoundException("目录", directoryId));

    // 2. 验证新名称不冲突（同一文档库的同一父目录下）
    if (nodeRepository.existsByLibraryIdAndParentIdAndNameAndIdNot(
        libraryId, directory.getParentId(), newName, directoryId)) {
      throw new DuplicateNameException(newName, directory.getParentId());
    }

    // 3. 更新名称
    DirectoryNode updatedDirectory = directory.toBuilder()
        .name(newName)
        .updatedBy(renamedBy)
        .build();

    // 4. 更新
    nodeRepository.update(updatedDirectory);
  }

}
