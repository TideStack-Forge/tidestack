package com.ouroboros.security.rbac.service;

import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.security.rbac.entity.UserSubjectEntity;

/**
 * 用户-主体绑定关系服务接口
 * 
 * @author songmingxu
 */
public interface UserSubjectService {

  /**
   * 创建绑定关系
   *
   * @param entity 绑定关系实体
   * @return 创建的实体
   * @throws OuroborosException 如果创建失败
   */
  UserSubjectEntity create(UserSubjectEntity entity) throws OuroborosException;

  /**
   * 创建绑定关系
   *
   * @param userIdentity 用户标识
   * @param subjectType 主体类型
   * @param subjectId 主体ID
   * @return 创建的实体
   * @throws OuroborosException 如果创建失败
   */
  UserSubjectEntity create(String userIdentity, String subjectType, String subjectId) throws OuroborosException;

  /**
   * 根据用户标识查询绑定关系
   *
   * @param userIdentity 用户标识
   * @return 绑定关系，如果不存在则返回空
   */
  Optional<UserSubjectEntity> findByUser(String userIdentity);

  /**
   * 根据用户标识和主体类型查询绑定关系
   *
   * @param userIdentity 用户标识
   * @param subjectType 主体类型
   * @return 绑定关系，如果不存在则返回空
   */
  Optional<UserSubjectEntity> findByUserAndSubjectType(String userIdentity, String subjectType);

  /**
   * 根据用户标识查询所有绑定关系
   *
   * @param userIdentity 用户标识
   * @return 绑定关系列表
   */
  List<UserSubjectEntity> findAllByUser(String userIdentity);

  /**
   * 根据主体类型和ID查询绑定关系
   *
   * @param subjectType 主体类型
   * @param subjectId   主体ID
   * @return 绑定关系，如果不存在则返回空
   */
  Optional<UserSubjectEntity> findBySubject(String subjectType, String subjectId);

  /**
   * 删除绑定关系
   *
   * @param userIdentity 用户标识
   * @return 是否删除成功
   * @throws OuroborosException 如果删除失败
   */
  Boolean deleteByUser(String userIdentity) throws OuroborosException;

  /**
   * 根据用户标识和主体类型删除绑定关系
   *
   * @param userIdentity 用户标识
   * @param subjectType 主体类型
   * @return 是否删除成功
   * @throws OuroborosException 如果删除失败
   */
  Boolean deleteByUserAndSubjectType(String userIdentity, String subjectType) throws OuroborosException;

  /**
   * 查询所有绑定关系
   *
   * @return 所有绑定关系列表
   */
  List<UserSubjectEntity> findAll();
}
