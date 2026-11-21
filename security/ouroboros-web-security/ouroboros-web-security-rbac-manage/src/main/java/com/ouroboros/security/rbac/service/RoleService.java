package com.ouroboros.security.rbac.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.security.rbac.entity.RoleEntity;

/**
 * @author liansz
 */
public interface RoleService {

  /**
   * 添加
   *
   * @param model
   * @return
   * @throws OuroborosException
   */
  RoleEntity add(RoleEntity model) throws OuroborosException;

  /**
   * 添加多个
   *
   * @param models
   * @return
   * @throws OuroborosException
   */
  List<RoleEntity> add(Collection<RoleEntity> models) throws OuroborosException;

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
  Boolean update(String id, RoleEntity model) throws OuroborosException;

  /**
   * 通过ID获取
   *
   * @param id
   * @return
   */
  Optional<RoleEntity> getById(String id);

  /**
   * 查询全部
   *
   * @return
   */
  List<RoleEntity> searchAll();
}
