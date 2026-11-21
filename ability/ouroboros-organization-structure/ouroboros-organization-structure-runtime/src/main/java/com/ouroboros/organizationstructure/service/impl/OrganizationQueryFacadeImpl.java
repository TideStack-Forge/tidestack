package com.ouroboros.organizationstructure.service.impl;

import com.ouroboros.organizationstructure.*;
import com.ouroboros.organizationstructure.dto.response.*;
import com.ouroboros.organizationstructure.entity.AdminLevelEntity;
import com.ouroboros.organizationstructure.service.AdminLevelService;
import com.ouroboros.organizationstructure.service.EmployeeAppointmentInfo;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.exception.BusinessException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 组织架构查询门面实现
 */
@Service
public class OrganizationQueryFacadeImpl implements OrganizationQueryFacade {

  private final OrganizationAggregator organizationAggregator;
  private final EmployeeAggregator employeeAggregator;
  private final AppointmentAggregator appointmentAggregator;
  private final AdminLevelService adminLevelService;

  public OrganizationQueryFacadeImpl(
      OrganizationAggregator organizationAggregator,
      EmployeeAggregator employeeAggregator,
      AppointmentAggregator appointmentAggregator) {
    this(organizationAggregator, employeeAggregator, appointmentAggregator, null);
  }

  @Autowired
  public OrganizationQueryFacadeImpl(
      OrganizationAggregator organizationAggregator,
      EmployeeAggregator employeeAggregator,
      AppointmentAggregator appointmentAggregator,
      AdminLevelService adminLevelService) {
    this.organizationAggregator = organizationAggregator;
    this.employeeAggregator = employeeAggregator;
    this.appointmentAggregator = appointmentAggregator;
    this.adminLevelService = adminLevelService;
  }

  // ==================== 公司查询 ====================
  @Override
  public Optional<Company> getCompanyById(String id) {
    return organizationAggregator.getById(id)
        .filter(Company.class::isInstance)
        .map(Company.class::cast);
  }

  @Override
  public List<Company> getAllCompanies() {
    return organizationAggregator.getAllCompanies();
  }

  @Override
  public List<CompanyDTO> getAllCompaniesDTO() {
    return getAllCompanies().stream()
        .map(CompanyDTO::from)
        .collect(Collectors.toList());
  }

  // ==================== 部门查询 ====================
  @Override
  public Optional<Department> getDepartmentById(String id) {
    return organizationAggregator.getById(id)
        .filter(Department.class::isInstance)
        .map(Department.class::cast);
  }

  @Override
  public List<Department> getAllDepartments() {
    return organizationAggregator.getAllDepartments();
  }

  @Override
  public List<Department> getDepartmentsByCompany(String companyId) {
    return organizationAggregator.getDescendants(companyId).stream()
        .filter(Department.class::isInstance)
        .map(Department.class::cast)
        .collect(Collectors.toList());
  }

  @Override
  public List<DepartmentDTO> getAllDepartmentsDTO() {
    return getAllDepartments().stream()
        .map(DepartmentDTO::from)
        .collect(Collectors.toList());
  }

  @Override
  public List<DepartmentDTO> getDepartmentsByCompanyDTO(String companyId) {
    return getDepartmentsByCompany(companyId).stream()
        .map(DepartmentDTO::from)
        .collect(Collectors.toList());
  }

  // ==================== 岗位查询 ====================
  @Override
  public Optional<Position> getPositionById(String id) {
    return organizationAggregator.getById(id)
        .filter(Position.class::isInstance)
        .map(Position.class::cast);
  }

  @Override
  public List<Position> getAllPositions() {
    return organizationAggregator.getAll().stream()
        .filter(Position.class::isInstance)
        .map(Position.class::cast)
        .collect(Collectors.toList());
  }

  @Override
  public List<Position> getPositionsByDepartment(String departmentId) {
    return organizationAggregator.getDescendantPositions(departmentId);
  }

  @Override
  public List<PositionDTO> getAllPositionsDTO() {
    return getAllPositions().stream()
        .map(PositionDTO::from)
        .collect(Collectors.toList());
  }

  @Override
  public List<PositionDTO> getPositionsByDepartmentDTO(String departmentId) {
    return getPositionsByDepartment(departmentId).stream()
        .map(PositionDTO::from)
        .collect(Collectors.toList());
  }

  // ==================== 员工查询 ====================
  @Override
  public Optional<Employee> getEmployeeById(String id) {
    return employeeAggregator.getById(id);
  }

  @Override
  public List<Employee> getAllEmployees() {
    return employeeAggregator.getAll();
  }

