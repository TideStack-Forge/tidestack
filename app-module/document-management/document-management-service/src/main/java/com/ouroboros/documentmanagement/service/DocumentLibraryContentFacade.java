package com.ouroboros.documentmanagement.service;

import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * 档案库内容管理和使用门面服务
 *
 * <p><b>职责边界</b>：从档案库使用者的角度提供所有内容管理和使用功能，包括：
 * <ul>
 *   <li>目录树浏览、文件列表查看</li>
 *   <li>文件查询、搜索</li>
 *   <li>文件查看（预览）、下载</li>
 *   <li>文件上传</li>
 *   <li>目录创建</li>
 *   <li>文件/目录移动、重命名、删除</li>
 *   <li>权限查询和管理（需要 MANAGE 权限）</li>
 *   <li>统计查询</li>
 * </ul>
 *
 * <p><b>权限控制</b>：
 * <ul>
 *   <li>所有操作都需要验证用户权限</li>
 *   <li>根据用户的有效权限（继承 + 自身）判断是否允许操作</li>
 *   <li>权限管理操作需要用户对资源拥有 MANAGE 权限</li>
 * </ul>
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>方法命名体现业务含义（如 browseDirectory、uploadFile）</li>
 *   <li>所有方法都包含 userId 参数用于权限验证</li>
 *   <li>返回类型符合业务场景（Optional、List、统计对象等）</li>
 *   <li>协调调用底层服务（LibraryContentService、ACLService、PermissionAdminService）</li>
 * </ul>
 */
public interface DocumentLibraryContentFacade {

  // ==================== 目录浏览和文件列表 ====================

  /**
   * 获取档案库的根目录
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对档案库是否有访问权限</li>
   *   <li>返回根目录节点信息</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param userId 用户ID
   * @return 根目录节点，如果档案库不存在或用户无权限则返回 empty
   */
  Optional<DirectoryNode> getRootDirectory(String libraryId, String userId);

  /**
   * 浏览指定目录的内容（子目录和文件列表）
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对目录是否有访问权限</li>
   *   <li>返回该目录下的所有子节点（目录和文件）</li>
   *   <li>只返回用户有权限访问的节点</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @return 子节点列表（包括目录和文件）
   */
  List<LibraryNode> browseDirectory(String libraryId, String directoryId, String userId);

  /**
   * 获取指定目录的子目录列表
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @return 子目录列表
   */
  List<DirectoryNode> listSubdirectories(String libraryId, String directoryId, String userId);

  /**
   * 获取指定目录的文件列表
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @return 文件列表
   */
  List<DocumentNode> listDocuments(String libraryId, String directoryId, String userId);

  /**
   * 获取从根目录到指定节点的完整路径（面包屑导航）
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对目标节点是否有访问权限</li>
   *   <li>返回从根目录到目标节点的完整路径</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param nodeId 节点ID（目录或文件）
   * @param userId 用户ID
   * @return 路径节点列表（从根到目标）
   */
  List<LibraryNode> getNodePath(String libraryId, String nodeId, String userId);

  /**
   * 构建档案库的完整目录树
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对档案库是否有权限</li>
   *   <li>递归构建整个目录树结构</li>
   *   <li>只包含用户有权限访问的目录</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param userId 用户ID
   * @return 目录树根节点
   */
  Optional<DirectoryNode> getDirectoryTree(String libraryId, String userId);

  /**
   * 构建档案库的指定目录树
   *
   * <p>业务规则：
   * <ul>
   *   <li>递归构建指定目录下的目录树结构</li>
   *   <li>只包含用户有权限访问的目录</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @return 目录树根节点
   */
  Optional<DirectoryNode> getDirectoryTree(String libraryId, String directoryId, String userId);

  // ==================== 文件查询和搜索 ====================

  /**
   * 根据ID获取文件详情
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param userId 用户ID
   * @return 文件节点，如果不存在或无权限则返回 empty
   */
  Optional<DocumentNode> getDocument(String libraryId, String documentId, String userId);

  /**
   * 根据ID获取目录详情
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @return 目录节点，如果不存在或无权限则返回 empty
   */
  Optional<DirectoryNode> getDirectory(String libraryId, String directoryId, String userId);

  /**
   * 在档案库中搜索文件（按名称模糊匹配）
   *
   * <p>业务规则：
   * <ul>
   *   <li>在整个档案库范围内搜索</li>
   *   <li>只返回用户有权限访问的文件</li>
   *   <li>支持文件名模糊匹配</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param namePattern 文件名模式（支持通配符）
   * @param userId 用户ID
   * @return 匹配的文件列表
   */
  List<DocumentNode> searchDocuments(String libraryId, String namePattern, String userId);

