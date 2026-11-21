package com.ouroboros.security.rbac.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;

/**
 * @author liansz
 */
public interface RoleAuthorityService {

  /**
   * 添加
   *
   * @param model
   * @return
   * @throws OuroborosException
   */
  RoleAuthorityMapEntity add(RoleAuthorityMapEntity model) throws OuroborosException;

  /**
   * 添加多个
   *
   * @param models
   * @return
   * @throws OuroborosException
   */
  List<RoleAuthorityMapEntity> add(Collection<RoleAuthorityMapEntity> models) throws OuroborosException;

  /**
   * 删除
   *
   * @param id
   * @return
   * @throws OuroborosException
   */
  Boolean delete(String id) throws OuroborosException;

  /**
   * 删除
   *
   * @param ids
   * @return
   * @throws OuroborosException
   */
  Boolean delete(Collection<String> ids) throws OuroborosException;

  /**
   * 更新
   *
   * @param model
   * @return
   * @throws OuroborosException
   */
  Boolean update(String id, RoleAuthorityMapEntity model) throws OuroborosException;

  /**
   * 更新角色权限
   *
   * @param roleId
   * @param authorities
   * @return
   * @throws OuroborosException
   */
  void updateRoleAuthorities(String roleId, Collection<RoleAuthorityMapEntity> authorities) throws OuroborosException;

  /**
   * 根据角色id查询角色权限
   *
   * @param roleId
   * @return
   */
  List<RoleAuthorityMapEntity> getByRoleId(String roleId);

  /**
   * 根据角色id列表查询角色权限
   *
   * @param roleIds
   * @return
   */
  Map<String, List<RoleAuthorityMapEntity>> getByRoleIds(Collection<String> roleIds);
}
