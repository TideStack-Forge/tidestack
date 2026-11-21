package com.ouroboros.documentmanagement.persistence.repository;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.NodeType;
import com.ouroboros.documentmanagement.persistence.entity.LibraryNodeEntity;
import com.ouroboros.documentmanagement.persistence.mapper.LibraryNodeMapper;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import io.vavr.collection.HashMap;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

/**
 * 档案库节点持久化实现类
 *
 * <p>使用 TypedDataModel 进行数据访问
 */
@Repository("libraryNodeRepository")
public class LibraryNodeRepositoryImpl implements LibraryNodeRepository {

  private final TypedDataModel<String, LibraryNodeEntity> dataModel;
  private final LibraryNodeMapper mapper;

  public LibraryNodeRepositoryImpl(TypedDataModel<String, LibraryNodeEntity> dataModel) {
    this.dataModel = dataModel;
    this.mapper = new LibraryNodeMapper();
  }

  @Override
  public LibraryNode save(LibraryNode node) {
    LibraryNodeEntity entity = mapper.toEntity(node);

    if (entity.getId() == null || entity.getId().isEmpty()) {
      // ID 为空，直接插入
      var insertResult = dataModel.insert(entity);
      return insertResult.map(mapper::toModel)
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } else {
      // ID 不为空，先查询
      Optional<LibraryNodeEntity> existing = dataModel.get(entity.getId()).toJavaOptional();

      if (existing.isPresent()) {
        // 存在则更新
        var updateResult = dataModel.update(entity.getId(), entity);
        if (updateResult.isFailure()) {
          throw OuroborosExceptionMapper.toOuroborosException(updateResult.getCause());
        }
        // 更新后重新查询返回最新数据
        return dataModel.get(entity.getId())
            .map(mapper::toModel)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      } else {
        // 不存在则插入
        var insertResult = dataModel.insert(entity);
        return insertResult.map(mapper::toModel)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      }
    }
  }

  @Override
  public LibraryNode add(LibraryNode node) {
    LibraryNodeEntity entity = mapper.toEntity(node);
    var insertResult = dataModel.insert(entity);
    return insertResult.map(mapper::toModel)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public LibraryNode update(LibraryNode node) {
    LibraryNodeEntity entity = mapper.toEntity(node);
    var updateResult = dataModel.update(entity.getId(), entity);
    if (updateResult.isFailure()) {
      throw OuroborosExceptionMapper.toOuroborosException(updateResult.getCause());
    }
    // 更新后重新查询返回最新数据
    return dataModel.get(entity.getId())
        .map(mapper::toModel)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public Optional<LibraryNode> findById(String id) {
    return dataModel.query().where(com.ouroboros.data.dsl.query.Query.field("id").eq(id)).clause("LIMIT", 1)
        .execute().getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().findFirst().map(mapper::toModel);
  }

  @Override
  public Optional<DirectoryNode> findDirectoryById(String id) {
    return findById(id)
        .filter(node -> node.getType() == NodeType.DIRECTORY)
        .map(node -> (DirectoryNode) node);
  }

  @Override
  public Optional<DocumentNode> findDocumentById(String id) {
    return findById(id)
        .filter(node -> node.getType() == NodeType.DOCUMENT)
        .map(node -> (DocumentNode) node);
  }

  @Override
  public Optional<DirectoryNode> findRootDirectory(String libraryId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId,
        "parentId", (Object) null,
        "type", "DIRECTORY"
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.isEmpty() ? null : (DirectoryNode) mapper.toModel(list.get(0)))
        .toJavaOptional();
  }

  @Override
  public List<LibraryNode> findByParentId(String parentId) {
    Map<String, Object> where = HashMap.<String, Object>of("parentId", parentId).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public List<DirectoryNode> findDirectoriesByParentId(String parentId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "parentId", parentId,
        "type", "DIRECTORY"
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(entity -> (DirectoryNode) mapper.toModel(entity))
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<DocumentNode> findDocumentsByParentId(String parentId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "parentId", parentId,
        "type", "DOCUMENT"
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(entity -> (DocumentNode) mapper.toModel(entity))
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<LibraryNode> findByLibraryId(String libraryId) {
    Map<String, Object> where = HashMap.<String, Object>of("libraryId", libraryId).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<DirectoryNode> findDirectoriesByLibraryId(String libraryId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId,
        "type", "DIRECTORY"
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();
    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(entity -> (DirectoryNode) mapper.toModel(entity))
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }


  @Override
  public List<DocumentNode> findDocumentsByLibraryId(String libraryId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId,
        "type", "DOCUMENT"
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(entity -> (DocumentNode) mapper.toModel(entity))
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public Optional<LibraryNode> findByParentIdAndName(String parentId, String name) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "parentId", parentId,
        "name", name
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.isEmpty() ? null : mapper.toModel(list.get(0)))
        .toJavaOptional();
  }

  @Override
  public Optional<LibraryNode> findByLibraryIdAndParentIdAndName(String libraryId, String parentId, String name) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId,
        "parentId", parentId,
        "name", name
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.isEmpty() ? null : mapper.toModel(list.get(0)))
        .toJavaOptional();
  }

  @Override
  public boolean existsByParentIdAndName(String parentId, String name) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "parentId", parentId,
        "name", name
    ).toJavaMap();

    return dataModel.count(where).getOrElse(0L) > 0;
  }

