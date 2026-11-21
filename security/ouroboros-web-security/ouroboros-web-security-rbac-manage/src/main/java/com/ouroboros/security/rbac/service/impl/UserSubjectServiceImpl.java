package com.ouroboros.security.rbac.service.impl;

import java.time.LocalDateTime;
import java.util.*;

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
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.entity.UserSubjectEntity;
import com.ouroboros.security.rbac.service.UserSubjectService;

/**
 * 用户-主体绑定关系服务实现
 * 
 * @author songmingxu
 */
@Service
@CacheConfig(cacheNames = "ouroboros:user-subjects")
public class UserSubjectServiceImpl implements UserSubjectService {

  @Autowired
  private TypedDataModel<String, UserSubjectEntity> dataModel;

  private TypedDataModel<String, UserSubjectEntity> getDataModel() {
    return dataModel;
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public UserSubjectEntity create(UserSubjectEntity entity) throws OuroborosException {
    // 检查 userIdentity + subjectType 唯一性
    Optional<UserSubjectEntity> existing = findByUserAndSubjectType(entity.getUserIdentity(), entity.getSubjectType());
    if (existing.isPresent()) {
      throw new BusinessException("用户在该主体类型下已存在绑定关系");
    }
    
    return getDataModel().insert(entity)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public UserSubjectEntity create(String userIdentity, String subjectType, String subjectId) throws OuroborosException {
    UserSubjectEntity entity = new UserSubjectEntity();
    entity.setUserIdentity(userIdentity);
    entity.setSubjectType(subjectType);
    entity.setSubjectId(subjectId);
    entity.setCreatedBy(getCurrentUser());
    entity.setCreatedAt(LocalDateTime.now());
    
    return create(entity);
  }

  @Override
  @Cacheable(key = "#p0")
  public Optional<UserSubjectEntity> findByUser(String userIdentity) {
    Map<String, Object> statement = new TreeMap<>(String::compareToIgnoreCase);
    Map<String, Object> where = new TreeMap<>(String::compareToIgnoreCase);
    statement.put("WHERE", where);
    where.put("userIdentity", userIdentity);
    return getDataModel().query(statement)
        .map(results -> results.isEmpty() ? null : results.get(0))
        .toJavaOptional();
  }

  @Override
  @Cacheable(key = "#p0 + ':' + #p1")
  public Optional<UserSubjectEntity> findByUserAndSubjectType(String userIdentity, String subjectType) {
    Map<String, Object> statement =new TreeMap<>(String::compareToIgnoreCase);
    Map<String, Object> where = new TreeMap<>(String::compareToIgnoreCase);
    where.put("userIdentity", userIdentity);
    where.put("subjectType", subjectType);
    statement.put("WHERE", where);
    
    List<UserSubjectEntity> results = getDataModel().query(statement)
        .getOrElse(Collections.emptyList());
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  @Override
  @Cacheable(key = "'all:' + #p0")
  public List<UserSubjectEntity> findAllByUser(String userIdentity) {
    Map<String, Object> statement = new TreeMap<>(String::compareToIgnoreCase);
    Map<String, Object> where = new TreeMap<>(String::compareToIgnoreCase);
    where.put("userIdentity", userIdentity);
    statement.put("WHERE", where);
    
    return getDataModel().query(statement)
        .getOrElse(Collections.emptyList());
  }

  @Override
  @Cacheable(key = "#p0 + ':' + #p1")
  public Optional<UserSubjectEntity> findBySubject(String subjectType, String subjectId) {
    Map<String, Object> statement = new TreeMap<>(String::compareToIgnoreCase);
    Map<String, Object> where = new TreeMap<>(String::compareToIgnoreCase);
    where.put("subjectType", subjectType);
    where.put("subjectId", subjectId);
    statement.put("WHERE", where);
    
    List<UserSubjectEntity> results = getDataModel().query(statement)
        .getOrElse(java.util.Collections.emptyList());
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean deleteByUser(String userIdentity) throws OuroborosException {
    return getDataModel().delete(userIdentity)
        .map(r -> r > 0)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean deleteByUserAndSubjectType(String userIdentity, String subjectType) throws OuroborosException {
    Map<String, Object> where = new TreeMap<>(String::compareToIgnoreCase);
    where.put("userIdentity", userIdentity);
    where.put("subjectType", subjectType);
    
    return getDataModel().delete(where)
        .map(r -> r > 0)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @Cacheable(key = "'all'")
  public List<UserSubjectEntity> findAll() {
    return getDataModel().query(Collections.emptyMap())
        .getOrElse(Collections.emptyList());
  }

  /**
   * 获取当前操作用户
   */
  private String getCurrentUser() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(identity -> identity.getUserIdentity())
        .orElse(null);
  }
}
