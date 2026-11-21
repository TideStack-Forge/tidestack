package com.ouroboros.organizationstructure.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.organizationstructure.*;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.service.EmployeeManageService;
import com.ouroboros.organizationstructure.service.OrganizationMutationPolicy;

/**
 * 员工服务实现 - 同时实现EmployeeManageService（存储层）和EmployeeProvider（业务层）
 *
 * @author liansz
 */
@Service
@CacheConfig(cacheNames = "ouroboros:organization:employees")
public class EmployeeServiceImpl implements EmployeeManageService, EmployeeProvider {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private EmployeeServiceImpl self;

  @Autowired
  private TypedDataModel<String, EmployeeEntity> dataModel;

  @Autowired
  private OrganizationModelResolver modelResolver;

  @Autowired(required = false)
  private List<OrganizationMutationPolicy> mutationPolicies = List.of();

  // ==================== EmployeeManageService 实现 ====================

  @Override
  @CacheEvict(allEntries = true)
  public EmployeeEntity insert(EmployeeEntity entity) throws OuroborosException {
    return getDataModel().insert(entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public List<EmployeeEntity> insertBatch(Collection<EmployeeEntity> entities) throws OuroborosException {
    return getDataModel().batchInsert(new ArrayList<>(entities)).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean deleteById(String id) throws OuroborosException {
    mutationPolicies.forEach(policy -> policy.employee(id, null, true));
    Long r = getDataModel().delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return r > 0;
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean updateById(String id, EmployeeEntity entity) throws OuroborosException {
    mutationPolicies.forEach(policy -> policy.employee(id, entity, false));
    Long r = getDataModel().update(id, entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return r > 0;
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean updateDepartmentByIds(Collection<String> ids, String departmentId) throws OuroborosException {
    Long r = getDataModel().update(
        Collections.singletonMap("id", new ArrayList<>(ids)),
        Collections.singletonMap("department", departmentId)
    ).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return r > 0;
  }

  @Override
  public Optional<EmployeeEntity> findById(String id) {
    return self.doFindAll().stream()
        .filter(entity -> Objects.equals(entity.getId(), id))
        .findAny();
  }

  @Override
  public List<EmployeeEntity> findByIds(Collection<String> ids) {
    return self.doFindAll().stream()
        .filter(entity -> ids.contains(entity.getId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<EmployeeEntity> findAll() {
    return self.doFindAll();
  }

  @Cacheable(sync = true)
  public List<EmployeeEntity> doFindAll() {
    try {
      return getDataModel().query(new HashMap<>()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("查询员工数据错误!", e);
      return Collections.emptyList();
    }
  }

  // ==================== EmployeeProvider 实现 ====================

  @Override
  public Optional<? extends Employee> getById(String id) {
    return self.findById(id).map(modelResolver::toEmployeeModel);
  }

  @Override
  public List<? extends Employee> getAll() {
    return self.findAll().stream().map(modelResolver::toEmployeeModel).collect(Collectors.toList());
  }

  @Override
  public List<? extends Employee> getByIds(Collection<String> ids) {
    return self.findByIds(ids).stream().map(modelResolver::toEmployeeModel).collect(Collectors.toList());
  }

  @Override
  public List<? extends Employee> getByPosition(String id) {
    return getByPositions(Collections.singletonList(id));
  }

  @Override
  public List<? extends Employee> getByPositions(Collection<String> ids) {
    var allEmployees = getAll();
    return allEmployees.stream()
        .filter(employee -> employee.getCurrentAppointments()
            .stream()
            .map(Appointment::getPosition)
            .filter(Objects::nonNull)
            .anyMatch(ids::contains)
        )
        .collect(Collectors.toList());
  }

  @Override
  public List<? extends Employee> getByDepartment(String id) {
    return getByDepartments(Collections.singletonList(id));
  }

  @Override
  public List<? extends Employee> getByDepartments(Collection<String> ids) {
    var allEmployees = getAll();
    var matchingEmployees = allEmployees.stream()
        .filter(employee -> employee.getCurrentAppointments()
            .stream()
            .map(Appointment::getPositionDetail)
            .filter(Objects::nonNull)
            .map(Position::getParentDetail)
            .filter(Objects::nonNull)
            .filter(Department.class::isInstance)
            .map(OrganizationNode::getId)
            .anyMatch(ids::contains)
        )
        .collect(Collectors.toList());
    return matchingEmployees;
  }

  @Override
  public List<? extends Employee> getByCompany(String id) {
    return getByCompanies(Collections.singletonList(id));
  }

  @Override
  public List<? extends Employee> getByCompanies(Collection<String> ids) {
    return getAll().stream()
        .filter(employee -> employee.getCurrentAppointments()
            .stream()
            .map(Appointment::getPositionDetail)
            .filter(Objects::nonNull)
            .map(Position::getParentDetail)
            .filter(Objects::nonNull)
            .map(node -> node instanceof Company company
                ? company.getId()
                : node instanceof Department dept
                ? Optional.ofNullable(dept.getCompanyDetail()).map(Company::getId).orElse(null)
                : null)
            .filter(Objects::nonNull)
            .anyMatch(ids::contains)
        )
        .collect(Collectors.toList());
  }

  // ==================== 私有方法 ====================

  /**
   * 获取数据模型
   */
  private TypedDataModel<String, EmployeeEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
