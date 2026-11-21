package com.ouroboros.security.rbac.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.security.rbac.entity.RoleEntity;
import com.ouroboros.security.rbac.service.RoleService;

/**
 * @author liansz
 */
@Service
@CacheConfig(cacheNames = "ouroboros:roles")
public class RoleServiceImpl implements RoleService {


  @Autowired
  private RoleServiceImpl self;

  @Autowired
  private TypedDataModel<String, RoleEntity> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public RoleEntity add(RoleEntity model) throws OuroborosException {
    var result = getDataModel().insert(model);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("插入角色失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public List<RoleEntity> add(Collection<RoleEntity> models) throws OuroborosException {
    var result = getDataModel().batchInsert(models);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("批量插入角色失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(String id) throws OuroborosException {
    var result = getDataModel().delete(id);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("删除角色失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean update(String id, RoleEntity model) throws OuroborosException {
    var result = getDataModel().update(id, model);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("更新角色失败", cause));
  }

  @Override
  public Optional<RoleEntity> getById(String id) {
    return searchAll().stream().filter(model -> Objects.equals(model.getId(), id)).findAny();
  }

  @Override
  public List<RoleEntity> searchAll() {
    return self.doSearchAll().stream().map(RoleEntity::new).collect(Collectors.toList());
  }

  @Cacheable(sync = true)
  public List<RoleEntity> doSearchAll() {
    return getDataModel().query(new HashMap<String, Object>())
        .getOrElseThrow(cause -> OuroborosExceptionMapper.toOuroborosException(cause,
            error -> new InternalException("查询角色数据失败", error)));
  }

  /**
   * 获取数据模型
   *
   * @return
   * @throws OuroborosException
   */
  private TypedDataModel<String, RoleEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
