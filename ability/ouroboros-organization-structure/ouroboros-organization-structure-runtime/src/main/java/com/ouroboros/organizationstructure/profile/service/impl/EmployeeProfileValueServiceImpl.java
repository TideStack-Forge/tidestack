package com.ouroboros.organizationstructure.profile.service.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.organizationstructure.profile.entity.EmployeeProfileValueEntity;
import com.ouroboros.organizationstructure.profile.service.EmployeeProfileValueService;

/**
 * 基于 typed data model 的员工档案扩展值存取实现。
 */
@Service
@CacheConfig(cacheNames = "ouroboros:organization:employee-profile-values")
public class EmployeeProfileValueServiceImpl implements EmployeeProfileValueService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private EmployeeProfileValueServiceImpl self;

  @Autowired
  private TypedDataModel<String, EmployeeProfileValueEntity> dataModel;

  @Override
  public Optional<EmployeeProfileValueEntity> findByEmployeeId(String employeeId) {
    return self.doFindAll().stream()
        .filter(entity -> Objects.equals(entity.getEmployeeId(), employeeId))
        .findFirst();
  }

  @Override
  public Map<String, Object> getProfileValuesByEmployeeId(String employeeId) {
    return findByEmployeeId(employeeId)
        .map(EmployeeProfileValueEntity::getProfileValues)
        .map(LinkedHashMap::new)
        .orElseGet(() -> new LinkedHashMap<String, Object>());
  }

  @Override
  @CacheEvict(allEntries = true)
  public void saveProfileValuesByEmployeeId(String employeeId, Map<String, Object> profileValues) throws OuroborosException {
    if (employeeId == null || employeeId.trim().isEmpty()) {
      throw new InternalException("员工ID不能为空", null);
    }
    if (profileValues == null || profileValues.isEmpty()) {
      self.deleteByEmployeeId(employeeId);
      return;
    }

    EmployeeProfileValueEntity entity = new EmployeeProfileValueEntity();
    entity.setEmployeeId(employeeId);
    entity.setProfileValues(new LinkedHashMap<String, Object>(profileValues));

    if (findByEmployeeId(employeeId).isPresent()) {
      getDataModel().update(employeeId, entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } else {
      getDataModel().insert(entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    }
  }

  @Override
  @CacheEvict(allEntries = true)
  public void deleteByEmployeeId(String employeeId) throws OuroborosException {
    if (employeeId == null || employeeId.trim().isEmpty()) {
      return;
    }
    findByEmployeeId(employeeId)
        .ifPresent(entity -> getDataModel().delete(entity.getEmployeeId()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException));
  }

  @Cacheable(sync = true)
  public List<EmployeeProfileValueEntity> doFindAll() {
    try {
      return getDataModel().query(new HashMap<String, Object>()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("查询员工档案扩展值失败", e);
      throw new InternalException("查询员工档案扩展值失败", e);
    }
  }

  private TypedDataModel<String, EmployeeProfileValueEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
