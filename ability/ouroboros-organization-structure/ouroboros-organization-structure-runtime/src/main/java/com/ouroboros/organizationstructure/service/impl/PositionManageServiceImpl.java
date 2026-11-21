package com.ouroboros.organizationstructure.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.AppointmentAggregator;
import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.organizationstructure.entity.PositionEntity;
import com.ouroboros.organizationstructure.model.PositionModel;
import com.ouroboros.organizationstructure.service.PositionManageService;

/**
 * 岗位管理服务实现
 *
 * @author songmingxu
 */
@Service
@CacheConfig(cacheNames = "ouroboros:organization:positions")
public class PositionManageServiceImpl implements PositionManageService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private PositionManageServiceImpl self;

  @Autowired
  private TypedDataModel<String, PositionEntity> dataModel;

  @Autowired
  @Lazy
  private OrganizationAggregator organizationAggregator;

  @Autowired
  @Lazy
  private EmployeeAggregator employeeAggregator;

  @Autowired
  @Lazy
  private AppointmentAggregator appointmentAggregator;

  @Autowired
  @Lazy
  private PositionDefinitionCenter positionDefinitionCenter;

  @Override
  @CacheEvict(allEntries = true)
  public PositionEntity add(PositionEntity position) throws OuroborosException {
    PositionEntity saved = getDataModel().insert(position).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return saved;
  }

  @Override
  @CacheEvict(allEntries = true)
  public List<PositionEntity> addBatch(Collection<PositionEntity> positions) throws OuroborosException {
    List<PositionEntity> saved = getDataModel().batchInsert(new ArrayList<>(positions)).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return saved;
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean delete(String id) throws OuroborosException {
    Long r = getDataModel().delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return r > 0;
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean update(String id, PositionEntity position) throws OuroborosException {
    Long r = getDataModel().update(id, position).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return r > 0;
  }

  @Override
  public Optional<PositionEntity> findById(String id) {
    return self.doFindAll().stream()
        .filter(e -> Objects.equals(e.getId(), id))
        .findFirst();
  }

  @Override
  public List<PositionEntity> findByIds(Collection<String> ids) {
    return self.doFindAll().stream()
        .filter(e -> ids.contains(e.getId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<PositionEntity> findAll() {
    return self.doFindAll();
  }

  @Override
  public List<PositionEntity> findByDepartment(String departmentId) {
    return self.doFindAll().stream()
        .filter(e -> Objects.equals(e.getParent(), departmentId))
        .collect(Collectors.toList());
  }

  @Override
  public List<PositionEntity> findByDepartmentWithDescendants(String departmentId) {
    Set<String> deptIds = new HashSet<>();
    deptIds.add(departmentId);
    organizationAggregator.getDescendants(departmentId).forEach(node -> deptIds.add(node.getId()));
    
    return self.doFindAll().stream()
        .filter(e -> deptIds.contains(e.getParent()))
        .collect(Collectors.toList());
  }

  @Override
  public List<PositionEntity> findByPositionKeys(Collection<String> positionKeys) {
    if (positionKeys == null || positionKeys.isEmpty()) {
      return Collections.emptyList();
    }
    return self.doFindAll().stream()
        .filter(e -> positionKeys.contains(e.getPositionKey()))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<PositionEntity> findByDepartmentAndPositionKey(String departmentId, String positionKey) {
    return self.doFindAll().stream()
        .filter(e -> Objects.equals(e.getParent(), departmentId))
        .filter(e -> Objects.equals(e.getPositionKey(), positionKey))
        .findFirst();
  }

  @Override
  @CacheEvict(allEntries = true)
  public PositionEntity assignDefinitionToDepartment(String departmentId,
                                                     String positionKey,
                                                     String fallbackName,
                                                     Integer headcount,
                                                     String leaderEmployee) throws OuroborosException {
    if (findByDepartmentAndPositionKey(departmentId, positionKey).isPresent()) {
      throw new InternalException(String.format("部门 %s 已存在岗位定义 %s 的分配", departmentId, positionKey), null);
    }

    PositionEntity entity = new PositionEntity();
    entity.setParent(departmentId);
    entity.setPositionKey(positionKey);
    entity.setPositionName(resolveDefinition(positionKey)
        .map(com.ouroboros.organizationstructure.PositionDefinition::getName)
        .orElse(fallbackName));
    entity.setLevel(resolveDefinition(positionKey)
        .map(com.ouroboros.organizationstructure.PositionDefinition::getLevel)
        .orElse(null));
    entity.setDescription(resolveDefinition(positionKey)
        .map(com.ouroboros.organizationstructure.PositionDefinition::getDescription)
        .orElse(null));
    entity.setHeadcount(headcount);
    entity.setLeaderEmployee(leaderEmployee);
    return getDataModel().insert(entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public PositionEntity getOrCreateDefaultPosition(String departmentId) throws OuroborosException {
    Optional<PositionEntity> existing = self.doFindAll().stream()
        .filter(e -> Objects.equals(e.getParent(), departmentId))
        .filter(e -> Objects.equals(e.getPositionKey(), DEFAULT_POSITION_KEY)
            || (e.getPositionKey() == null && DEFAULT_POSITION_NAME.equals(e.getPositionName())))
        .findFirst();
    
    if (existing.isPresent()) {
      return existing.get();
    }
    
    return assignDefinitionToDepartment(departmentId, DEFAULT_POSITION_KEY, DEFAULT_POSITION_NAME, null, null);
  }

  @Cacheable(sync = true)
  public List<PositionEntity> doFindAll() {
    try {
      return getDataModel().query(new HashMap<>()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("查询岗位数据错误!", e);
      return Collections.emptyList();
    }
  }

  // 供 Provider 使用的转换方法
  public PositionModel toModel(PositionEntity entity) {
    String parentId = entity.getParent();
    String leaderId = entity.getLeaderEmployee();
    
    return new PositionModel(
        entity,
        parentId == null ? () -> null : () -> organizationAggregator.getById(parentId).orElse(null),
        leaderId == null ? () -> null : () -> employeeAggregator.getById(leaderId).orElse(null),
        () -> appointmentAggregator.getCurrentByPosition(entity.getId()).size(),
        () -> resolveDefinition(entity.getPositionKey()).orElse(null)
    );
  }

  private Optional<com.ouroboros.organizationstructure.PositionDefinition> resolveDefinition(String positionKey) {
    if (positionKey == null) {
      return Optional.empty();
    }
    return positionDefinitionCenter.getDefinition(positionKey);
  }

  private TypedDataModel<String, PositionEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
