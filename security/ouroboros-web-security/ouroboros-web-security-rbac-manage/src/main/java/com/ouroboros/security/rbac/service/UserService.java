package com.ouroboros.security.rbac.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.security.rbac.entity.UserEntity;

/**
 * @author liansz
 */
public interface UserService {

  /**
   * 添加
   *
   * @param model
   * @return
   * @throws OuroborosException
   */
  UserEntity add(UserEntity model) throws OuroborosException;

  /**
   * 添加多个
   *
   * @param models
   * @return
   * @throws OuroborosException
   */
  List<UserEntity> add(Collection<UserEntity> models) throws OuroborosException;

  /**
   * 删除
   *
   * @param id
   * @return
   * @throws OuroborosException
   */
  Boolean delete(String id) throws OuroborosException;

  /**
   * 更新
   *
   * @param model
   * @return
   * @throws OuroborosException
   */
  Boolean update(String id, UserEntity model) throws OuroborosException;

  /**
   * 重置密码
   *
   * @param userIdentity
   * @return
   * @throws OuroborosException
   */
  Boolean resetPassword(String userIdentity) throws OuroborosException;

  /**
   * 修改密码
   *
   * @param userIdentity
   * @param password
   * @return
   * @throws OuroborosException
   */
  Boolean modifyPassword(String userIdentity, String password) throws OuroborosException;

  /**
   * 通过ID获取
   *
   * @param userIdentity
   * @return
   */
  Optional<UserEntity> getByUserIdentity(String userIdentity);

  /**
   * 按用户id查询密码
   *
   * @param userIdentity
   * @return
   */
  Optional<String> getPasswordByUserIdentity(String userIdentity);

  /**
   * 查询全部
   *
   * @return
   */
  List<UserEntity> searchAll();

  void saveProfile(String identity, String publicSubject, long expectedRevision, java.util.Map<String, Object> patch);
}
