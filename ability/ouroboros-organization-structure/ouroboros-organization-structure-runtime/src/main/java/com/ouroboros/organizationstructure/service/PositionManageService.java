package com.ouroboros.organizationstructure.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.entity.PositionEntity;

/**
 * 岗位管理服务 - 管理职能性节点（岗位）
 *
 * @author songmingxu
 */
public interface PositionManageService {

  /**
   * 默认岗位名称
   */
  String DEFAULT_POSITION_NAME = "成员";
  String DEFAULT_POSITION_KEY = "member";

  /**
   * 添加岗位
   */
  PositionEntity add(PositionEntity position) throws OuroborosException;

  /**
   * 批量添加岗位
   */
  List<PositionEntity> addBatch(Collection<PositionEntity> positions) throws OuroborosException;

  /**
   * 删除岗位
   */
  Boolean delete(String id) throws OuroborosException;

  /**
   * 更新岗位
   */
  Boolean update(String id, PositionEntity position) throws OuroborosException;

  /**
   * 根据ID查找岗位
   */
  Optional<PositionEntity> findById(String id);

  /**
   * 根据ID列表查找岗位
   */
  List<PositionEntity> findByIds(Collection<String> ids);

  /**
   * 查找所有岗位
   */
  List<PositionEntity> findAll();

  /**
   * 根据部门ID查找岗位列表
   */
  List<PositionEntity> findByDepartment(String departmentId);

  /**
   * 根据部门ID查找岗位列表（包含子部门）
   */
  List<PositionEntity> findByDepartmentWithDescendants(String departmentId);

  List<PositionEntity> findByPositionKeys(Collection<String> positionKeys);

  Optional<PositionEntity> findByDepartmentAndPositionKey(String departmentId, String positionKey);

  PositionEntity assignDefinitionToDepartment(String departmentId,
                                              String positionKey,
                                              String fallbackName,
                                              Integer headcount,
                                              String leaderEmployee) throws OuroborosException;

  /**
   * 获取或创建部门的默认岗位（名称为"成员"）
   */
  PositionEntity getOrCreateDefaultPosition(String departmentId) throws OuroborosException;
}
