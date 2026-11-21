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
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.enums.OrganizationNodeType;
import com.ouroboros.organizationstructure.service.StructureManageService;
import com.ouroboros.organizationstructure.service.OrganizationMutationPolicy;

/**
 * 结构性节点管理服务实现
 *
 * @author songmingxu
 */
@Service
@CacheConfig(cacheNames = "ouroboros:organization:structure")
public class StructureManageServiceImpl implements StructureManageService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private StructureManageServiceImpl self;

  @Autowired
  private TypedDataModel<String, OrganizationNodeEntity> dataModel;

  @Autowired(required = false)
  private List<OrganizationMutationPolicy> mutationPolicies = List.of();

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public OrganizationNodeEntity add(OrganizationNodeEntity node) throws OuroborosException {
    return getDataModel().insert(node).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public List<OrganizationNodeEntity> addBatch(Collection<OrganizationNodeEntity> nodes) throws OuroborosException {
    return getDataModel().batchInsert(new ArrayList<>(nodes)).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(String id) throws OuroborosException {
    mutationPolicies.forEach(policy -> policy.node(id, null, true));
    Long r = getDataModel().delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return r > 0;
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean update(String id, OrganizationNodeEntity node) throws OuroborosException {
    mutationPolicies.forEach(policy -> policy.node(id, node, false));
    Long r = getDataModel().update(id, node).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return r > 0;
  }

  @Override
  public Optional<OrganizationNodeEntity> findById(String id) {
    return self.doFindAll().stream()
        .filter(entity -> Objects.equals(entity.getId(), id))
        .findAny();
  }

  @Override
  public List<OrganizationNodeEntity> findByIds(Collection<String> ids) {
    return self.doFindAll().stream()
        .filter(entity -> ids.contains(entity.getId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<OrganizationNodeEntity> findAll() {
    return self.doFindAll();
  }

  @Override
  public List<OrganizationNodeEntity> findChildren(String parentId) {
    return self.doFindAll().stream()
        .filter(entity -> Objects.equals(entity.getParent(), parentId))
        .collect(Collectors.toList());
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public OrganizationNodeEntity addCompany(OrganizationNodeEntity company) throws OuroborosException {
    company.setType(OrganizationNodeType.COMPANY.getType());
    return add(company);
  }

  @Override
  public List<OrganizationNodeEntity> findAllCompanies() {
    return self.doFindAll().stream()
        .filter(entity -> OrganizationNodeType.COMPANY.getType().equals(entity.getType()))
        .collect(Collectors.toList());
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public OrganizationNodeEntity addDepartment(OrganizationNodeEntity department) throws OuroborosException {
    department.setType(OrganizationNodeType.DEPARTMENT.getType());
    return add(department);
  }

  @Override
  public List<OrganizationNodeEntity> findAllDepartments() {
    return self.doFindAll().stream()
        .filter(entity -> OrganizationNodeType.DEPARTMENT.getType().equals(entity.getType()))
        .collect(Collectors.toList());
  }

  @Override
  public List<OrganizationNodeEntity> findDepartmentsByCompany(String companyId) {
    return self.doFindAll().stream()
        .filter(entity -> OrganizationNodeType.DEPARTMENT.getType().equals(entity.getType()))
        .filter(entity -> Objects.equals(entity.getParent(), companyId))
        .collect(Collectors.toList());
  }

  @Cacheable(sync = true)
  public List<OrganizationNodeEntity> doFindAll() {
    try {
      return getDataModel().query(new HashMap<>()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("查询组织架构数据错误!", e);
      return Collections.emptyList();
    }
  }

  private TypedDataModel<String, OrganizationNodeEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
