package com.ouroboros.organizationstructure.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;

/**
 * 员工存储服务接口 - 操作EmployeeEntity
 *
 * @author songmingxu
 */
public interface EmployeeManageService {

  /**
   * 添加
   */
  EmployeeEntity insert(EmployeeEntity entity) throws OuroborosException;

  /**
   * 添加多个
   */
  List<EmployeeEntity> insertBatch(Collection<EmployeeEntity> entities) throws OuroborosException;

  /**
   * 删除
   */
  Boolean deleteById(String id) throws OuroborosException;

  /**
   * 更新
   */
  Boolean updateById(String id, EmployeeEntity entity) throws OuroborosException;

  /**
   * 根据员工id更新部门id
   */
  Boolean updateDepartmentByIds(Collection<String> ids, String departmentId) throws OuroborosException;

  /**
   * 通过id获取
   */
  Optional<EmployeeEntity> findById(String id);

  /**
   * 根据id列表查询
   */
  List<EmployeeEntity> findByIds(Collection<String> ids);

  /**
   * 查询全部
   */
  List<EmployeeEntity> findAll();
}
