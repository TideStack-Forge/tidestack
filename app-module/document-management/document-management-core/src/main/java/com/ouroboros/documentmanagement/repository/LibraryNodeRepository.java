package com.ouroboros.documentmanagement.repository;

import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.NodeType;

import java.util.List;
import java.util.Optional;

/**
 * 文档库节点持久化接口
 *
 * <p>负责文档库中所有节点（目录和文档）的存储和检索，包括节点的层级关系、元数据等。
 *
 * <p>设计说明：
 * <ul>
 *   <li>统一管理目录和文档节点，通过 {@link NodeType} 区分类型</li>
 *   <li>支持层级查询（父子关系）</li>
 *   <li>支持路径查询和构建</li>
 *   <li>提供统计功能（文件数、总大小等）</li>
 * </ul>
 */
public interface LibraryNodeRepository {

  /**
   * 保存节点（目录或文档）
   *
   * @param node 节点实体
   * @return 保存后的节点
   */
  LibraryNode save(LibraryNode node);

  /**
   * 添加节点（直接插入）
   *
   * @param node 节点实体
   * @return 添加后的节点
   */
  LibraryNode add(LibraryNode node);

  /**
   * 更新节点（直接更新）
   *
   * @param node 节点实体
   * @return 更新后的节点
   */
  LibraryNode update(LibraryNode node);

  /**
   * 根据ID查询节点
   *
   * @param id 节点ID
   * @return 节点，如果不存在则返回 empty
   */
  Optional<LibraryNode> findById(String id);

  /**
   * 根据ID查询目录节点
   *
   * @param id 目录ID
   * @return 目录节点，如果不存在或不是目录则返回 empty
   */
  Optional<DirectoryNode> findDirectoryById(String id);

  /**
   * 根据ID查询文档节点
   *
   * @param id 文档ID
   * @return 文档节点，如果不存在或不是文档则返回 empty
   */
  Optional<DocumentNode> findDocumentById(String id);

  /**
   * 查询文档库的根目录
   *
   * @param libraryId 文档库ID
   * @return 根目录节点，如果不存在则返回 empty
   */
  Optional<DirectoryNode> findRootDirectory(String libraryId);

  /**
   * 查询指定父目录下的所有子节点
   *
   * @param parentId 父目录ID
   * @return 子节点列表
   */
  List<LibraryNode> findByParentId(String parentId);

  /**
   * 查询指定父目录下的所有子目录
   *
   * @param parentId 父目录ID
   * @return 子目录列表
   */
  List<DirectoryNode> findDirectoriesByParentId(String parentId);

  /**
   * 查询指定父目录下的所有文档
   *
   * @param parentId 父目录ID
   * @return 文档列表
   */
  List<DocumentNode> findDocumentsByParentId(String parentId);

  /**
   * 查询文档库下的所有节点
   *
   * @param libraryId 文档库ID
   * @return 节点列表
   */
  List<LibraryNode> findByLibraryId(String libraryId);

  /**
   * 获取文档库下的所有目录
   *
   * @param libraryId 文档库ID
   * @return 目录列表
   */
  List<DirectoryNode> findDirectoriesByLibraryId(String libraryId);

  /**
   * 查询文档库下的所有文档
   *
   * @param libraryId 文档库ID
   * @return 文档列表
   */
  List<DocumentNode> findDocumentsByLibraryId(String libraryId);

  /**
   * 根据名称查询指定父目录下的节点
   *
   * @param parentId 父目录ID
   * @param name 节点名称
   * @return 节点，如果不存在则返回 empty
   */
  Optional<LibraryNode> findByParentIdAndName(String parentId, String name);

  /**
   * 根据名称查询指定文档库和父目录下的节点
   *
   * @param libraryId 文档库ID
   * @param parentId 父目录ID
   * @param name 节点名称
   * @return 节点，如果不存在则返回 empty
   */
  Optional<LibraryNode> findByLibraryIdAndParentIdAndName(String libraryId, String parentId, String name);

  /**
   * 检查指定父目录下是否存在同名节点
   *
   * @param parentId 父目录ID
   * @param name 节点名称
   * @return 如果存在返回 true
   */
  boolean existsByParentIdAndName(String parentId, String name);

  /**
   * 检查指定文档库和父目录下是否存在同名节点
   *
   * @param libraryId 文档库ID
   * @param parentId 父目录ID
   * @param name 节点名称
   * @return 如果存在返回 true
   */
  boolean existsByLibraryIdAndParentIdAndName(String libraryId, String parentId, String name);

  /**
   * 检查指定父目录下是否存在同名节点（排除指定ID）
   *
   * @param parentId 父目录ID
   * @param name 节点名称
   * @param excludeId 要排除的节点ID
   * @return 如果存在返回 true
   */
  boolean existsByParentIdAndNameAndIdNot(String parentId, String name, String excludeId);

  /**
   * 检查指定文档库和父目录下是否存在同名节点（排除指定ID）
   *
   * @param libraryId 文档库ID
   * @param parentId 父目录ID
   * @param name 节点名称
   * @param excludeId 要排除的节点ID
   * @return 如果存在返回 true
   */
  boolean existsByLibraryIdAndParentIdAndNameAndIdNot(String libraryId, String parentId, String name, String excludeId);

  /**
   * 删除节点（物理删除）
   *
   * @param id 节点ID
   */
  void deleteById(String id);

  /**
   * 批量删除节点（物理删除）
   *
   * @param ids 节点ID列表
   */
  void deleteByIds(List<String> ids);

  /**
   * 递归删除目录及其所有子节点
   *
   * @param directoryId 目录ID
   */
  void deleteDirectoryRecursively(String directoryId);

  /**
   * 统计文档库的文档数量
   *
   * @param libraryId 文档库ID
   * @return 文档数量
   */
  long countDocumentsByLibraryId(String libraryId);

  /**
   * 统计文档库的总大小（所有文档大小之和）
   *
   * @param libraryId 文档库ID
   * @return 总大小（字节）
   */
  long sumDocumentSizeByLibraryId(String libraryId);

  /**
   * 统计目录下的直接子节点数量
   *
   * @param parentId 父目录ID
   * @return 子节点数量
   */
  long countByParentId(String parentId);

  /**
   * 统计目录下的文档数量（递归统计）
   *
   * @param directoryId 目录ID
   * @return 文档数量
   */
  long countDocumentsRecursively(String directoryId);

  /**
   * 统计目录的总大小（递归统计所有文档大小之和）
   *
   * @param directoryId 目录ID
   * @return 总大小（字节）
   */
  long sumDocumentSizeRecursively(String directoryId);

  /**
   * 查询从根目录到指定节点的路径
   *
   * @param nodeId 节点ID
   * @return 路径节点列表（从根到目标节点）
   */
  List<LibraryNode> findPathToNode(String nodeId);

  /**
   * 检查目标目录是否是源目录的子孙节点
   * <p>用于防止将目录移动到自己的子目录下
   *
   * @param sourceId 源目录ID
   * @param targetId 目标目录ID
   * @return 如果是子孙节点返回 true
   */
  boolean isDescendantOf(String sourceId, String targetId);

  long countByLibraryId(String libraryId);

  /** Includes references in soft-deleted, restorable libraries. Query failures propagate. */
  boolean existsByStoragePath(String storagePath);
}
