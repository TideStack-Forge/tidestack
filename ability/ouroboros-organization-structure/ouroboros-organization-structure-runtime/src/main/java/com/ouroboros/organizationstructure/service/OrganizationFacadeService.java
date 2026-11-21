package com.ouroboros.organizationstructure.service;

import java.time.LocalDate;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.dto.request.CreateAppointmentRequest;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.entity.PositionEntity;

/**
 * 组织架构门面服务
 * 
 * <p>提供组织架构管理的统一入口，封装复杂的跨服务操作。
 * 所有组织架构相关操作都应通过此门面服务进行。
 *
 * @author songmingxu
 */
public interface OrganizationFacadeService {

  // ==================== 复合操作 ====================

  /**
   * 创建部门（可选同时创建默认岗位"成员"）
   * 
   * <p>复合操作：
   * <ol>
   *   <li>创建部门节点：structure().addDepartment(department)</li>
   *   <li>如果 withDefaultPosition=true，创建默认岗位：position().getOrCreateDefaultPosition(departmentId)</li>
   * </ol>
   *
   * @param department          部门信息
   * @param withDefaultPosition 是否同时创建默认岗位
   * @return 部门ID
   */
  String createDepartment(OrganizationNodeEntity department, boolean withDefaultPosition) throws OuroborosException;

  /**
   * 员工调岗（结束当前主岗，分配新主岗）
   * 
   * <p>复合操作：
   * <ol>
   *   <li>查询当前主岗任职：appointment().findPrimaryAppointment(employeeId)</li>
   *   <li>结束当前主岗：appointment().endAppointment(currentAppointmentId)</li>
   *   <li>创建新主岗任职：appointment().create(employeeId, newPositionId, PRIMARY, now, null)</li>
   * </ol>
   *
   * @param employeeId    员工ID
   * @param newPositionId 新岗位ID
   */
  void transferToPosition(String employeeId, String newPositionId) throws OuroborosException;

  /**
   * 员工调部门（结束当前主岗，分配到新部门的默认岗位）
   * 
   * <p>复合操作：
   * <ol>
   *   <li>查询当前主岗任职：appointment().findPrimaryAppointment(employeeId)</li>
   *   <li>结束当前主岗：appointment().endAppointment(currentAppointmentId)</li>
   *   <li>获取或创建新部门的默认岗位：position().getOrCreateDefaultPosition(newDepartmentId)</li>
   *   <li>创建新主岗任职：appointment().create(employeeId, defaultPositionId, PRIMARY, now, null)</li>
   * </ol>
   *
   * @param employeeId      员工ID
   * @param newDepartmentId 新部门ID
   */
  void transferToDepartment(String employeeId, String newDepartmentId) throws OuroborosException;

  // ==================== 组织节点管理 ====================

  /**
   * 创建公司
   */
  String createCompany(OrganizationNodeEntity company) throws OuroborosException;

  /**
   * 更新组织节点
   */
  void updateNode(String id, OrganizationNodeEntity node) throws OuroborosException;

  /**
   * 移动节点（调整父级）
   */
  void moveNode(String id, String newParentId) throws OuroborosException;

  /**
   * 删除节点
   */
  void deleteNode(String id) throws OuroborosException;

  // ==================== 岗位管理 ====================

  /**
   * 创建岗位
   */
  String createPosition(PositionEntity position) throws OuroborosException;

  /**
   * 更新岗位
   */
  void updatePosition(String id, PositionEntity position) throws OuroborosException;

  /**
   * 删除岗位
   */
  void deletePosition(String id) throws OuroborosException;

  // ==================== 员工管理 ====================

  /**
   * 创建员工（可选指定部门，自动创建主岗任职）
   */
  String createEmployee(EmployeeEntity employee, String departmentId) throws OuroborosException;

  /**
   * 更新员工
   */
  void updateEmployee(String id, EmployeeEntity employee) throws OuroborosException;

  /**
   * 删除员工
   */
  void deleteEmployee(String id) throws OuroborosException;

  /**
   * 关联用户
   */
  void linkEmployeeUser(String employeeId, String userId) throws OuroborosException;

  /**
   * 取消用户关联
   */
  void unlinkEmployeeUser(String employeeId) throws OuroborosException;

  // ==================== 任职管理 ====================

  /**
   * 创建任职
   */
  String createAppointment(CreateAppointmentRequest request) throws OuroborosException;

  // ==================== 便利方法（组合操作） ====================

  /**
   * 分配员工到岗位（创建主岗任职）
   */
  void assignToPosition(String employeeId, String positionId) throws OuroborosException;

  /**
   * 添加员工副岗
   */
  void addSecondaryPosition(String employeeId, String positionId) throws OuroborosException;

  /**
   * 设置员工代理岗位
   */
  void setActingPosition(String employeeId, String positionId, LocalDate startDate, LocalDate endDate) throws OuroborosException;

  /**
   * 移除任职
   */
  void removeAppointment(String appointmentId);

  /**
   * 分配员工到部门（分配到部门默认岗位）
   */
  void assignToDepartment(String employeeId, String departmentId) throws OuroborosException;
}
