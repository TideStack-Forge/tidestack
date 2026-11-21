package com.ouroboros.documentmanagement.persistence.repository;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.persistence.entity.DocumentLibraryEntity;
import com.ouroboros.documentmanagement.persistence.mapper.DocumentLibraryMapper;
import com.ouroboros.documentmanagement.repository.LibraryRepository;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import io.vavr.collection.HashMap;
import io.vavr.control.Try;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

/**
 * 档案库持久化实现类
 *
 * <p>使用 TypedDataModel 进行数据访问
 */
@Repository("libraryRepository")
public class LibraryRepositoryImpl implements LibraryRepository {

  private final TypedDataModel<String, DocumentLibraryEntity> dataModel;
  private final DocumentLibraryMapper mapper;

  public LibraryRepositoryImpl(TypedDataModel<String, DocumentLibraryEntity> dataModel) {
    this.dataModel = dataModel;
    this.mapper = new DocumentLibraryMapper();
  }

  @Override
  public DocumentLibrary save(DocumentLibrary library) {
    DocumentLibraryEntity entity = mapper.toEntity(library);

    // Ensure required fields are set with default values if not already set
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(java.time.LocalDateTime.now());
    }
    if (entity.getCreatedBy() == null && entity.getOwner() != null) {
      entity.setCreatedBy(entity.getOwner());
    }

    if (entity.getId() == null || entity.getId().isEmpty()) {
      // ID 为空，直接插入
      Try<DocumentLibraryEntity> insertResult = dataModel.insert(entity);
      return insertResult.map(mapper::toModel)
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } else {
      // ID 不为空，先查询
      Optional<DocumentLibraryEntity> existing = dataModel.get(entity.getId()).toJavaOptional();

      if (existing.isPresent()) {
        // 存在则更新
        Try<Long> updateResult = dataModel.update(entity.getId(), entity);
        if (updateResult.isFailure()) {
          throw OuroborosExceptionMapper.toOuroborosException(updateResult.getCause());
        }
        // 更新后重新查询返回最新数据
        return dataModel.get(entity.getId())
            .map(mapper::toModel)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      } else {
        // 不存在则插入
        Try<DocumentLibraryEntity> insertResult = dataModel.insert(entity);
        return insertResult.map(mapper::toModel)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      }
    }
  }

  @Override
  public DocumentLibrary add(DocumentLibrary library) {
    DocumentLibraryEntity entity = mapper.toEntity(library);

    // Ensure required fields are set with default values if not already set
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(java.time.LocalDateTime.now());
    }
    if (entity.getCreatedBy() == null && entity.getOwner() != null) {
      entity.setCreatedBy(entity.getOwner());
    }

    Try<DocumentLibraryEntity> insertResult = dataModel.insert(entity);
    return insertResult.map(mapper::toModel)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public DocumentLibrary update(DocumentLibrary library) {
    DocumentLibraryEntity entity = mapper.toEntity(library);
    Try<Long> updateResult = dataModel.update(entity.getId(), entity);
    if (updateResult.isFailure()) {
      throw OuroborosExceptionMapper.toOuroborosException(updateResult.getCause());
    }
    // 更新后重新查询返回最新数据
    return dataModel.get(entity.getId())
        .map(mapper::toModel)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public Optional<DocumentLibrary> findById(String id) {
    return dataModel.get(id)
        .map(mapper::toModel)
        .toJavaOptional();
  }

  @Override
  public Optional<DocumentLibrary> findByName(String name) {
    Map<String, Object> where = HashMap.<String, Object>of("name", name).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.isEmpty() ? null : mapper.toModel(list.get(0)))
        .toJavaOptional();
  }

  @Override
  public List<DocumentLibrary> findAll() {
    return findAll(false);
  }

  @Override
  public List<DocumentLibrary> findAll(boolean includeDeleted) {
    Map<String, Object> where = includeDeleted
        ? Collections.emptyMap()
        : HashMap.<String, Object>of("deleted", false).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<DocumentLibrary> findByOwner(String ownerId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "owner", ownerId,
        "deleted", false
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<DocumentLibrary> findByAdmin(String adminId) {
    // 查询所有未删除的档案库，然后在内存中过滤
    Map<String, Object> where = HashMap.<String, Object>of("deleted", false).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .filter(library -> library.getAdminIds().contains(adminId))
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public boolean existsByName(String name) {
    Map<String, Object> where = HashMap.<String, Object>of("name", name).toJavaMap();
    return dataModel.count(where).getOrElse(0L) > 0;
  }

  @Override
  public boolean existsByNameAndIdNot(String name, String excludeId) {
    Map<String, Object> where = HashMap.<String, Object>of("name", name).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .anyMatch(entity -> !entity.getId().equals(excludeId)))
        .getOrElse(false);
  }

  @Override
  public void deleteById(String id) {
    dataModel.delete(id);
  }

  @Override
  public long count() {
    return dataModel.count(Collections.emptyMap()).getOrElse(0L);
  }

  @Override
  public long countByOwner(String ownerId) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "owner", ownerId,
        "deleted", false
    ).toJavaMap();

    return dataModel.count(where).getOrElse(0L);
  }
}
