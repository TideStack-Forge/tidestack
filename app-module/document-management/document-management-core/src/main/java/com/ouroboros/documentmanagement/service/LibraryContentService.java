package com.ouroboros.documentmanagement.service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentNode;

/**
 * 文档库服务接口，提供文档和目录的原子化CRUD操作，通过 LibraryNodeRepository 和 StorageService 进行存储和元数据管理
 */
public interface LibraryContentService {
    /**
     * 创建目录
     *
     * @param libraryId   文档库ID
     * @param parentId   父目录ID（如果为null，则在根目录下创建）
     * @param name       目录名称
     * @param createdBy  创建人ID
     * @return 创建的目录元数据
     */
    DirectoryNode createDirectory(String libraryId, String parentId, String name, String createdBy);

    /**
     * 获取目录信息
     *
     * @param libraryId 文档库ID
     * @param directoryId 目录ID
     * @return 目录元数据
     */
    Optional<DirectoryNode> getDirectory(String libraryId, String directoryId);

    /**
     * 列出目录下的所有子目录
     *
     * @param libraryId 文档库ID
     * @param directoryId 目录ID
     * @return 子目录列表
     */
    List<DirectoryNode> listDirectories(String libraryId, String directoryId);

    /**
     * 列出目录下的所有文档
     *
     * @param libraryId 文档库ID
     * @param directoryId 目录ID
     * @return 文档列表
     */
    List<DocumentNode> listDocuments(String libraryId, String directoryId);

    /**
     * 上传文档
     *
     * @param libraryId   文档库ID
     * @param directoryId 目录ID
     * @param name        文档名称
     * @param inputStream 文件输入流
     * @param size        文件大小
     * @param mimeType    MIME类型
     * @param uploadedBy  上传人ID
     * @return 上传的文档元数据
     */
    DocumentNode uploadDocument(String libraryId, String directoryId, String name,
                                InputStream inputStream, Long size, String mimeType,
                                String uploadedBy);

    /**
     * 获取文档信息
     *
     * @param libraryId 文档库ID
     * @param documentId 文档ID
     */
    Optional<DocumentNode> getDocument(String libraryId, String documentId);

    /**
     * 获取文档内容
     *
     * @param libraryId 文档库ID
     * @param documentId 文档ID
     */
    Optional<InputStream> getDocumentContent(String libraryId, String documentId);

    /**
     * 删除文档
     *
     * @param libraryId 文档库ID
     * @param documentId 文档ID
     * @param deletedBy  删除人ID
     */
    void deleteDocument(String libraryId, String documentId, String deletedBy);

    /**
     * 删除目录（递归删除子目录和文档）
     *
     * @param libraryId 文档库ID
     * @param directoryId 目录ID
     * @param deletedBy  删除人ID
     */
    void deleteDirectory(String libraryId, String directoryId, String deletedBy);

    /**
     * 移动文档
     *
     * @param libraryId 文档库ID
     * @param documentId 文档ID
     * @param movedBy    移动人ID
     */
    void moveDocument(String libraryId, String documentId, String targetDirectoryId, String movedBy);

    /**
     * 移动目录
     *
     * @param libraryId 文档库ID
     * @param targetDirectoryId 目标父目录ID
     * @param movedBy           移动人ID
     */
    void moveDirectory(String libraryId, String directoryId, String targetDirectoryId, String movedBy);

    /**
     * 重命名文档
     *
     * @param libraryId 文档库ID
     * @param newName    新名称
     * @param renamedBy  重命名人ID
     */
    void renameDocument(String libraryId, String documentId, String newName, String renamedBy);

    /**
     * 重命名目录
     *
     * @param libraryId 文档库ID
     * @param newName     新名称
     * @param renamedBy   重命名人ID
     */
    void renameDirectory(String libraryId, String directoryId, String newName, String renamedBy);

    /**
     * 获取目录的完整路径
     *
     * @param libraryId 文档库ID
     * @return 路径列表（从根目录到当前目录）
     */
    List<DirectoryNode> getDirectoryPath(String libraryId, String directoryId);
}
