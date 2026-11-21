package com.ouroboros.security.rbac.service.impl;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.util.CollectionCalculateUtils;

/**
 * @author liansz
 */
@Service
@Order(99)
@CacheConfig(cacheNames = "ouroboros:role:authority")
public class RoleAuthorityServiceImpl implements RoleAuthorityService {


  @Autowired
  private RoleAuthorityServiceImpl self;

  @Autowired
  private TypedDataModel<String, RoleAuthorityMapEntity> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public RoleAuthorityMapEntity add(RoleAuthorityMapEntity model) throws OuroborosException {
    var result = getDataModel().insert(model);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("插入角色权限映射失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public List<RoleAuthorityMapEntity> add(Collection<RoleAuthorityMapEntity> models) throws OuroborosException {
    var result = getDataModel().batchInsert(models);
    if (result.isSuccess()) {
      return result.get();
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("批量插入角色权限映射失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(String id) throws OuroborosException {
    var result = getDataModel().delete(id);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("删除角色权限映射失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(Collection<String> ids) throws OuroborosException {
    var result = getDataModel().delete(ids);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("批量删除角色权限映射失败", cause));
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean update(String id, RoleAuthorityMapEntity model) throws OuroborosException {
    var result = getDataModel().update(id, model);
    if (result.isSuccess()) {
      return result.get() > 0;
    }

    throw OuroborosExceptionMapper.toOuroborosException(result.getCause(), cause -> new InternalException("更新角色权限映射失败", cause));
  }

  @Override
  @Transactional
  @CacheEvict(allEntries = true)
  public void updateRoleAuthorities(String roleId, Collection<RoleAuthorityMapEntity> authorities) throws OuroborosException {
    var oldRoleAuthorities = getByRoleId(roleId);

    var addRoleAuthorities = CollectionCalculateUtils.findNewCollection(
        oldRoleAuthorities, authorities, (old, now) -> Objects.equals(old.getId(), now.getId())
    );
    var removeRoleAuthorities = CollectionCalculateUtils.findRemoveCollection(
        oldRoleAuthorities, authorities, (old, now) -> Objects.equals(old.getId(), now.getId())
    );
    var updateRoleAuthorities = CollectionCalculateUtils.findUpdateCollection(
        oldRoleAuthorities,
        authorities,
        (old, now) -> Objects.equals(old.getId(), now.getId()),
        (old, now) ->
            Objects.equals(old.getAuthority(), now.getAuthority())
                && Objects.equals(old.getAccessControlParams(), now.getAccessControlParams())
    );

    if (ObjectUtils.isNotEmpty(addRoleAuthorities)) {
      addRoleAuthorities.forEach(roleAuthority -> roleAuthority.setRole(roleId));
      add(addRoleAuthorities);
    }

    if (ObjectUtils.isNotEmpty(removeRoleAuthorities)) {
      var ids = removeRoleAuthorities.stream().map(RoleAuthorityMapEntity::getId).collect(Collectors.toList());
      var result = delete(ids);
      if (!result) {
        throw new PlatformException("更新角色权限失败!", null);
      }
    }

    for (RoleAuthorityMapEntity updateRoleAuthority : updateRoleAuthorities) {
      updateRoleAuthority.setRole(roleId);
      var result = update(updateRoleAuthority.getId(), updateRoleAuthority);
      if (!result) {
        throw new PlatformException("更新角色权限失败!", null);
      }
    }
  }

  @Override
  public List<RoleAuthorityMapEntity> getByRoleId(String roleId) {
    return self.searchAll().stream()
        .filter(model -> Objects.equals(model.getRole(), roleId))
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, List<RoleAuthorityMapEntity>> getByRoleIds(Collection<String> roleIds) {
    return self.searchAll().stream()
        .filter(model -> roleIds.contains(model.getRole()))
        .collect(
            Collectors.toMap(
                RoleAuthorityMapEntity::getRole,
                Collections::singletonList,
                (a, b) -> Stream.concat(a.stream(), b.stream()).collect(Collectors.toList())
            )
        );
  }

  @Cacheable(sync = true)
  public List<RoleAuthorityMapEntity> searchAll() {
    return getDataModel().query(new HashMap<String, Object>())
        .getOrElseThrow(cause -> OuroborosExceptionMapper.toOuroborosException(cause,
            error -> new InternalException("查询角色权限关联数据失败", error)));
  }

  /**
   * 获取数据模型
   *
   * @return
   */
  private TypedDataModel<String, RoleAuthorityMapEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
