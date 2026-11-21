package com.ouroboros.security.rbac.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;

/**
 * @author liansz
 */
public interface UserRoleService {

  /**
   * 添加
   *
   * @param model
   * @return
   * @throws OuroborosException
   */
  UserRoleMapEntity add(UserRoleMapEntity model) throws OuroborosException;

  /**
   * 添加多个
   *
   * @param models
   * @return
   * @throws OuroborosException
   */
  List<UserRoleMapEntity> add(Collection<UserRoleMapEntity> models) throws OuroborosException;

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
   * 按roleId删除
   *
   * @param roleId
   * @return
   * @throws OuroborosException
   */
  Boolean deleteByRoleId(String roleId) throws OuroborosException;

  /**
   * 按userIdentity删除
   *
   * @param userIdentity
   * @return
   * @throws OuroborosException
   */
  Boolean deleteByUserIdentity(String userIdentity) throws OuroborosException;

  /**
   * 按roleId与userIdentities删除
   *
   * @param roleId
   * @param userIdentities
   * @return
   * @throws OuroborosException
   */
  Boolean deleteByRoleIdAndUserIdentities(String roleId, Collection<String> userIdentities) throws OuroborosException;

  /**
   * 按userIdentity与roleIds删除
   *
   * @param userIdentity
   * @param roleIds
   * @return
   * @throws OuroborosException
   */
  Boolean deleteByUserIdentityAndRoleIds(String userIdentity, Collection<String> roleIds) throws OuroborosException;

  /**
   * 按角色id与用户identity查询
   *
   * @param roleId
   * @param userIdentity
   * @return
   */
  Optional<UserRoleMapEntity> getByRoleIdAndUserIdentity(String roleId, String userIdentity);

  /**
   * 按角色id查询用户列表
   *
   * @param roleId
   * @return
   */
  List<UserRoleMapEntity> searchListByRoleId(String roleId);

  /**
   * 按用户identity查询角色列表
   *
   * @param userIdentity
   * @return
   */
  List<UserRoleMapEntity> searchListByUserIdentity(String userIdentity);
}
