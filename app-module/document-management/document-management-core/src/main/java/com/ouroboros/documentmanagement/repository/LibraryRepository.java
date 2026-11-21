package com.ouroboros.documentmanagement.repository;

import com.ouroboros.documentmanagement.model.DocumentLibrary;

import java.util.List;
import java.util.Optional;

/**
 * 文档库实体的持久化接口
 *
 * <p>负责文档库元数据的存储和检索，包括基本信息、权限配置、配额限制等。
 */
public interface LibraryRepository {

  /**
   * 保存文档库
   *
   * @param library 文档库实体
   * @return 保存后的文档库
   */
  DocumentLibrary save(DocumentLibrary library);

  /**
   * 添加文档库（直接插入）
   *
   * @param library 文档库实体
   * @return 添加后的文档库
   */
  DocumentLibrary add(DocumentLibrary library);

  /**
   * 更新文档库（直接更新）
   *
   * @param library 文档库实体
   * @return 更新后的文档库
   */
  DocumentLibrary update(DocumentLibrary library);

  /**
   * 根据ID查询文档库
   *
   * @param id 文档库ID
   * @return 文档库，如果不存在则返回 empty
   */
  Optional<DocumentLibrary> findById(String id);

  /**
   * 根据名称查询文档库
   *
   * @param name 文档库名称（唯一）
   * @return 文档库，如果不存在则返回 empty
   */
  Optional<DocumentLibrary> findByName(String name);

  /**
   * 查询用户拥有的文档库列表（作为 owner）
   *
   * @param userId 用户ID
   * @return 文档库列表
   */
  List<DocumentLibrary> findByOwner(String userId);

  /**
   * 查询用户作为管理员的文档库列表
   *
   * @param userId 用户ID
   * @return 文档库列表
   */
  List<DocumentLibrary> findByAdmin(String userId);

  /**
   * 查询所有文档库（不包括已删除）
   *
   * @return 文档库列表
   */
  List<DocumentLibrary> findAll();

  /**
   * 查询所有文档库（包括已删除）
   *
   * @param includeDeleted 是否包括已删除的文档库
   * @return 文档库列表
   */
  List<DocumentLibrary> findAll(boolean includeDeleted);

  /**
   * 检查文档库名称是否已存在
   *
   * @param name 文档库名称
   * @return 如果存在返回 true
   */
  boolean existsByName(String name);

  /**
   * 检查文档库名称是否已存在（排除指定ID）
   *
   * @param name 文档库名称
   * @param excludeId 要排除的文档库ID
   * @return 如果存在返回 true
   */
  boolean existsByNameAndIdNot(String name, String excludeId);

  /**
   * 删除文档库（物理删除）
   *
   * @param id 文档库ID
   */
  void deleteById(String id);

  /**
   * 统计用户拥有的文档库数量
   *
   * @param userId 用户ID
   * @return 文档库数量
   */
  long countByOwner(String userId);

  /**
   * 统计所有文档库数量（不包括已删除）
   *
   * @return 文档库数量
   */
  long count();
}
