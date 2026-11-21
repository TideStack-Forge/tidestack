package com.ouroboros.documentmanagement.persistence.repository;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.persistence.entity.AclEntryEntity;
import com.ouroboros.documentmanagement.persistence.mapper.AclEntryMapper;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import io.vavr.collection.HashMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

/**
 * 权限条目持久化实现类
 *
 * <p>使用 TypedDataModel 进行数据访问
 */
@Repository("aclEntryRepository")
public class AclEntryRepositoryImpl implements AclEntryRepository {

  private final TypedDataModel<String, AclEntryEntity> dataModel;
  private final AclEntryMapper mapper;

  public AclEntryRepositoryImpl(TypedDataModel<String, AclEntryEntity> dataModel) {
    this.dataModel = dataModel;
    this.mapper = new AclEntryMapper();
  }

  @Override
  public AclEntry save(AclEntry entry) {
    AclEntryEntity entity = mapper.toEntity(entry);

    if (entity.getId() == null || entity.getId().isEmpty()) {
      // ID 为空，直接插入
      var insertResult = dataModel.insert(entity);
      return insertResult.map(mapper::toModel)
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } else {
      // ID 不为空，先查询
      Optional<AclEntryEntity> existing = dataModel.get(entity.getId()).toJavaOptional();

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
  public AclEntry add(AclEntry entry) {
    AclEntryEntity entity = mapper.toEntity(entry);
    var insertResult = dataModel.insert(entity);
    return insertResult.map(mapper::toModel)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public AclEntry update(AclEntry entry) {
    AclEntryEntity entity = mapper.toEntity(entry);
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
  public List<AclEntry> saveAll(List<AclEntry> entries) {
    return entries.stream()
        .map(this::save)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<AclEntry> findById(String id) {
    return dataModel.get(id)
        .map(mapper::toModel)
        .toJavaOptional();
  }

  @Override
  public List<AclEntry> findByResource(String resourceId, ResourceType resourceType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "resourceId", resourceId,
        "resourceType", resourceType.name()
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public Optional<AclEntry> findByResourceAndPrincipal(
      String resourceId, ResourceType resourceType, String principal, String principalType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "resourceId", resourceId,
        "resourceType", resourceType.name(),
        "principal", principal,
        "principalType", principalType
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.isEmpty() ? null : mapper.toModel(list.get(0)))
        .toJavaOptional();
  }

  @Override
  public List<AclEntry> findByPrincipal(String principal, String principalType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "principal", principal,
        "principalType", principalType
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<AclEntry> findByPrincipalAndResourceType(
      String principal, String principalType, ResourceType resourceType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "principal", principal,
        "principalType", principalType,
        "resourceType", resourceType.name()
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<AclEntry> findByLibrary(String libraryId) {
    // 查询指定文档库的所有权限条目
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<AclEntry> findByLibraryAndPrincipal(String libraryId, String principal, String principalType) {
    // 查询用户在指定文档库的权限条目
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId,
        "principal", principal,
        "principalType", principalType
    ).toJavaMap();
    Map<String, Object> statement = HashMap.<String, Object>of("where", where).toJavaMap();

    return dataModel.query(statement)
        .map(list -> list.stream()
            .map(mapper::toModel)
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public boolean existsByResourceAndPrincipal(
      String resourceId, ResourceType resourceType, String principal, String principalType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "resourceId", resourceId,
        "resourceType", resourceType.name(),
        "principal", principal,
        "principalType", principalType
    ).toJavaMap();

    return dataModel.count(where).getOrElse(0L) > 0;
  }

  @Override
  public void deleteById(String id) {
    dataModel.delete(id);
  }

  @Override
  public void deleteByIds(List<String> ids) {
    dataModel.delete(ids);
  }

  @Override
  public void deleteByResource(String resourceId, ResourceType resourceType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "resourceId", resourceId,
        "resourceType", resourceType.name()
    ).toJavaMap();

    dataModel.delete(where);
  }

  @Override
  public void deleteByResourceAndPrincipal(
      String resourceId, ResourceType resourceType, String principal, String principalType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "resourceId", resourceId,
        "resourceType", resourceType.name(),
        "principal", principal,
        "principalType", principalType
    ).toJavaMap();

    dataModel.delete(where);
  }

  @Override
  public void deleteByPrincipal(String principal, String principalType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "principal", principal,
        "principalType", principalType
    ).toJavaMap();

    dataModel.delete(where);
  }

  @Override
  public void deleteByLibrary(String libraryId) {
    // 删除指定文档库的所有权限条目
    Map<String, Object> where = HashMap.<String, Object>of(
        "libraryId", libraryId
    ).toJavaMap();

    dataModel.delete(where);
  }

  @Override
  public long countByResource(String resourceId, ResourceType resourceType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "resourceId", resourceId,
        "resourceType", resourceType.name()
    ).toJavaMap();

    return dataModel.count(where).getOrElse(0L);
  }

  @Override
  public long countByPrincipal(String principal, String principalType) {
    Map<String, Object> where = HashMap.<String, Object>of(
        "principal", principal,
        "principalType", principalType
    ).toJavaMap();

    return dataModel.count(where).getOrElse(0L);
  }
}