  @Override
  public boolean existsByLibraryIdAndParentIdAndName(String libraryId, String parentId, String name) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId,
        "parentId", parentId,
        "name", name
    ).toJavaMap();

    return dataModel.count(where).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException) > 0;
  }

  @Override
  public boolean existsByParentIdAndNameAndIdNot(String parentId, String name, String excludeId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "parentId", parentId,
        "name", name
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .anyMatch(entity -> !entity.getId().equals(excludeId)))
        .getOrElse(false);
  }

  @Override
  public boolean existsByLibraryIdAndParentIdAndNameAndIdNot(String libraryId, String parentId, String name, String excludeId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId,
        "parentId", parentId,
        "name", name
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .anyMatch(entity -> !entity.getId().equals(excludeId)))
        .getOrElse(false);
  }

  @Override
  public void deleteById(String id) {
    dataModel.delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public void deleteByIds(List<String> ids) {
    dataModel.delete(ids).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public void deleteDirectoryRecursively(String directoryId) {
    // 递归查找所有子节点
    List<String> allNodeIds = collectDescendantIds(directoryId);
    allNodeIds.add(directoryId);

    // 批量删除
    deleteByIds(allNodeIds);
  }

  /**
   * 递归收集所有子孙节点ID
   */
  private List<String> collectDescendantIds(String parentId) {
    List<String> result = new ArrayList<>();
    List<LibraryNode> children = findByParentId(parentId);

    for (LibraryNode child : children) {
      result.add(child.getId());
      if (child.getType() == NodeType.DIRECTORY) {
        result.addAll(collectDescendantIds(child.getId()));
      }
    }

    return result;
  }

  @Override
  public long countDocumentsByLibraryId(String libraryId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId,
        "type", "DOCUMENT"
    ).toJavaMap();

    return dataModel.count(where).getOrElse(0L);
  }

  @Override
  public long sumDocumentSizeByLibraryId(String libraryId) {
    List<DocumentNode> documents = findDocumentsByLibraryId(libraryId);
    return documents.stream()
        .mapToLong(DocumentNode::getSize)
        .sum();
  }

  @Override
  public long countByParentId(String parentId) {
    Map<String, Object> where = HashMap.<String, Object>of("parentId", parentId).toJavaMap();
    return dataModel.count(where).getOrElse(0L);
  }

  @Override
  public long countDocumentsRecursively(String directoryId) {
    long count = 0;

    // 统计直接子文档
    Map<String, Object> where = HashMap.<String, Object>of(
        "parentId", directoryId,
        "type", "DOCUMENT"
    ).toJavaMap();
    count += dataModel.count(where).getOrElse(0L);

    // 递归统计子目录
    List<DirectoryNode> subdirs = findDirectoriesByParentId(directoryId);
    for (DirectoryNode subdir : subdirs) {
      count += countDocumentsRecursively(subdir.getId());
    }

    return count;
  }

  @Override
  public long sumDocumentSizeRecursively(String directoryId) {
    long totalSize = 0;

    // 统计直接子文档大小
    List<DocumentNode> documents = findDocumentsByParentId(directoryId);
    totalSize += documents.stream()
        .mapToLong(DocumentNode::getSize)
        .sum();

    // 递归统计子目录
    List<DirectoryNode> subdirs = findDirectoriesByParentId(directoryId);
    for (DirectoryNode subdir : subdirs) {
      totalSize += sumDocumentSizeRecursively(subdir.getId());
    }

    return totalSize;
  }

  @Override
  public List<LibraryNode> findPathToNode(String nodeId) {
    List<LibraryNode> path = new ArrayList<>();

    Optional<LibraryNode> nodeOpt = findById(nodeId);
    if (!nodeOpt.isPresent()) {
      return path;
    }

    LibraryNode current = nodeOpt.get();
    path.add(0, current);

    // 向上遍历到根节点
    while (current.getParentId() != null) {
      Optional<LibraryNode> parentOpt = findById(current.getParentId());
      if (!parentOpt.isPresent()) {
        break;
      }
      current = parentOpt.get();
      path.add(0, current);
    }

    return path;
  }

  @Override
  public boolean isDescendantOf(String sourceId, String targetId) {
    if (sourceId.equals(targetId)) {
      return true;
    }

    // 向上查找 targetId 的所有祖先
    Optional<LibraryNode> targetOpt = findById(targetId);
    if (!targetOpt.isPresent()) {
      return false;
    }

    LibraryNode current = targetOpt.get();
    while (current.getParentId() != null) {
      if (current.getParentId().equals(sourceId)) {
        return true;
      }
      Optional<LibraryNode> parentOpt = findById(current.getParentId());
      if (!parentOpt.isPresent()) {
        break;
      }
      current = parentOpt.get();
    }

    return false;
  }

  @Override
  public long countByLibraryId(String libraryId) {
    Map<String, Object> where = HashMap.<String, Object>of("libraryId", libraryId).toJavaMap();
    return dataModel.count(where).getOrElse(0L);
  }
  @Override
  public boolean existsByStoragePath(String key) {
    return !dataModel.query().where(com.ouroboros.data.dsl.query.Query.field("storagePath").eq(key)).clause("LIMIT", 1)
        .execute().getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).isEmpty();
  }
}