  @Override
  public List<Employee> getEmployeesByDepartment(String departmentId) {
    return employeeAggregator.getByDepartment(departmentId);
  }

  @Override
  public List<Employee> getEmployeesByPosition(String positionId) {
    return employeeAggregator.getByPosition(positionId);
  }

  @Override
  public List<EmployeeDTO> getAllEmployeesDTO() {
    return getAllEmployees().stream()
        .map(EmployeeDTO::from)
        .collect(Collectors.toList());
  }

  @Override
  public List<EmployeeDTO> getEmployeesByDepartmentDTO(String departmentId) {
    return getEmployeesByDepartment(departmentId).stream()
        .map(EmployeeDTO::from)
        .collect(Collectors.toList());
  }

  @Override
  public List<EmployeeDTO> getEmployeesByPositionDTO(String positionId) {
    return getEmployeesByPosition(positionId).stream()
        .map(EmployeeDTO::from)
        .collect(Collectors.toList());
  }

  @Override
  public List<OrganizationNode> getOrganizationChain(String employeeId) {
    // 获取员工信息
    Optional<Employee> employee = employeeAggregator.getById(employeeId);
    if (!employee.isPresent() || employee.get().getDepartment() == null) {
      return Collections.emptyList();
    }
    
    // 获取员工所在部门的组织链
    return getNodePath(employee.get().getDepartment());
  }

  @Override
  public List<EmployeeAppointmentInfo> getAppointments(String employeeId) {
    List<Appointment> appointments = appointmentAggregator.getByEmployee(employeeId);
    List<EmployeeAppointmentInfo> result = new ArrayList<>();
    
    for (Appointment appointment : appointments) {
      Position position = organizationAggregator.getById(appointment.getPosition())
          .filter(Position.class::isInstance)
          .map(Position.class::cast)
          .orElse(null);
      OrganizationNode department = null;
      if (position != null && position.getParent() != null) {
        department = organizationAggregator.getById(position.getParent()).orElse(null);
      }
      
      result.add(new EmployeeAppointmentInfo(appointment, position, department));
    }
    
    return result;
  }

  // ==================== 任职查询 ====================
  @Override
  public Optional<Appointment> getAppointmentById(String id) {
    return appointmentAggregator.getById(id);
  }

  @Override
  public List<Appointment> getAppointmentsByEmployee(String employeeId) {
    return appointmentAggregator.getByEmployee(employeeId);
  }

  @Override
  public List<Appointment> getCurrentAppointmentsByEmployee(String employeeId) {
    return appointmentAggregator.getCurrentByEmployee(employeeId);
  }

  @Override
  public List<Appointment> getAppointmentsByPosition(String positionId) {
    return appointmentAggregator.getByPosition(positionId);
  }

  @Override
  public List<Appointment> getAppointmentsByDepartment(String departmentId) {
    List<Employee> employees = employeeAggregator.getByDepartment(departmentId);
    return employees.stream()
        .flatMap(emp -> appointmentAggregator.getByEmployee(emp.getId()).stream())
        .collect(Collectors.toList());
  }

  @Override
  public List<AppointmentDTO> getAppointmentsByEmployeeDTO(String employeeId) {
    return getAppointmentsByEmployee(employeeId).stream()
        .map(AppointmentDTO::from)
        .collect(Collectors.toList());
  }

  @Override
  public List<AppointmentDTO> getAppointmentsByPositionDTO(String positionId) {
    return getAppointmentsByPosition(positionId).stream()
        .map(AppointmentDTO::from)
        .collect(Collectors.toList());
  }

  @Override
  public List<AppointmentDTO> getAppointmentsByDepartmentDTO(String departmentId) {
    return getAppointmentsByDepartment(departmentId).stream()
        .map(AppointmentDTO::from)
        .collect(Collectors.toList());
  }

  @Override
  public List<AppointmentDTO> getAllAppointmentsDTO() {
    return appointmentAggregator.getAll().stream()
        .map(AppointmentDTO::from)
        .collect(Collectors.toList());
  }

  // ==================== 树形结构 ====================
  @Override
  public List<OrganizationNodeDTO> buildTree(String rootId, boolean includePositions, List<String> nodeTypes) {
    // 1. 获取根节点
    List<OrganizationNode> roots = (rootId == null)
      ? organizationAggregator.getAll().stream()
        .filter(node -> isTopLevel(node.getParent()))
        .collect(Collectors.toList())
      : Collections.singletonList(getRequiredNode(rootId));

    // 2. 定义过滤条件
    Predicate<OrganizationNode> filter = node -> {
      if (!includePositions && node.getCategory() == NodeCategory.FUNCTIONAL) {
        return false;
      }
      if (nodeTypes != null && !nodeTypes.isEmpty() && !nodeTypes.contains(node.getType())) {
        return false;
      }
      return true;
    };

    // 3. 递归构建DTO树形结构
    return roots.stream()
        .filter(filter)
        .map(root -> buildTreeNodeDTO(root, filter))
        .collect(Collectors.toList());
  }

