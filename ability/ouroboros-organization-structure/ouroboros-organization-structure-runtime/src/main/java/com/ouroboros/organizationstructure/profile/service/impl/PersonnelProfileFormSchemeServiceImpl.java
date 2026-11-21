package com.ouroboros.organizationstructure.profile.service.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;
import com.ouroboros.organizationstructure.profile.service.PersonnelProfileFormSchemeService;

@Service
@CacheConfig(cacheNames = "ouroboros:organization:personnel-profile-form-scheme")
public class PersonnelProfileFormSchemeServiceImpl implements PersonnelProfileFormSchemeService {

  @Autowired
  private PersonnelProfileFormSchemeServiceImpl self;

  @Autowired
  private TypedDataModel<String, PersonnelProfileFormSchemeEntity> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  public PersonnelProfileFormSchemeEntity create(PersonnelProfileFormSchemeEntity entity, String operatedBy) throws OuroborosException {
    validateForCreate(entity);
    LocalDateTime now = LocalDateTime.now();
    entity.setCreatedAt(now);
    entity.setCreatedBy(operatedBy);
    entity.setUpdatedAt(now);
    entity.setUpdatedBy(operatedBy);
    return getDataModel().insert(entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean update(String id, PersonnelProfileFormSchemeEntity entity, String operatedBy) throws OuroborosException {
    PersonnelProfileFormSchemeEntity existing = getById(id).orElseThrow(() -> new BusinessException(404, "人员档案表单方案不存在"));
    validateForUpdate(id, existing, entity);
    entity.setId(id);
    entity.setCreatedAt(existing.getCreatedAt());
    entity.setCreatedBy(existing.getCreatedBy());
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setUpdatedBy(operatedBy);
    Long updated = getDataModel().update(id, entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return updated > 0;
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean delete(String id, String operatedBy) throws OuroborosException {
    Long deleted = getDataModel().delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return deleted > 0;
  }

  @Override
  public Optional<PersonnelProfileFormSchemeEntity> getById(String id) throws OuroborosException {
    return self.doFindAll().stream().filter(entity -> Objects.equals(entity.getId(), id)).findFirst();
  }

  @Override
  public Optional<PersonnelProfileFormSchemeEntity> getById(String id, String appName) throws OuroborosException {
    return searchAll(appName).stream().filter(entity -> Objects.equals(entity.getId(), id)).findFirst();
  }

  @Override
  public List<PersonnelProfileFormSchemeEntity> searchAll(String appName) throws OuroborosException {
    return self.doFindAll().stream().filter(entity -> matchesAppName(entity.getAppName(), appName)).collect(Collectors.toList());
  }

  @Override
  public Optional<PersonnelProfileFormSchemeEntity> findDefaultScheme(String appName) throws OuroborosException {
    Comparator<PersonnelProfileFormSchemeEntity> comparator = Comparator
        .comparing((PersonnelProfileFormSchemeEntity entity) -> entity.getPriority() == null ? Integer.MIN_VALUE : entity.getPriority())
        .reversed()
        .thenComparing((PersonnelProfileFormSchemeEntity entity) -> entity.getUpdatedAt() == null ? LocalDateTime.MIN : entity.getUpdatedAt(), Comparator.reverseOrder())
        .thenComparing((PersonnelProfileFormSchemeEntity entity) -> entity.getId() == null ? "" : entity.getId(), String.CASE_INSENSITIVE_ORDER);
    return self.doFindAll().stream()
        .filter(entity -> Boolean.TRUE.equals(entity.getEnabled()))
        .filter(entity -> Boolean.TRUE.equals(entity.getIsDefault()))
        .filter(entity -> matchesAppName(entity.getAppName(), appName))
        .sorted(comparator)
        .findFirst();
  }

  @Cacheable(sync = true)
  public List<PersonnelProfileFormSchemeEntity> doFindAll() throws OuroborosException {
    return getDataModel().query(new HashMap<String, Object>()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  private void validateForCreate(PersonnelProfileFormSchemeEntity entity) throws OuroborosException {
    if (entity == null) {
      throw new BusinessException(400, "人员档案表单方案不能为空");
    }
    validateShape(entity);
    ensureDefaultUniqueness(null, entity);
  }

  private void validateForUpdate(String id, PersonnelProfileFormSchemeEntity existing, PersonnelProfileFormSchemeEntity entity) throws OuroborosException {
    if (entity == null) {
      throw new BusinessException(400, "人员档案表单方案不能为空");
    }
    if (entity.getId() != null && !Objects.equals(id, entity.getId())) {
      throw new BusinessException(400, "人员档案表单方案 ID 不可修改");
    }
    if (!Objects.equals(normalizeAppName(existing.getAppName()), normalizeAppName(entity.getAppName()))) {
      throw new BusinessException(400, "人员档案表单方案所属应用不可修改");
    }
    validateShape(entity);
    ensureDefaultUniqueness(id, entity);
  }

  private void validateShape(PersonnelProfileFormSchemeEntity entity) {
    if (StringUtils.isBlank(entity.getName())) {
      throw new BusinessException(400, "人员档案表单方案名称不能为空");
    }
    if (entity.getEditSchema().isEmpty()) {
      throw new BusinessException(400, "编辑表单 schema 不能为空");
    }
    if (entity.getDetailSchema().isEmpty()) {
      throw new BusinessException(400, "详情表单 schema 不能为空");
    }
    if (entity.getCreateSchema().isEmpty()) {
      throw new BusinessException(400, "新增表单 schema 不能为空");
    }
  }

  private void ensureDefaultUniqueness(String currentId, PersonnelProfileFormSchemeEntity entity) throws OuroborosException {
    if (!Boolean.TRUE.equals(entity.getIsDefault())) {
      return;
    }
    String appName = normalizeAppName(entity.getAppName());
    Optional<PersonnelProfileFormSchemeEntity> conflict = self.doFindAll().stream()
        .filter(item -> !Objects.equals(item.getId(), currentId))
        .filter(item -> Boolean.TRUE.equals(item.getIsDefault()))
        .filter(item -> matchesAppName(item.getAppName(), appName))
        .findFirst();
    if (conflict.isPresent()) {
      throw new BusinessException(400, "同一应用下只能存在一个默认人员档案表单方案");
    }
  }

  private boolean matchesAppName(String itemAppName, String requestedAppName) {
    return Objects.equals(normalizeAppName(itemAppName), normalizeAppName(requestedAppName));
  }

  private String normalizeAppName(String appName) {
    return StringUtils.trimToNull(appName);
  }

  private TypedDataModel<String, PersonnelProfileFormSchemeEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
