package com.ouroboros.security.rbac.service.impl;

import com.ouroboros.common.app.util.SpringContextUtil;

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
import com.ouroboros.security.rbac.*;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;
import com.ouroboros.security.rbac.service.UserRoleService;

/**
 * @author liansz
 */
@Service
@CacheConfig(cacheNames = "ouroboros:user:role")
public class UserRoleServiceImpl implements UserRoleService {


  @Autowired
  private UserRoleServiceImpl self;

  @Autowired
  private UserCenter userCenter;

  @Autowired
  private TypedDataModel<String, UserRoleMapEntity> dataModel;

  public UserRoleServiceImpl() {
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public UserRoleMapEntity add(UserRoleMapEntity model) throws OuroborosException {
    var result = getDataModel().insert(model);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("插入数据失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public List<UserRoleMapEntity> add(Collection<UserRoleMapEntity> models) throws OuroborosException {
    var result = getDataModel().batchInsert(models);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("批量插入数据失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(String id) throws OuroborosException {
    var result = getDataModel().delete(id);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("删除用户角色映射失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(Collection<String> ids) throws OuroborosException {
    var result = getDataModel().delete(ids);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("批量删除用户角色映射失败", cause));
  }

  @Override
  @Transactional
  public Boolean deleteByRoleId(String roleId) throws OuroborosException {
    var result = getDataModel().delete(new HashMap<String, Object>(Collections.<String, Object>singletonMap("role", roleId)));
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("按角色ID删除用户角色映射失败", cause));
  }

  @Override
  @Transactional
  public Boolean deleteByUserIdentity(String userIdentity) throws OuroborosException {
    var result = getDataModel().delete(new HashMap<String, Object>(Collections.<String, Object>singletonMap("user", userIdentity)));
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("按用户ID删除用户角色映射失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean deleteByRoleIdAndUserIdentities(String roleId, Collection<String> userIdentities) throws OuroborosException {
    var params = new HashMap<String, Object>();
    params.put("role", roleId);
    params.put("user", userIdentities);

    var result = getDataModel().delete(params);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("按角色ID和用户ID列表删除用户角色映射失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean deleteByUserIdentityAndRoleIds(String userIdentity, Collection<String> roleIds) throws OuroborosException {
    var params = new HashMap<String, Object>();
    params.put("user", userIdentity);
    params.put("role", roleIds);

    var result = getDataModel().delete(params);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("按用户ID和角色ID列表删除用户角色映射失败", cause));
  }

  @Override
  public Optional<UserRoleMapEntity> getByRoleIdAndUserIdentity(String roleId, String userIdentity) {
    return self.searchAll().stream()
        .filter(model -> Objects.equals(model.getRole(), roleId) && Objects.equals(model.getUser(), userIdentity))
        .findAny();
  }

  @Override
  public List<UserRoleMapEntity> searchListByRoleId(String roleId) {
    return self.searchAll().stream()
        .filter(model -> Objects.equals(model.getRole(), roleId))
        .collect(Collectors.toList());
  }

  @Override
  public List<UserRoleMapEntity> searchListByUserIdentity(String userIdentity) {
    return self.searchAll().stream()
        .filter(model -> Objects.equals(model.getUser(), userIdentity))
        .collect(Collectors.toList());
  }

  @Cacheable(sync = true)
  public List<UserRoleMapEntity> searchAll() {
    return getDataModel().query(new HashMap<String, Object>())
        .getOrElseThrow(cause -> OuroborosExceptionMapper.toOuroborosException(cause,
            error -> new InternalException("查询用户角色关联数据失败", error)));
  }

  /**
   * 获取数据模型
   *
   * @return
   * @throws OuroborosException
   */
  private TypedDataModel<String, UserRoleMapEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }

  public List<User> getUsersByRole(String roleId) {
    var mappings = searchListByRoleId(roleId);
    return userCenter.getUsers(mappings.stream().map(UserRoleMapEntity::getUser).collect(Collectors.toList()));
  }

  public List<Role> getRolesByUser(String userId) {
    var mappings = searchListByUserIdentity(userId);
    return RoleCenter.instance().getRoles(mappings.stream().map(UserRoleMapEntity::getRole).collect(Collectors.toList()));
  }

  public static class Proxy implements UserRoleProvider {

    @Override
    public List<User> getUsersByRole(String roleId) {
      return SpringContextUtil.getBean(UserRoleServiceImpl.class).getUsersByRole(roleId);
    }

    @Override
    public List<Role> getRolesByUser(String userId) {
      return SpringContextUtil.getBean(UserRoleServiceImpl.class).getRolesByUser(userId);
    }
  }
}
