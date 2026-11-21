package com.ouroboros.organizationstructure.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.organizationstructure.entity.AdminLevelEntity;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.enums.OrganizationNodeType;
import com.ouroboros.organizationstructure.service.AdminLevelService;
import com.ouroboros.organizationstructure.service.StructureManageService;

@Service
@CacheConfig(cacheNames = "ouroboros:admin:level")
public class AdminLevelServiceImpl implements AdminLevelService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private StructureManageService structureManageService;

  @Autowired
  private TypedDataModel<String, AdminLevelEntity> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public AdminLevelEntity create(AdminLevelEntity entity) throws OuroborosException {
    validateForCreate(entity);
    return getDataModel().insert(entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean update(String id, AdminLevelEntity entity) throws OuroborosException {
    AdminLevelEntity existing = findById(id).orElseThrow(() -> new BusinessException(404, "行政级别不存在"));
    validateForUpdate(existing, entity);
    Long r = getDataModel().update(id, entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return r > 0;
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(String id) throws OuroborosException {
    AdminLevelEntity existing = findById(id).orElseThrow(() -> new BusinessException(404, "行政级别不存在"));
    if (isReferencedByOrgNodes(existing.getCode())) {
      throw new BusinessException(409, "行政级别已被组织节点引用，无法删除");
    }
    Long count = getDataModel().delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return count > 0;
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean disable(String id) throws OuroborosException {
    AdminLevelEntity existing = findById(id).orElseThrow(() -> new BusinessException(404, "行政级别不存在"));
    existing.setEnabled(false);
    getDataModel().update(id, existing).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return true;
  }

  @Override
  public Optional<AdminLevelEntity> findById(String id) throws OuroborosException {
    return findAll().stream().filter(entity -> Objects.equals(entity.getId(), id)).findAny();
  }

  @Override
  public Optional<AdminLevelEntity> findByCode(String code) throws OuroborosException {
    if (code == null || code.trim().isEmpty()) {
      return Optional.empty();
    }
    String normalized = code.trim();
    return findAll().stream().filter(entity -> Objects.equals(entity.getCode(), normalized)).findAny();
  }

  @Override
  @Cacheable(sync = true)
  public List<AdminLevelEntity> findAll() throws OuroborosException {
    return getDataModel().query(new HashMap<>()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public List<AdminLevelEntity> findByScope(String scope) throws OuroborosException {
    if (scope == null || scope.trim().isEmpty()) {
      return findAll();
    }
    String normalized = scope.trim();
    return findAll().stream().filter(entity -> normalized.equals(entity.getScope())).collect(Collectors.toList());
  }

  @Override
  public List<AdminLevelEntity> findEnabledByScope(String scope) throws OuroborosException {
    return findByScope(scope).stream().filter(entity -> Boolean.TRUE.equals(entity.getEnabled())).collect(Collectors.toList());
  }

  @Override
  public boolean isReferencedByOrgNodes(String code) throws OuroborosException {
    return structureManageService.findAll().stream().anyMatch(node -> Objects.equals(node.getAdminLevel(), code));
  }

  @Override
  public void validateAdminLevelReference(String code, String nodeType) throws OuroborosException {
    if (code == null || code.trim().isEmpty()) {
      throw new BusinessException(400, "行政级别代码不能为空");
    }
    AdminLevelEntity level = findByCode(code).orElseThrow(() -> new BusinessException(400, "行政级别 " + code + " 不存在"));
    String expectedScope = mapNodeTypeToScope(nodeType);
    if (!expectedScope.equals(level.getScope())) {
      throw new BusinessException(400, nodeTypeName(nodeType) + "节点不能引用" + scopeName(level.getScope()) + "行政级别");
    }
    if (!Boolean.TRUE.equals(level.getEnabled())) {
      throw new BusinessException(400, "行政级别 " + level.getName() + " 已禁用，不能被新引用");
    }
  }

  @Override
  public List<String> verifyExistingOrganizationNodeAdminLevels() throws OuroborosException {
    List<String> warnings = new ArrayList<>();
    for (OrganizationNodeEntity node : structureManageService.findAll()) {
      String code = node.getAdminLevel();
      if (code == null || code.trim().isEmpty()) {
        continue;
      }
      Optional<AdminLevelEntity> adminLevel = findByCode(code);
      if (!adminLevel.isPresent()) {
        warnings.add(String.format("组织节点 %s 引用了不存在的行政级别 %s", node.getId(), code));
        continue;
      }
      if (!Boolean.TRUE.equals(adminLevel.get().getEnabled())) {
        warnings.add(String.format("组织节点 %s 引用了已禁用的行政级别 %s", node.getId(), code));
      }
    }
    if (!warnings.isEmpty()) {
      logger.warn("行政级别引用校验发现 {} 个问题", warnings.size());
    }
    return warnings;
  }

  private void validateForCreate(AdminLevelEntity entity) throws OuroborosException {
    if (entity == null) {
      throw new BusinessException(400, "行政级别不能为空");
    }
    if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
      throw new BusinessException(400, "行政级别代码不能为空");
    }
    if (entity.getName() == null || entity.getName().trim().isEmpty()) {
      throw new BusinessException(400, "行政级别名称不能为空");
    }
    if (entity.getScope() == null || entity.getScope().trim().isEmpty()) {
      throw new BusinessException(400, "行政级别范围不能为空");
    }
    String normalizedScope = entity.getScope().trim();
    if (!"ORG".equals(normalizedScope) && !"DEPT".equals(normalizedScope)) {
      throw new BusinessException(400, "行政级别范围仅支持 ORG 或 DEPT");
    }
    if (entity.getRank() == null) {
      throw new BusinessException(400, "行政级别排序不能为空");
    }
    if (findByCode(entity.getCode()).isPresent()) {
      throw new BusinessException(400, "行政级别代码 " + entity.getCode() + " 已存在");
    }
    findByScope(normalizedScope).stream()
        .filter(existing -> Objects.equals(existing.getRank(), entity.getRank()))
        .findAny()
        .ifPresent(existing -> {
          throw new BusinessException(400, "同范围内排序 " + entity.getRank() + " 已被 " + existing.getName() + " 占用");
        });
  }

  private void validateForUpdate(AdminLevelEntity existing, AdminLevelEntity updated) throws OuroborosException {
    if (updated == null) {
      throw new BusinessException(400, "行政级别不能为空");
    }
    if (!Objects.equals(existing.getCode(), updated.getCode())) {
      throw new BusinessException(400, "行政级别代码不可修改");
    }
    if (updated.getName() == null || updated.getName().trim().isEmpty()) {
      throw new BusinessException(400, "行政级别名称不能为空");
    }
    if (updated.getScope() != null && !Objects.equals(existing.getScope(), updated.getScope().trim())) {
      throw new BusinessException(400, "行政级别范围不可修改");
    }
    if (updated.getRank() == null) {
      throw new BusinessException(400, "行政级别排序不能为空");
    }
    if (!Objects.equals(existing.getRank(), updated.getRank())) {
      findByScope(existing.getScope()).stream()
          .filter(other -> !Objects.equals(other.getId(), existing.getId()))
          .filter(other -> Objects.equals(other.getRank(), updated.getRank()))
          .findAny()
          .ifPresent(conflict -> {
            throw new BusinessException(400, "同范围内排序 " + updated.getRank() + " 已被 " + conflict.getName() + " 占用");
          });
    }
  }

  private String mapNodeTypeToScope(String nodeType) {
    if (OrganizationNodeType.COMPANY.getType().equals(nodeType)) {
      return "ORG";
    }
    if (OrganizationNodeType.DEPARTMENT.getType().equals(nodeType)) {
      return "DEPT";
    }
    throw new BusinessException(400, "不支持的节点类型: " + nodeType);
  }

  private String nodeTypeName(String nodeType) {
    if (OrganizationNodeType.COMPANY.getType().equals(nodeType)) {
      return "组织";
    }
    return "部门";
  }

  private String scopeName(String scope) {
    if ("ORG".equals(scope)) {
      return "组织级";
    }
    return "部门级";
  }

  private TypedDataModel<String, AdminLevelEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
