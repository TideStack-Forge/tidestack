package com.ouroboros.organizationstructure.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.dto.request.UpdateEmployeePositionRequest;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.service.OrganizationFacadeService;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.organizationstructure.service.AppointmentManageService;
import com.ouroboros.organizationstructure.service.EmployeeManageService;
import com.ouroboros.organizationstructure.service.PositionManageService;
import com.ouroboros.organizationstructure.service.StructureManageService;
import com.ouroboros.organizationstructure.service.impl.OrganizationFacadeServiceImpl;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.security.rbac.service.UserSubjectService;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmployeeControllerHttpStatusTest {

  @Test
  void updateShouldReturnHttp404WhenEmployeeIsMissing() throws Exception {
    EmployeeManageService employeeManageService = mock(EmployeeManageService.class);
    when(employeeManageService.updateById(org.mockito.ArgumentMatchers.eq("missing"), org.mockito.ArgumentMatchers.any(EmployeeEntity.class)))
        .thenReturn(false);

    OrganizationFacadeService manageFacade = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        employeeManageService,
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/missing")
            .contentType("application/json")
            .content("{\"name\":\"tester\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("员工不存在"));
  }

  @Test
  void deleteShouldReturnHttp404WhenEmployeeIsMissing() throws Exception {
    EmployeeManageService employeeManageService = mock(EmployeeManageService.class);
    when(employeeManageService.deleteById("missing")).thenReturn(false);

    OrganizationFacadeService manageFacade = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        employeeManageService,
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(delete("/api/organization/employees/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("员工不存在"));
  }

  @Test
  void getByIdShouldReturnHttp404WhenEmployeeIsMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(queryFacade.getEmployeeById("missing")).thenReturn(java.util.Optional.empty());

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/employees/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("员工不存在"));
  }

  @Test
  void getOrganizationChainShouldReturnHttp404WhenEmployeeIsMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(queryFacade.getEmployeeById("missing")).thenReturn(java.util.Optional.empty());

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/employees/missing/org-chain"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("员工不存在"));
  }

  @Test
  void getAppointmentsShouldReturnHttp404WhenEmployeeIsMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(queryFacade.getEmployeeById("missing")).thenReturn(java.util.Optional.empty());

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/employees/missing/appointments"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("员工不存在"));
  }

  @Test
  void updateEmployeePositionShouldReturnHttp400WhenPositionIdIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/position")
            .contentType("application/json")
            .content("{\"positionId\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("岗位ID不能为空"));
  }

  @Test
  void updateEmployeePositionShouldReturnHttp404WhenEmployeeIsMissing() throws Exception {
    PositionManageService positionManageService = mock(PositionManageService.class);
    AppointmentManageService appointmentManageService = mock(AppointmentManageService.class);
    EmployeeManageService employeeManageService = mock(EmployeeManageService.class);
    when(employeeManageService.findById("missing")).thenReturn(java.util.Optional.empty());

    OrganizationFacadeService manageFacade = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        positionManageService,
        appointmentManageService,
        employeeManageService,
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/missing/position")
            .contentType("application/json")
            .content("{\"positionId\":\"position-1\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("员工不存在"));
  }

  @Test
  void updateEmployeePositionShouldReturnHttp404WhenPositionIsMissing() throws Exception {
    PositionManageService positionManageService = mock(PositionManageService.class);
    AppointmentManageService appointmentManageService = mock(AppointmentManageService.class);
    EmployeeManageService employeeManageService = mock(EmployeeManageService.class);
    when(employeeManageService.findById("employee-1")).thenReturn(java.util.Optional.of(new EmployeeEntity()));
    when(positionManageService.findById("missing-position")).thenReturn(java.util.Optional.empty());

    OrganizationFacadeService manageFacade = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        positionManageService,
        appointmentManageService,
        employeeManageService,
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        mock(EmployeeAggregator.class),
        mock(UserCenter.class));
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/position")
            .contentType("application/json")
            .content("{\"positionId\":\"missing-position\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("岗位不存在"));
  }

  @Test
  void linkUserShouldReturnHttp404WhenFacadeSignalsMissingEmployee() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    org.mockito.Mockito.doThrow(new BusinessException(404, "员工不存在"))
        .when(manageFacade)
        .linkEmployeeUser("missing", "user-1");

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/missing/user")
            .contentType("application/json")
            .content("{\"mode\":\"link\",\"userId\":\"user-1\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("员工不存在"));
  }

  @Test
  void linkUserShouldReturnHttp404WhenFacadeSignalsMissingUser() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    org.mockito.Mockito.doThrow(new BusinessException(404, "用户不存在"))
        .when(manageFacade)
        .linkEmployeeUser("employee-1", "missing-user");

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"link\",\"userId\":\"missing-user\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("用户不存在"));
  }

  @Test
  void linkUserShouldReturnHttp409WhenFacadeSignalsUserAlreadyBound() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    org.mockito.Mockito.doThrow(new BusinessException(409, "用户已关联员工：Alice，无法重复关联"))
        .when(manageFacade)
        .linkEmployeeUser("employee-1", "user-1");

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"link\",\"userId\":\"user-1\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.msg").value("用户已关联员工：Alice，无法重复关联"));
  }

  @Test
  void linkUserShouldReturnHttp409WhenFacadeSignalsEmployeeAlreadyBound() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    org.mockito.Mockito.doThrow(new BusinessException(409, "员工已关联用户,无法重复关联"))
        .when(manageFacade)
        .linkEmployeeUser("employee-1", "user-1");

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"link\",\"userId\":\"user-1\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.msg").value("员工已关联用户,无法重复关联"));
  }

  @Test
  void unlinkUserShouldReturnHttp404WhenEmployeeIsMissing() throws Exception {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    when(employeeAggregator.getById("missing")).thenReturn(java.util.Optional.empty());

    OrganizationFacadeService manageFacade = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        mock(UserSubjectService.class),
        mock(UserSubjectCenter.class),
        employeeAggregator,
        mock(UserCenter.class));
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(delete("/api/organization/employees/missing/user"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("员工不存在"));
  }

  @Test
  void unlinkUserShouldReturnHttp200WhenBindingIsAbsent() throws Exception {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    UserSubjectService userSubjectService = mock(UserSubjectService.class);
    when(employeeAggregator.getById("employee-1")).thenReturn(java.util.Optional.of(mock(com.ouroboros.organizationstructure.Employee.class)));
    when(userSubjectService.findBySubject("employee", "employee-1")).thenReturn(java.util.Optional.empty());

    OrganizationFacadeService manageFacade = new OrganizationFacadeServiceImpl(
        mock(StructureManageService.class),
        mock(PositionManageService.class),
        mock(AppointmentManageService.class),
        mock(EmployeeManageService.class),
        userSubjectService,
        mock(UserSubjectCenter.class),
        employeeAggregator,
        mock(UserCenter.class));
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(delete("/api/organization/employees/employee-1/user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(userSubjectService, never()).deleteByUserAndSubjectType(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void linkUserShouldReturnHttp400WhenCreateModeUsernameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"create\",\"newUsername\":\"\",\"newPassword\":\"secret\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("用户名不能为空"));
  }

  @Test
  void linkUserShouldReturnHttp400WhenCreateModePasswordIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"create\",\"newUsername\":\"new-user\",\"newPassword\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("密码不能为空"));
  }

  @Test
  void linkUserShouldReturnHttp400WhenCreateModeUsernameAlreadyExists() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(userCenter.getUser("existing-user")).thenReturn(java.util.Optional.of(mock(User.class)));

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"create\",\"newUsername\":\"existing-user\",\"newPassword\":\"secret\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("用户名已存在: existing-user"));
  }

  @Test
  void linkUserShouldReturnHttp500WhenCreateModeUserCreationFails() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(userCenter.getUser("new-user")).thenReturn(java.util.Optional.empty());
    when(userService.add(any(UserEntity.class))).thenThrow(new RuntimeException("boom"));

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"create\",\"newUsername\":\"new-user\",\"newPassword\":\"secret\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.msg").value("创建用户失败: boom"));
  }

  @Test
  void linkUserShouldReturnHttp400WhenLinkModeUserIdIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"link\",\"userId\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("用户ID不能为空"));
  }

  @Test
  void linkUserShouldReturnHttp400WhenModeIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"\",\"userId\":\"user-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("用户关联模式不能为空"));
  }

  @Test
  void linkUserShouldReturnHttp400WhenModeIsUnsupported() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1/user")
            .contentType("application/json")
            .content("{\"mode\":\"noop\",\"userId\":\"user-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("无效的用户关联模式: noop"));
  }

  @Test
  void createShouldReturnHttp400WhenNameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/organization/employees")
            .contentType("application/json")
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("员工姓名不能为空"));
  }

  @Test
  void updateShouldReturnHttp400WhenNameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/employees/employee-1")
            .contentType("application/json")
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("员工姓名不能为空"));
  }

  @Test
  void createShouldReturnHttp400WhenNestedCreateUsernameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/organization/employees")
            .contentType("application/json")
            .content("{\"name\":\"tester\",\"user\":{\"mode\":\"create\",\"username\":\"\",\"password\":\"secret\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("用户名不能为空"));
  }

  @Test
  void createShouldReturnHttp400WhenNestedCreateUsernameAlreadyExists() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(userCenter.getUser("existing-user")).thenReturn(java.util.Optional.of(mock(User.class)));

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/organization/employees")
            .contentType("application/json")
            .content("{\"name\":\"tester\",\"user\":{\"mode\":\"create\",\"username\":\"existing-user\",\"password\":\"secret\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("用户名已存在: existing-user"));
  }

  @Test
  void createShouldReturnHttp500WhenNestedUserCreationFails() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(userCenter.getUser("new-user")).thenReturn(java.util.Optional.empty());
    when(userService.add(any(UserEntity.class))).thenThrow(new RuntimeException("boom"));

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/organization/employees")
            .contentType("application/json")
            .content("{\"name\":\"tester\",\"user\":{\"mode\":\"create\",\"username\":\"new-user\",\"password\":\"secret\"}}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.msg").value("创建用户失败: boom"));
  }

  @Test
  void createShouldReturnHttp404WhenLinkingExistingUserFailsAfterCreate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(manageFacade.createEmployee(any(EmployeeEntity.class), org.mockito.ArgumentMatchers.isNull())).thenReturn("employee-1");
    org.mockito.Mockito.doThrow(new BusinessException(404, "用户不存在"))
        .when(manageFacade)
        .linkEmployeeUser("employee-1", "missing-user");

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/organization/employees")
            .contentType("application/json")
            .content("{\"name\":\"tester\",\"user\":{\"mode\":\"link\",\"userId\":\"missing-user\"}}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("用户不存在"));
  }

  @Test
  void createShouldReturnHttp409WhenLinkingExistingUserConflictsAfterCreate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    when(manageFacade.createEmployee(any(EmployeeEntity.class), org.mockito.ArgumentMatchers.isNull())).thenReturn("employee-1");
    org.mockito.Mockito.doThrow(new BusinessException(409, "员工已关联用户,无法重复关联"))
        .when(manageFacade)
        .linkEmployeeUser("employee-1", "user-1");

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/organization/employees")
            .contentType("application/json")
            .content("{\"name\":\"tester\",\"user\":{\"mode\":\"link\",\"userId\":\"user-1\"}}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.msg").value("员工已关联用户,无法重复关联"));
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