  private OrganizationNodeDTO buildTreeNodeDTO(OrganizationNode node, Predicate<OrganizationNode> filter) {
    OrganizationNodeDTO dto = toNodeDTO(node);
    
    // 获取直接子节点
    List<OrganizationNode> children = organizationAggregator.getDescendants(node.getId()).stream()
        .filter(child -> Objects.equals(child.getParent(), node.getId()))
        .filter(filter)
        .collect(Collectors.toList());
    
    // 递归构建子节点DTO
    if (!children.isEmpty()) {
      List<OrganizationNodeDTO> childDTOs = children.stream()
          .map(child -> buildTreeNodeDTO(child, filter))
          .collect(Collectors.toList());
      dto.setChildren(childDTOs);
    }
    
    return dto;
  }

  @Override
  public List<OrganizationNodeDTO> buildTree(String rootId, boolean includePositions, List<String> nodeTypes, String type) {
    if (type == null || type.trim().isEmpty()) {
      return buildTree(rootId, includePositions, nodeTypes);
    }
    
    List<OrganizationNode> allNodes = organizationAggregator.getAll();
    
    switch (type.toLowerCase()) {
      case "position":
        return filterForPositionSelection(allNodes, rootId);
      case "department":
        return filterForDepartmentSelection(allNodes, rootId);
      case "company":
        return filterForCompanySelection(allNodes, rootId);
      default:
        return buildTree(rootId, includePositions, nodeTypes);
    }
  }

  private List<OrganizationNodeDTO> filterForPositionSelection(List<OrganizationNode> allNodes, String rootId) {
    // 1. 找出所有岗位
    Set<String> positionIds = allNodes.stream()
        .filter(n -> "position".equals(n.getType()))
        .map(OrganizationNode::getId)
        .collect(Collectors.toSet());
    
    // 2. 找出包含岗位的部门
    Set<String> departmentsWithPositions = allNodes.stream()
        .filter(n -> "position".equals(n.getType()))
        .map(OrganizationNode::getParent)
        .filter(p -> p != null && !p.trim().isEmpty())
        .collect(Collectors.toSet());
    
    // 3. 找出包含这些部门的公司（递归向上查找）
    Set<String> companiesWithDepartments = new HashSet<>();
    for (String deptId : departmentsWithPositions) {
      findAncestorCompanies(deptId, allNodes, companiesWithDepartments);
    }
    
    // 4. 构建树，公司和部门id设为null
    List<OrganizationNode> filteredNodes = allNodes.stream()
        .filter(n -> {
          if ("position".equals(n.getType())) {
            return positionIds.contains(n.getId());
          }
          if ("department".equals(n.getType())) {
            return departmentsWithPositions.contains(n.getId());
          }
          if ("company".equals(n.getType())) {
            return companiesWithDepartments.contains(n.getId());
          }
          return false;
        })
        .collect(Collectors.toList());
    
    return buildTreeWithNullIds(filteredNodes, rootId, new HashSet<>(Arrays.asList("company", "department")));
  }

  private void findAncestorCompanies(String nodeId, List<OrganizationNode> allNodes, Set<String> companies) {
    OrganizationNode node = allNodes.stream()
        .filter(n -> nodeId.equals(n.getId()))
        .findFirst()
        .orElse(null);
    
    if (node != null && node.getParent() != null && !node.getParent().trim().isEmpty()) {
      OrganizationNode parent = allNodes.stream()
          .filter(n -> node.getParent().equals(n.getId()))
          .findFirst()
          .orElse(null);
      
      if (parent != null) {
        if ("company".equals(parent.getType())) {
          companies.add(parent.getId());
        }
        // 继续向上查找
        findAncestorCompanies(parent.getId(), allNodes, companies);
      }
    }
  }

  private List<OrganizationNodeDTO> filterForDepartmentSelection(List<OrganizationNode> allNodes, String rootId) {
    // 不显示岗位，公司id设为null
    List<OrganizationNode> filteredNodes = allNodes.stream()
        .filter(n -> !"position".equals(n.getType()))
        .collect(Collectors.toList());
    
    return buildTreeWithNullIds(filteredNodes, rootId, new HashSet<>(Collections.singletonList("company")));
  }

