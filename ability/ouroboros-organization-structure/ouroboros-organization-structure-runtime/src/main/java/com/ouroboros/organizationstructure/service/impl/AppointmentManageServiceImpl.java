package com.ouroboros.organizationstructure.service.impl;

import java.time.LocalDate;
import java.util.*;
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
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.organizationstructure.AppointmentType;
import com.ouroboros.organizationstructure.entity.AppointmentEntity;
import com.ouroboros.organizationstructure.service.AppointmentManageService;
import com.ouroboros.organizationstructure.service.OrganizationMutationPolicy;

/**
 * 任职管理服务实现
 *
 * @author songmingxu
 */
@Service
@CacheConfig(cacheNames = "ouroboros:organization:appointments")
public class AppointmentManageServiceImpl implements AppointmentManageService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private AppointmentManageServiceImpl self;

  @Autowired
  private TypedDataModel<String, AppointmentEntity> dataModel;

  @Autowired(required = false)
  private List<OrganizationMutationPolicy> mutationPolicies = List.of();

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public AppointmentEntity create(AppointmentEntity appointment) {
    mutationPolicies.forEach(policy -> policy.appointment(appointment, false));
    try {
      return getDataModel().insert(appointment).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("创建任职失败", e);
      throw e;
    }
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public AppointmentEntity create(String employeeId, String positionId, AppointmentType type, LocalDate startDate) {
    AppointmentEntity entity = new AppointmentEntity();
    entity.setEmployee(employeeId);
    entity.setPosition(positionId);
    entity.setType(type.name());
    entity.setStartDate(startDate);
    return create(entity);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public AppointmentEntity create(String employeeId, String positionId, AppointmentType type, LocalDate startDate, LocalDate endDate) {
    AppointmentEntity entity = new AppointmentEntity();
    entity.setEmployee(employeeId);
    entity.setPosition(positionId);
    entity.setType(type.name());
    entity.setStartDate(startDate);
    entity.setEndDate(endDate);
    return create(entity);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public List<AppointmentEntity> createBatch(Collection<AppointmentEntity> appointments) {
    appointments.forEach(appointment -> mutationPolicies.forEach(policy -> policy.appointment(appointment, false)));
    try {
      return getDataModel().batchInsert(new ArrayList<>(appointments)).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("批量创建任职失败", e);
      throw e;
    }
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean remove(String id) {
    self.findById(id).ifPresent(appointment -> mutationPolicies.forEach(policy -> policy.appointment(appointment, true)));
    try {
      Long r = getDataModel().delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      return r > 0;
    } catch (OuroborosException e) {
      logger.error("删除任职失败", e);
      return false;
    }
  }

  @Override
  public List<AppointmentEntity> findByEmployee(String employeeId) {
    return self.doFindAll().stream()
        .filter(entity -> Objects.equals(entity.getEmployee(), employeeId))
        .collect(Collectors.toList());
  }

  @Override
  public List<AppointmentEntity> findByPosition(String positionId) {
    return self.doFindAll().stream()
        .filter(entity -> Objects.equals(entity.getPosition(), positionId))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<AppointmentEntity> findPrimaryAppointment(String employeeId) {
    return self.doFindAll().stream()
        .filter(entity -> Objects.equals(entity.getEmployee(), employeeId))
        .filter(entity -> AppointmentType.PRIMARY.name().equals(entity.getType()))
        .filter(this::isCurrent)
        .findFirst();
  }

  @Override
  public List<AppointmentEntity> findCurrentAppointments(String employeeId) {
    return self.doFindAll().stream()
        .filter(entity -> Objects.equals(entity.getEmployee(), employeeId))
        .filter(this::isCurrent)
        .collect(Collectors.toList());
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean endAppointment(String id) {
    self.findById(id).ifPresent(appointment -> mutationPolicies.forEach(policy -> policy.appointment(appointment, true)));
    try {
      AppointmentEntity entity = new AppointmentEntity();
      entity.setEndDate(LocalDate.now());
      Long r = getDataModel().update(id, entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      return r > 0;
    } catch (OuroborosException e) {
      logger.error("结束任职失败", e);
      return false;
    }
  }

  @Cacheable(sync = true)
  public List<AppointmentEntity> doFindAll() {
    try {
      return getDataModel().query(new HashMap<>()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("查询任职数据错误!", e);
      return Collections.emptyList();
    }
  }

  private boolean isCurrent(AppointmentEntity entity) {
    LocalDate now = LocalDate.now();
    LocalDate startDate = entity.getStartDate();
    LocalDate endDate = entity.getEndDate();
    
    boolean afterStart = startDate == null || !now.isBefore(startDate);
    boolean beforeEnd = endDate == null || !now.isAfter(endDate);
    
    return afterStart && beforeEnd;
  }

  @Override
  @Cacheable(key = "'id:' + #id")
  public Optional<AppointmentEntity> findById(String id) {
    try {
      return getDataModel().get(id)
        .toJavaOptional();
    } catch (OuroborosException e) {
      logger.error("根据ID查询任职失败: {}", id, e);
      return Optional.empty();
    }
  }

  @Override
  @Cacheable(key = "'all'")
  public List<AppointmentEntity> findAll() {
    try {
      return getDataModel().query(new HashMap<>()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("查询所有任职失败", e);
      return Collections.emptyList();
    }
  }

  private TypedDataModel<String, AppointmentEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
