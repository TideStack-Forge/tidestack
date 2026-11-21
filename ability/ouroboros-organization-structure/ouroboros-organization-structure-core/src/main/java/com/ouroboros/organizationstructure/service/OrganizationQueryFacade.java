package com.ouroboros.organizationstructure.service;

import com.ouroboros.organizationstructure.*;
import com.ouroboros.organizationstructure.dto.response.*;
import com.ouroboros.organizationstructure.service.EmployeeAppointmentInfo;
import java.util.List;
import java.util.Optional;

/**
 * 组织架构查询门面
 */
public interface OrganizationQueryFacade {

  // ==================== 公司查询 ====================
  Optional<Company> getCompanyById(String id);
  List<Company> getAllCompanies();
  List<CompanyDTO> getAllCompaniesDTO();
  
  // ==================== 部门查询 ====================
  Optional<Department> getDepartmentById(String id);
  List<Department> getAllDepartments();
  List<Department> getDepartmentsByCompany(String companyId);
  List<DepartmentDTO> getAllDepartmentsDTO();
  List<DepartmentDTO> getDepartmentsByCompanyDTO(String companyId);
  
  // ==================== 岗位查询 ====================
  Optional<Position> getPositionById(String id);
  List<Position> getAllPositions();
  List<Position> getPositionsByDepartment(String departmentId);
  List<PositionDTO> getAllPositionsDTO();
  List<PositionDTO> getPositionsByDepartmentDTO(String departmentId);
  
  // ==================== 员工查询 ====================
  Optional<Employee> getEmployeeById(String id);
  List<Employee> getAllEmployees();
  List<Employee> getEmployeesByDepartment(String departmentId);
/**
 * 根据职位ID获取员工列表
 *
 * @param positionId 职位ID，用于筛选特定职位的员工
 * @return 返回匹配指定职位ID的员工列表，如果没有找到则返回空列表
 */
  List<Employee> getEmployeesByPosition(String positionId);
  List<EmployeeDTO> getAllEmployeesDTO();
  List<EmployeeDTO> getEmployeesByDepartmentDTO(String departmentId);
  List<EmployeeDTO> getEmployeesByPositionDTO(String positionId);
  
  // 员工组织链查询
  List<OrganizationNode> getOrganizationChain(String employeeId);
  List<EmployeeAppointmentInfo> getAppointments(String employeeId);
  
  // ==================== 任职查询 ====================
  Optional<Appointment> getAppointmentById(String id);
  List<Appointment> getAppointmentsByEmployee(String employeeId);
  List<Appointment> getCurrentAppointmentsByEmployee(String employeeId);
  List<Appointment> getAppointmentsByPosition(String positionId);
  List<Appointment> getAppointmentsByDepartment(String departmentId);
  List<AppointmentDTO> getAppointmentsByEmployeeDTO(String employeeId);
  List<AppointmentDTO> getAppointmentsByPositionDTO(String positionId);
  List<AppointmentDTO> getAppointmentsByDepartmentDTO(String departmentId);
  List<AppointmentDTO> getAllAppointmentsDTO();
  
  // ==================== 树形结构 ====================
  List<OrganizationNodeDTO> buildTree(String rootId, boolean includePositions, List<String> nodeTypes);
  List<OrganizationNodeDTO> buildTree(String rootId, boolean includePositions, List<String> nodeTypes, String type);
  List<OrganizationNode> getNodePath(String nodeId);
  
  // ==================== 备用通用查询 ====================
  Optional<OrganizationNode> getNodeById(String id);
  List<OrganizationNode> getNodeChildren(String id);
  List<OrganizationNodeDTO> getNodeChildrenDTO(String id);
  List<OrganizationNode> getNodeDescendants(String id);

  // ==================== 废弃方法（向后兼容） ====================
  /**
   * 获取组织架构聚合器（直接访问）
   * @deprecated Since 1.0.0-beta.2, for removal in 1.0.0-beta.4. Use specific query methods instead to avoid direct aggregator access.
   */
  @Deprecated
  OrganizationAggregator aggregator();
}
