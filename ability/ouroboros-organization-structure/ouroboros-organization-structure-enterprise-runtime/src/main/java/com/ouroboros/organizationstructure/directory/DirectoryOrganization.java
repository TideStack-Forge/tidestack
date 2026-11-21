package com.ouroboros.organizationstructure.directory;

import java.time.LocalDate;
import java.util.*;

import com.ouroboros.enterprise.directory.*;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.organizationstructure.AppointmentType;
import com.ouroboros.organizationstructure.dto.request.CreateAppointmentRequest;
import com.ouroboros.organizationstructure.entity.*;
import com.ouroboros.organizationstructure.service.*;
import com.ouroboros.util.Json;

/** Adapts directory commands to the original organization management services. */
public class DirectoryOrganization {
  private final OrganizationFacadeService organization;
  private final StructureManageService nodes;
  private final EmployeeManageService employees;
  private final PositionManageService positions;
  private final AppointmentManageService appointments;

  public DirectoryOrganization(OrganizationFacadeService organization, StructureManageService nodes,
      EmployeeManageService employees, PositionManageService positions, AppointmentManageService appointments) {
    this.organization = organization; this.nodes = nodes; this.employees = employees;
    this.positions = positions; this.appointments = appointments;
  }

  public OrganizationNodeEntity company(String id) {
    var node = node(id);
    if (!"company".equals(node.getType())) { throw new ValidationException("同步范围必须选择本地公司"); }
    return node;
  }

  public OrganizationNodeEntity node(String id) {
    return nodes.findById(id).orElseThrow(() -> new ValidationException("本地组织节点不存在"));
  }

  public EmployeeEntity employee(String id) {
    return employees.findById(id).orElseThrow(() -> new ValidationException("本地员工不存在"));
  }

  public boolean contains(String companyId, String nodeId) {
    var visited = new HashSet<String>();
    for (String id = nodeId; id != null; id = node(id).getParent()) {
      if (!visited.add(id)) { throw new ValidationException("本地组织层级存在环"); }
      if (companyId.equals(id)) { return true; }
    }
    return false;
  }

  public String createDepartment(EnterpriseDirectoryDepartment external, String parentId) {
    var node = new OrganizationNodeEntity(); node.setName(external.name()); node.setParent(parentId);
    return organization.createDepartment(node, true);
  }

  public void updateDepartment(String id, EnterpriseDirectoryDepartment external, String parentId) {
    var patch = new OrganizationNodeEntity(); patch.setName(external.name()); patch.setParent(parentId);
    organization.updateNode(id, patch);
  }

  public String createMember(EnterpriseDirectoryMember external, DirectorySyncScope scope) {
    return organization.createEmployee(memberPatch(external, scope), null);
  }

  public void updateMember(String id, EnterpriseDirectoryMember external, DirectorySyncScope scope) {
    organization.updateEmployee(id, memberPatch(external, scope));
  }

  private EmployeeEntity memberPatch(EnterpriseDirectoryMember external, DirectorySyncScope scope) {
    var member = new EmployeeEntity(); member.setName(external.name());
    if (Boolean.TRUE.equals(scope.getSyncMobile())) { member.setMobile(external.mobile()); }
    if (Boolean.TRUE.equals(scope.getSyncEmail())) { member.setEmail(external.email()); }
    return member;
  }

  public List<String> appointments(String employeeId, EnterpriseDirectoryMember external,
      Map<String, String> departments, List<String> ownedIds) {
    var wanted = new LinkedHashMap<String, AppointmentType>();
    for (String externalId : external.departmentExternalIds()) {
      String localId = departments.get(externalId);
      if (localId == null) { throw new ValidationException("成员部门未关联或超出同步范围，请先处理部门关系"); }
      wanted.put(localId, externalId.equals(external.primaryDepartmentExternalId())
          ? AppointmentType.PRIMARY : AppointmentType.SECONDARY);
    }
    if (external.primaryDepartmentExternalId() == null && !wanted.isEmpty()) {
      throw new ValidationException("外部未明确主部门，请先在预览中确认主部门");
    }
    var current = appointments.findCurrentAppointments(employeeId);
    if (wanted.containsValue(AppointmentType.PRIMARY) && current.stream().anyMatch(item ->
        "PRIMARY".equals(item.getType()) && !ownedIds.contains(item.getId()))) {
      throw new ValidationException("员工已有本地主岗，请管理员先处理，目录同步不会覆盖本地任职");
    }
    var history = new ArrayList<>(ownedIds);
    for (String id : ownedIds) {
      var appointment = appointments.findById(id).orElseThrow(() -> new ValidationException("同步来源任职记录不存在"));
      if (!employeeId.equals(appointment.getEmployee())) { throw new ValidationException("同步任职归属不一致"); }
      var position = positions.findById(appointment.getPosition()).orElseThrow(() -> new ValidationException("同步岗位不存在"));
      var type = wanted.get(position.getParent());
      if (appointment.getEndDate() == null && type != null && type.name().equals(appointment.getType())) {
        wanted.remove(position.getParent());
      } else if (appointment.getEndDate() == null) { endAppointment(id); }
    }
    for (var entry : wanted.entrySet()) {
      var request = new CreateAppointmentRequest(); request.setEmployeeId(employeeId);
      request.setPositionId(positions.getOrCreateDefaultPosition(entry.getKey()).getId());
      request.setType(entry.getValue()); request.setStartDate(LocalDate.now());
      history.add(organization.createAppointment(request));
    }
    return List.copyOf(history);
  }

  public void endAppointments(List<String> ids) {
    ids.forEach(id -> appointments.findById(id).filter(item -> item.getEndDate() == null)
        .ifPresent(item -> endAppointment(item.getId())));
  }

  private void endAppointment(String id) {
    if (!Boolean.TRUE.equals(appointments.endAppointment(id))) {
      throw new com.ouroboros.exception.BusinessException("结束同步来源任职失败，保留关联等待重试");
    }
  }

  public List<Map<String, String>> candidates(String type, String name) {
    if ("MEMBER".equals(type)) {
      return employees.findAll().stream().filter(item -> name.equals(item.getName()))
          .map(item -> Map.of("id", item.getId(), "name", item.getName())).toList();
    }
    return nodes.findAllDepartments().stream().filter(item -> name.equals(item.getName()))
        .map(item -> Map.of("id", item.getId(), "name", item.getName())).toList();
  }

  public List<Map<String, String>> options(String type) {
    if ("MEMBER".equals(type)) {
      return employees.findAll().stream().map(item -> Map.of("value", item.getId(), "label", item.getName())).toList();
    }
    var all = "COMPANY".equals(type) ? nodes.findAllCompanies() : nodes.findAllDepartments();
    return all.stream().map(item -> Map.of("value", item.getId(), "label", item.getName())).toList();
  }

  public Object baseline(DirectoryBinding binding) {
    if ("MEMBER".equals(binding.getObjectType())) {
      var employee = employee(binding.getLocalId());
      var result = new LinkedHashMap<String, Object>(); result.put("employee", employee);
      result.put("appointments", appointments.findByEmployee(employee.getId())); return result;
    }
    return node(binding.getLocalId());
  }

  public static List<String> ownedAppointments(DirectoryBinding binding) {
    if (binding.getAppointmentIds() == null) { return List.of(); }
    var raw = Json.tryToBean(String[].class, binding.getAppointmentIds())
        .getOrElseThrow(error -> new com.ouroboros.exception.BusinessException("同步任职关系格式无效", error));
    return List.of(raw);
  }
}