  /**
   * 在指定目录及其子目录中搜索文件
   *
   * @param libraryId 档案库ID
   * @param directoryId 起始目录ID
   * @param namePattern 文件名模式
   * @param userId 用户ID
   * @return 匹配的文件列表
   */
  List<DocumentNode> searchDocumentsInDirectory(
      String libraryId, String directoryId, String namePattern, String userId);

  // ==================== 文件查看和下载 ====================

  /**
   * 预览文件（在线查看）
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否有 VIEW 权限</li>
   *   <li>返回文件内容流用于在线预览</li>
   *   <li>记录预览操作日志</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param userId 用户ID
   * @return 文件内容流
   */
  Optional<InputStream> previewDocument(String libraryId, String documentId, String userId);

  /**
   * 下载文件
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户是否有 DOWNLOAD 权限</li>
   *   <li>返回文件内容流用于下载</li>
   *   <li>记录下载操作日志</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param userId 用户ID
   * @return 文件内容流
   */
  Optional<InputStream> downloadDocument(String libraryId, String documentId, String userId);

  // ==================== 文件上传 ====================

  /**
   * 上传文件到指定目录
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对目录是否有 CREATE_FILE 权限</li>
   *   <li>验证档案库配额是否超限</li>
   *   <li>验证文件名是否冲突</li>
   *   <li>存储文件内容并创建文档节点</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param directoryId 目标目录ID
   * @param fileName 文件名
   * @param contentType 文件MIME类型
   * @param fileSize 文件大小（字节）
   * @param inputStream 文件内容流
   * @param userId 用户ID
   * @return 创建的文档节点
   */
  DocumentNode uploadFile(
      String libraryId,
      String directoryId,
      String fileName,
      String contentType,
      long fileSize,
      InputStream inputStream,
      String userId);

  // ==================== 目录创建 ====================

  /**
   * 在指定目录下创建子目录
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对父目录是否有 CREATE_DIR 权限</li>
   *   <li>验证目录名是否冲突</li>
   *   <li>创建目录节点</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param parentDirectoryId 父目录ID
   * @param directoryName 目录名
   * @param userId 用户ID
   * @return 创建的目录节点
   */
  DirectoryNode createDirectory(
      String libraryId, String parentDirectoryId, String directoryName, String userId);

  // ==================== 移动和重命名 ====================

  /**
   * 移动文件到其他目录
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对源文件是否有 RENAME_MOVE 权限</li>
   *   <li>验证用户对目标目录是否有 CREATE_FILE 权限</li>
   *   <li>验证目标目录中文件名是否冲突</li>
   *   <li>更新文件的 parentId</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param targetDirectoryId 目标目录ID
   * @param userId 用户ID
   * @return 移动后的文档节点
   */
  DocumentNode moveDocument(
      String libraryId, String documentId, String targetDirectoryId, String userId);

  /**
   * 移动目录到其他目录
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对源目录是否有 RENAME_MOVE 权限</li>
   *   <li>验证用户对目标目录是否有 CREATE_DIR 权限</li>
   *   <li>验证不能移动到自己的子目录下（防止循环）</li>
   *   <li>验证目标目录中目录名是否冲突</li>
   *   <li>更新目录的 parentId</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param targetDirectoryId 目标父目录ID
   * @param userId 用户ID
   * @return 移动后的目录节点
   */
  DirectoryNode moveDirectory(
      String libraryId, String directoryId, String targetDirectoryId, String userId);

  /**
   * 重命名文件
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对文件是否有 RENAME_MOVE 权限</li>
   *   <li>验证新文件名在同目录下是否冲突</li>
   *   <li>更新文件名</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param newName 新文件名
   * @param userId 用户ID
   * @return 重命名后的文档节点
   */
  DocumentNode renameDocument(String libraryId, String documentId, String newName, String userId);

  /**
   * 重命名目录
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对目录是否有 RENAME_MOVE 权限</li>
   *   <li>验证新目录名在同目录下是否冲突</li>
   *   <li>更新目录名</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param newName 新目录名
   * @param userId 用户ID
   * @return 重命名后的目录节点
   */
  DirectoryNode renameDirectory(String libraryId, String directoryId, String newName, String userId);

  // ==================== 删除操作 ====================

  /**
   * 删除文件
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对文件是否有 DELETE_FILE 权限</li>
   *   <li>删除文件节点和物理文件</li>
   *   <li>如果启用了回收站，则移到回收站</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param userId 用户ID
   */
  void deleteDocument(String libraryId, String documentId, String userId);

