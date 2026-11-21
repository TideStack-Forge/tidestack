package com.ouroboros.organizationstructure.service.impl;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.entity.PositionEntity;
import com.ouroboros.organizationstructure.service.AdminLevelService;
import com.ouroboros.organizationstructure.service.AppointmentManageService;
import com.ouroboros.organizationstructure.service.EmployeeManageService;
import com.ouroboros.organizationstructure.service.PositionManageService;
import com.ouroboros.organizationstructure.service.StructureManageService;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.service.UserSubjectService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrganizationFacadeServiceImplStatusTest {

  @Test
  void updateEmployeeShouldThrowBusinessExceptionWith404WhenEmployeeIsMissing() {
    EmployeeManageService employeeManageService = mock(EmployeeManageService.class);
    when(employeeManageService.updateById("missing", new EmployeeEntity())).thenReturn(false);

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        employeeManageService,
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateEmployee("missing", new EmployeeEntity()));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("员工不存在", exception.getMessage());
  }

  @Test
  void deleteEmployeeShouldThrowBusinessExceptionWith404WhenEmployeeIsMissing() {
    EmployeeManageService employeeManageService = mock(EmployeeManageService.class);
    when(employeeManageService.deleteById("missing")).thenReturn(false);

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        employeeManageService,
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.deleteEmployee("missing"));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("员工不存在", exception.getMessage());
  }

  @Test
  void transferToPositionShouldThrowBusinessExceptionWith404WhenEmployeeIsMissing() {
    PositionManageService positionManageService = mock(PositionManageService.class);
    AppointmentManageService appointmentManageService = mock(AppointmentManageService.class);
    EmployeeManageService employeeManageService = mock(EmployeeManageService.class);
    when(employeeManageService.findById("missing")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        positionManageService,
        appointmentManageService,
        employeeManageService,
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.transferToPosition("missing", "position-1"));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("员工不存在", exception.getMessage());
    verifyNoInteractions(appointmentManageService);
  }

  @Test
  void transferToPositionShouldThrowBusinessExceptionWith404WhenPositionIsMissing() {
    PositionManageService positionManageService = mock(PositionManageService.class);
    AppointmentManageService appointmentManageService = mock(AppointmentManageService.class);
    EmployeeManageService employeeManageService = mock(EmployeeManageService.class);
    when(employeeManageService.findById("employee-1")).thenReturn(Optional.of(new EmployeeEntity()));
    when(positionManageService.findById("missing-position")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        positionManageService,
        appointmentManageService,
        employeeManageService,
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.transferToPosition("employee-1", "missing-position"));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("岗位不存在", exception.getMessage());
    verifyNoInteractions(appointmentManageService);
  }

  @Test
  void linkEmployeeUserShouldThrowBusinessExceptionWith404WhenEmployeeIsMissing() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    when(employeeAggregator.getById("missing")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        employeeAggregator,
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.linkEmployeeUser("missing", "user-1"));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("员工不存在", exception.getMessage());
  }

  @Test
  void linkEmployeeUserShouldThrowBusinessExceptionWith404WhenUserIsMissing() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(employeeAggregator.getById("employee-1")).thenReturn(Optional.of(mock(com.ouroboros.organizationstructure.Employee.class)));
    when(userCenter.getUser("missing-user")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        employeeAggregator,
        userCenter);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.linkEmployeeUser("employee-1", "missing-user"));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("用户不存在", exception.getMessage());
  }

  @Test
  void linkEmployeeUserShouldThrowBusinessExceptionWith409WhenUserAlreadyBoundToEmployee() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    UserCenter userCenter = mock(UserCenter.class);
    UserSubjectCenter userSubjectCenter = mock(UserSubjectCenter.class);
    com.ouroboros.security.rbac.UserSubject existingSubject = mock(com.ouroboros.security.rbac.UserSubject.class);
    com.ouroboros.organizationstructure.Employee existingEmployee = mock(com.ouroboros.organizationstructure.Employee.class);
    when(employeeAggregator.getById("employee-1")).thenReturn(Optional.of(mock(com.ouroboros.organizationstructure.Employee.class)));
    when(userCenter.getUser("user-1")).thenReturn(Optional.of(mock(com.ouroboros.security.rbac.User.class)));
    when(userSubjectCenter.getByUser("user-1", "employee")).thenReturn(Optional.of(existingSubject));
    when(existingSubject.getSubjectId()).thenReturn("employee-2");
    when(employeeAggregator.getById("employee-2")).thenReturn(Optional.of(existingEmployee));
    when(existingEmployee.getName()).thenReturn("Alice");

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        userSubjectCenter,
        employeeAggregator,
        userCenter);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.linkEmployeeUser("employee-1", "user-1"));

    assertEquals(Integer.valueOf(409), exception.getErrorCode());
    assertEquals("用户已关联员工：Alice，无法重复关联", exception.getMessage());
  }

  @Test
  void linkEmployeeUserShouldThrowBusinessExceptionWith409WhenEmployeeAlreadyBoundToUser() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    UserCenter userCenter = mock(UserCenter.class);
    UserSubjectCenter userSubjectCenter = mock(UserSubjectCenter.class);
    com.ouroboros.security.rbac.UserSubject existingSubject = mock(com.ouroboros.security.rbac.UserSubject.class);
    when(employeeAggregator.getById("employee-1")).thenReturn(Optional.of(mock(com.ouroboros.organizationstructure.Employee.class)));
    when(userCenter.getUser("user-1")).thenReturn(Optional.of(mock(com.ouroboros.security.rbac.User.class)));
    when(userSubjectCenter.getByUser("user-1", "employee")).thenReturn(Optional.empty());
    when(userSubjectCenter.getBySubject("employee", "employee-1")).thenReturn(Optional.of(existingSubject));
    when(existingSubject.getUserIdentity()).thenReturn("bound-user");
    when(userCenter.getUser("bound-user")).thenReturn(Optional.of(mock(com.ouroboros.security.rbac.User.class)));

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        userSubjectCenter,
        employeeAggregator,
        userCenter);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.linkEmployeeUser("employee-1", "user-1"));

    assertEquals(Integer.valueOf(409), exception.getErrorCode());
    assertEquals("员工已关联用户,无法重复关联", exception.getMessage());
  }

  @Test
  void unlinkEmployeeUserShouldThrowBusinessExceptionWith404WhenEmployeeIsMissing() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    UserSubjectService userSubjectService = mock(UserSubjectService.class);
    when(employeeAggregator.getById("missing")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        userSubjectService,
        mock(UserSubjectCenter.class),
        employeeAggregator,
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.unlinkEmployeeUser("missing"));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("员工不存在", exception.getMessage());
    verifyNoInteractions(userSubjectService);
  }

  @Test
  void unlinkEmployeeUserShouldDoNothingWhenBindingIsAbsent() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    UserSubjectService userSubjectService = mock(UserSubjectService.class);
    when(employeeAggregator.getById("employee-1")).thenReturn(Optional.of(mock(com.ouroboros.organizationstructure.Employee.class)));
    when(userSubjectService.findBySubject("employee", "employee-1")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        userSubjectService,
        mock(UserSubjectCenter.class),
        employeeAggregator,
        mock(UserCenter.class));

    service.unlinkEmployeeUser("employee-1");

    verify(userSubjectService, never()).deleteByUserAndSubjectType(anyString(), anyString());
  }

  @Test
  void linkEmployeeUserShouldCleanupStaleEmployeeBindingWhenBoundUserIsMissing() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    UserCenter userCenter = mock(UserCenter.class);
    UserSubjectCenter userSubjectCenter = mock(UserSubjectCenter.class);
    UserSubjectService userSubjectService = mock(UserSubjectService.class);
    com.ouroboros.security.rbac.UserSubject staleSubject = mock(com.ouroboros.security.rbac.UserSubject.class);
    when(employeeAggregator.getById("employee-1")).thenReturn(Optional.of(mock(com.ouroboros.organizationstructure.Employee.class)));
    when(userCenter.getUser("new-user")).thenReturn(Optional.of(mock(com.ouroboros.security.rbac.User.class)));
    when(userSubjectCenter.getByUser("new-user", "employee")).thenReturn(Optional.empty());
    when(userSubjectCenter.getBySubject("employee", "employee-1")).thenReturn(Optional.of(staleSubject));
    when(staleSubject.getUserIdentity()).thenReturn("stale-user");
    when(userCenter.getUser("stale-user")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        userSubjectService,
        userSubjectCenter,
        employeeAggregator,
        userCenter);

    service.linkEmployeeUser("employee-1", "new-user");

    verify(userSubjectService).deleteByUserAndSubjectType("stale-user", "employee");
    verify(userSubjectService).create("new-user", "employee", "employee-1");
  }

  @Test
  void linkEmployeeUserShouldCleanupStaleUserBindingWhenBoundEmployeeIsMissing() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    UserCenter userCenter = mock(UserCenter.class);
    UserSubjectCenter userSubjectCenter = mock(UserSubjectCenter.class);
    UserSubjectService userSubjectService = mock(UserSubjectService.class);
    com.ouroboros.security.rbac.UserSubject staleSubject = mock(com.ouroboros.security.rbac.UserSubject.class);
    when(employeeAggregator.getById("employee-1")).thenReturn(Optional.of(mock(com.ouroboros.organizationstructure.Employee.class)));
    when(userCenter.getUser("new-user")).thenReturn(Optional.of(mock(com.ouroboros.security.rbac.User.class)));
    when(userSubjectCenter.getByUser("new-user", "employee")).thenReturn(Optional.of(staleSubject));
    when(staleSubject.getSubjectId()).thenReturn("missing-employee");
    when(employeeAggregator.getById("missing-employee")).thenReturn(Optional.empty());
    when(userSubjectCenter.getBySubject("employee", "employee-1")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        userSubjectService,
        userSubjectCenter,
        employeeAggregator,
        userCenter);

    service.linkEmployeeUser("employee-1", "new-user");

    verify(userSubjectService).deleteByUserAndSubjectType("new-user", "employee");
    verify(userSubjectService).create("new-user", "employee", "employee-1");
  }

  @Test
  void createCompanyShouldValidateAdminLevelReferenceWhenPresent() throws Exception {
    StructureManageService structureManageService = mock(StructureManageService.class);
    AdminLevelService adminLevelService = mock(AdminLevelService.class);
    OrganizationNodeEntity company = new OrganizationNodeEntity();
    company.setType("company");
    company.setAdminLevel("GROUP");
    when(structureManageService.addCompany(org.mockito.ArgumentMatchers.any())).thenReturn(company);

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        structureManageService,
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(PositionDefinitionCenter.class),
        mock(UserCenter.class),
        adminLevelService);

    service.createCompany(company);

    verify(adminLevelService).validateAdminLevelReference("GROUP", "company");
  }

  @Test
  void createDepartmentShouldValidateAdminLevelReferenceWhenPresent() throws Exception {
    StructureManageService structureManageService = mock(StructureManageService.class);
    AdminLevelService adminLevelService = mock(AdminLevelService.class);
    OrganizationNodeEntity department = new OrganizationNodeEntity();
    department.setType("department");
    department.setAdminLevel("DEPT-L1");
    when(structureManageService.addDepartment(org.mockito.ArgumentMatchers.any())).thenReturn(department);

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        structureManageService,
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(PositionDefinitionCenter.class),
        mock(UserCenter.class),
        adminLevelService);

    service.createDepartment(department, false);

    verify(adminLevelService).validateAdminLevelReference("DEPT-L1", "department");
  }

  @Test
  void updateNodeShouldSkipValidationWhenAdminLevelDoesNotChange() throws Exception {
    StructureManageService structureManageService = mock(StructureManageService.class);
    AdminLevelService adminLevelService = mock(AdminLevelService.class);
    OrganizationNodeEntity current = new OrganizationNodeEntity();
    current.setId("node-1");
    current.setType("company");
    current.setAdminLevel("GROUP");
    OrganizationNodeEntity updated = new OrganizationNodeEntity();
    updated.setAdminLevel("GROUP");
    when(structureManageService.findById("node-1")).thenReturn(Optional.of(current));

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        structureManageService,
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(PositionDefinitionCenter.class),
        mock(UserCenter.class),
        adminLevelService);

    service.updateNode("node-1", updated);

    verify(adminLevelService, never()).validateAdminLevelReference(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void updateNodeShouldValidateWhenAdminLevelChanges() throws Exception {
    StructureManageService structureManageService = mock(StructureManageService.class);
    AdminLevelService adminLevelService = mock(AdminLevelService.class);
    OrganizationNodeEntity current = new OrganizationNodeEntity();
    current.setId("node-1");
    current.setType("company");
    current.setAdminLevel("GROUP");
    OrganizationNodeEntity updated = new OrganizationNodeEntity();
    updated.setAdminLevel("HQ");
    when(structureManageService.findById("node-1")).thenReturn(Optional.of(current));

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        structureManageService,
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(PositionDefinitionCenter.class),
        mock(UserCenter.class),
        adminLevelService);

    service.updateNode("node-1", updated);

    verify(adminLevelService).validateAdminLevelReference("HQ", "company");
  }

  @Test
  void updateNodeShouldThrowBusinessExceptionWith404WhenNodeIsMissing() {
    StructureManageService structureManageService = mock(StructureManageService.class);
    PositionManageService positionManageService = mock(PositionManageService.class);
    when(structureManageService.findById("missing")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        structureManageService,
        positionManageService,
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updateNode("missing", new OrganizationNodeEntity()));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("组织节点不存在", exception.getMessage());
    verify(structureManageService, never()).update(anyString(), any());
  }

  @Test
  void updatePositionShouldThrowBusinessExceptionWith404WhenPositionIsMissing() {
    StructureManageService structureManageService = mock(StructureManageService.class);
    PositionManageService positionManageService = mock(PositionManageService.class);
    when(positionManageService.findById("missing")).thenReturn(Optional.empty());

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        structureManageService,
        positionManageService,
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updatePosition("missing", new PositionEntity()));

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("岗位不存在", exception.getMessage());
    verify(positionManageService, never()).update(anyString(), any());
  }

  @Test
  void updatePositionShouldRejectCatalogBackedMetadataChanges() {
    StructureManageService structureManageService = mock(StructureManageService.class);
    PositionManageService positionManageService = mock(PositionManageService.class);
    PositionEntity existing = new PositionEntity();
    existing.setId("position-1");
    existing.setPositionKey("member");
    when(positionManageService.findById("position-1")).thenReturn(Optional.of(existing));

    PositionEntity update = new PositionEntity();
    update.setPositionName("自定义成员");
    update.setDescription("修改后的说明");
    update.setHeadcount(10);

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        structureManageService,
        positionManageService,
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.updatePosition("position-1", update));

    assertEquals(Integer.valueOf(400), exception.getErrorCode());
    assertEquals("全局岗位定义的名称和描述请在岗位目录中维护", exception.getMessage());
    verify(positionManageService, never()).update(anyString(), any());
  }

  @Test
  void updatePositionShouldAllowCatalogBackedHeadcountChanges() {
    PositionManageService positionManageService = mock(PositionManageService.class);
    PositionEntity existing = new PositionEntity();
    existing.setId("position-1");
    existing.setPositionKey("member");
    existing.setPositionName("成员");
    when(positionManageService.findById("position-1")).thenReturn(Optional.of(existing));

    PositionEntity update = new PositionEntity();
    update.setHeadcount(10);

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        positionManageService,
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    service.updatePosition("position-1", update);

    verify(positionManageService).update("position-1", update);
  }

  @Test
  void createPositionShouldUseLegacyAddWhenNoDefinitionMatchesLegacyName() {
    PositionManageService positionManageService = mock(PositionManageService.class);
    PositionEntity created = new PositionEntity();
    created.setId("position-1");
    when(positionManageService.add(any())).thenReturn(created);

    OrganizationFacadeServiceImpl service = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        positionManageService,
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));

    PositionEntity request = new PositionEntity();
    request.setParent("dept-1");
    request.setPositionName("自定义岗位");

    String id = service.createPosition(request);

    assertEquals("position-1", id);
    verify(positionManageService).add(request);
    verify(positionManageService, never()).assignDefinitionToDepartment(anyString(), anyString(), anyString(), any(), anyString());
  }
}