  private List<OrganizationNodeDTO> filterForCompanySelection(List<OrganizationNode> allNodes, String rootId) {
    // 只显示公司
    List<OrganizationNode> filteredNodes = allNodes.stream()
        .filter(n -> "company".equals(n.getType()))
        .collect(Collectors.toList());
    
    return buildTreeWithNullIds(filteredNodes, rootId, new HashSet<>());
  }

  private List<OrganizationNodeDTO> buildTreeWithNullIds(List<OrganizationNode> nodes, String rootId, Set<String> nullIdTypes) {
    // 获取根节点
    List<OrganizationNode> roots = (rootId == null)
        ? nodes.stream()
            .filter(node -> isTopLevel(node.getParent()))
            .collect(Collectors.toList())
        : nodes.stream()
            .filter(node -> node.getId().equals(rootId))
            .collect(Collectors.toList());

    if (rootId != null && roots.isEmpty() && !organizationAggregator.getById(rootId).isPresent()) {
      throw new BusinessException(404, "组织节点不存在");
    }
    
    // 构建树
    return roots.stream()
        .map(root -> buildTreeNodeDTOWithNullIds(root, nodes, nullIdTypes))
        .collect(Collectors.toList());
  }

  private OrganizationNodeDTO buildTreeNodeDTOWithNullIds(OrganizationNode node, List<OrganizationNode> allNodes, Set<String> nullIdTypes) {
    OrganizationNodeDTO dto = toNodeDTO(node);
    
    // 如果类型在nullIdTypes中，设置id为null
    if (nullIdTypes.contains(node.getType())) {
      dto.setId(null);
    }
    
    // 获取直接子节点
    List<OrganizationNode> children = allNodes.stream()
        .filter(child -> node.getId().equals(child.getParent()))
        .collect(Collectors.toList());
    
    // 递归构建子节点
    if (!children.isEmpty()) {
      List<OrganizationNodeDTO> childDTOs = children.stream()
          .map(child -> buildTreeNodeDTOWithNullIds(child, allNodes, nullIdTypes))
          .collect(Collectors.toList());
      dto.setChildren(childDTOs);
    }
    
    return dto;
  }

  @Override
  public List<OrganizationNode> getNodePath(String nodeId) {
    OrganizationNode node = getRequiredNode(nodeId);
    List<OrganizationNode> ancestors = new ArrayList<>(organizationAggregator.getAncestors(nodeId));
    Collections.reverse(ancestors);
    ancestors.add(node);
    return ancestors;
  }

  // ==================== 备用通用查询 ====================
  @Override
  public Optional<OrganizationNode> getNodeById(String id) {
    return organizationAggregator.getById(id);
  }

  @Override
  public List<OrganizationNode> getNodeChildren(String id) {
    getRequiredNode(id);
    return organizationAggregator.getDescendants(id).stream()
        .filter(node -> !isTopLevel(node.getParent()) && node.getParent().equals(id))
        .collect(Collectors.toList());
  }

  @Override
  public List<OrganizationNodeDTO> getNodeChildrenDTO(String id) {
    return getNodeChildren(id).stream()
        .map(this::toNodeDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<OrganizationNode> getNodeDescendants(String id) {
    return organizationAggregator.getDescendants(id);
  }

  // ==================== 废弃方法（向后兼容） ====================
  @Override
  @Deprecated
  public OrganizationAggregator aggregator() {
    return organizationAggregator;
  }

  /**
   * 判断是否为顶级节点（parent为null或空字符串）
   */
  private boolean isTopLevel(String parent) {
    return parent == null || parent.trim().isEmpty();
  }

  private OrganizationNode getRequiredNode(String nodeId) {
    return organizationAggregator.getById(nodeId)
        .orElseThrow(() -> new BusinessException(404, "组织节点不存在"));
  }

  private OrganizationNodeDTO toNodeDTO(OrganizationNode node) {
    OrganizationNodeDTO dto = OrganizationNodeDTO.from(node);
    dto.setAdminLevelScope(resolveAdminLevelScope(node));
    return dto;
  }

  private String resolveAdminLevelScope(OrganizationNode node) {
    if (node == null || "position".equals(node.getType())) {
      return null;
    }
    if (adminLevelService != null && StringUtils.isNotBlank(node.getAdminLevel())) {
      return adminLevelService.findByCode(node.getAdminLevel())
          .map(AdminLevelEntity::getScope)
          .orElseGet(() -> fallbackAdminLevelScope(node));
    }
    return fallbackAdminLevelScope(node);
  }

  private String fallbackAdminLevelScope(OrganizationNode node) {
    if ("company".equals(node.getType())) {
      return "ORG";
    }
    if ("department".equals(node.getType())) {
      return "DEPT";
    }
    return null;
  }
}