  /**
   * 删除目录（递归删除所有子节点）
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对目录是否有 DELETE_DIR 权限</li>
   *   <li>验证目录不是根目录</li>
   *   <li>递归删除所有子目录和文件</li>
   *   <li>如果启用了回收站，则移到回收站</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   */
  void deleteDirectory(String libraryId, String directoryId, String userId);

  // ==================== 权限查询 ====================

  /**
   * 查询用户对指定文件的有效权限
   *
   * <p>业务规则：
   * <ul>
   *   <li>计算继承权限 + 自身权限</li>
   *   <li>返回用户对该文件的所有有效权限</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param userId 用户ID
   * @return 用户的有效权限集合
   */
  PermissionSet getDocumentPermissions(String libraryId, String documentId, String userId);

  /**
   * 查询用户对指定目录的有效权限
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @return 用户的有效权限集合
   */
  PermissionSet getDirectoryPermissions(String libraryId, String directoryId, String userId);

  /**
   * 检查用户对文件是否有指定权限
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param userId 用户ID
   * @param permission 要检查的权限
   * @return 如果有该权限返回 true
   */
  boolean hasDocumentPermission(
      String libraryId, String documentId, String userId, Permission permission);

  /**
   * 检查用户对目录是否有指定权限
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @param permission 要检查的权限
   * @return 如果有该权限返回 true
   */
  boolean hasDirectoryPermission(
      String libraryId, String directoryId, String userId, Permission permission);

  // ==================== 权限管理（需要 MANAGE 权限）====================

  /**
   * 查询文件的所有权限条目
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对文件是否有 MANAGE 权限</li>
   *   <li>返回文件的所有权限条目（不包括继承的）</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param userId 用户ID
   * @return 权限条目列表
   */
  List<AclEntry> getDocumentAclEntries(String libraryId, String documentId, String userId);

  /**
   * 查询目录的所有权限条目
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @return 权限条目列表
   */
  List<AclEntry> getDirectoryAclEntries(String libraryId, String directoryId, String userId);

  /**
   * 为文件添加权限条目
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对文件是否有 MANAGE 权限</li>
   *   <li>添加新的权限条目</li>
   *   <li>如果已存在，则合并权限</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param principal 主体标识
   * @param principalType 主体类型
   * @param permissions 要授予的权限集合
   * @param userId 操作用户ID
   * @return 创建或更新的权限条目
   */
  AclEntry grantDocumentPermission(
      String libraryId,
      String documentId,
      String principal,
      String principalType,
      PermissionSet permissions,
      String userId);

  /**
   * 为目录添加权限条目
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param principal 主体标识
   * @param principalType 主体类型
   * @param permissions 要授予的权限集合
   * @param userId 操作用户ID
   * @return 创建或更新的权限条目
   */
  AclEntry grantDirectoryPermission(
      String libraryId,
      String directoryId,
      String principal,
      String principalType,
      PermissionSet permissions,
      String userId);

  /**
   * 撤销文件的权限条目
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对文件是否有 MANAGE 权限</li>
   *   <li>删除指定的权限条目</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param documentId 文件ID
   * @param principal 主体标识
   * @param principalType 主体类型
   * @param userId 操作用户ID
   */
  void revokeDocumentPermission(
      String libraryId, String documentId, String principal, String principalType, String userId);

  /**
   * 撤销目录的权限条目
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param principal 主体标识
   * @param principalType 主体类型
   * @param userId 操作用户ID
   */
  void revokeDirectoryPermission(
      String libraryId, String directoryId, String principal, String principalType, String userId);

  // ==================== 统计查询 ====================

  /**
   * 查询目录统计信息
   *
   * <p>业务规则：
   * <ul>
   *   <li>验证用户对目录是否有访问权限</li>
   *   <li>统计目录下的文件数、子目录数、总大小等</li>
   * </ul>
   *
   * @param libraryId 档案库ID
   * @param directoryId 目录ID
   * @param userId 用户ID
   * @return 统计信息
   */
  DirectoryStatistics getDirectoryStatistics(String libraryId, String directoryId, String userId);

  /**
   * 目录统计信息
   */
  interface DirectoryStatistics {
    /** 目录ID */
    String getDirectoryId();

    /** 直接子目录数量 */
    long getSubdirectoryCount();

    /** 直接文件数量 */
    long getDocumentCount();

    /** 递归统计所有子目录数量 */
    long getTotalSubdirectoryCount();

    /** 递归统计所有文件数量 */
    long getTotalDocumentCount();

    /** 递归统计所有文件总大小（字节） */
    long getTotalSize();
  }
}
