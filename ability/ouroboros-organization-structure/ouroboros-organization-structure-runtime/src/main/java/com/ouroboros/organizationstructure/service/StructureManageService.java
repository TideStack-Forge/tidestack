package com.ouroboros.organizationstructure.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;

/**
 * 结构性节点管理服务 - 管理公司、部门等结构性节点
 *
 * @author songmingxu
 */
public interface StructureManageService {

  // ==================== 通用操作 ====================

  /**
   * 添加结构性节点
   */
  OrganizationNodeEntity add(OrganizationNodeEntity node) throws OuroborosException;

  /**
   * 批量添加结构性节点
   */
  List<OrganizationNodeEntity> addBatch(Collection<OrganizationNodeEntity> nodes) throws OuroborosException;

  /**
   * 删除节点
   */
  Boolean delete(String id) throws OuroborosException;

  /**
   * 更新节点
   */
  Boolean update(String id, OrganizationNodeEntity node) throws OuroborosException;

  /**
   * 根据ID查找节点
   */
  Optional<OrganizationNodeEntity> findById(String id);

  /**
   * 根据ID列表查找节点
   */
  List<OrganizationNodeEntity> findByIds(Collection<String> ids);

  /**
   * 查找所有结构性节点
   */
  List<OrganizationNodeEntity> findAll();

  /**
   * 查找子节点列表
   */
  List<OrganizationNodeEntity> findChildren(String parentId);

  // ==================== Company操作 ====================

  /**
   * 添加公司
   */
  OrganizationNodeEntity addCompany(OrganizationNodeEntity company) throws OuroborosException;

  /**
   * 查找所有公司
   */
  List<OrganizationNodeEntity> findAllCompanies();

  // ==================== Department操作 ====================

  /**
   * 添加部门
   */
  OrganizationNodeEntity addDepartment(OrganizationNodeEntity department) throws OuroborosException;

  /**
   * 查找所有部门
   */
  List<OrganizationNodeEntity> findAllDepartments();

  /**
   * 查找公司下的部门
   */
  List<OrganizationNodeEntity> findDepartmentsByCompany(String companyId);
}
