package com.ouroboros.organizationstructure.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.AppointmentType;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.Position;
import com.ouroboros.organizationstructure.entity.PositionEntity;
import com.ouroboros.organizationstructure.service.OrganizationFacadeService;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationManagementControllerHttpStatusTest {

  @Test
  void createPositionShouldReturnHttp400WhenDepartmentIdIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/positions")
            .contentType("application/json")
            .content("{\"name\":\"dev\",\"departmentId\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("部门ID不能为空"));
  }

  @Test
  void createPositionShouldReturnHttp400WhenNameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/positions")
            .contentType("application/json")
            .content("{\"name\":\"\",\"departmentId\":\"dept-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("岗位名称不能为空"));
  }

  @Test
  void createDepartmentShouldReturnHttp400WhenNameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/departments")
            .contentType("application/json")
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("部门名称不能为空"));
  }

  @Test
  void createCompanyShouldReturnHttp400WhenNameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/companies")
            .contentType("application/json")
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("公司名称不能为空"));
  }

  @Test
  void updateCompanyShouldReturnHttp400WhenNameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/companies/company-1")
            .contentType("application/json")
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("公司名称不能为空"));
  }

  @Test
  void updateDepartmentShouldReturnHttp400WhenNameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/departments/department-1")
            .contentType("application/json")
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("部门名称不能为空"));
  }

  @Test
  void updatePositionShouldReturnHttp400WhenNameIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/positions/position-1")
            .contentType("application/json")
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("岗位名称不能为空"));
  }

  @Test
  void getCompanyByIdShouldReturnHttp404WhenMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getCompanyById("missing")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/companies/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("公司不存在"));
  }

  @Test
  void getDepartmentByIdShouldReturnHttp404WhenMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getDepartmentById("missing")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/departments/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("部门不存在"));
  }

  @Test
  void getPositionByIdShouldReturnHttp404WhenMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getPositionById("missing")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/positions/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("岗位不存在"));
  }

  @Test
  void deleteCompanyShouldReturnHttp404WhenMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getCompanyById("missing")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(delete("/api/organization/companies/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("公司不存在"));
  }

  @Test
  void deleteDepartmentShouldReturnHttp404WhenMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getDepartmentById("missing")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(delete("/api/organization/departments/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("部门不存在"));
  }

  @Test
  void deletePositionShouldReturnHttp404WhenMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getPositionById("missing")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(delete("/api/organization/positions/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("岗位不存在"));
  }

  @Test
  void deleteAppointmentShouldReturnHttp404WhenMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getAppointmentById("missing")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(delete("/api/organization/appointments/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("任职不存在"));
  }

  @Test
  void createPositionShouldReturnHttp500WhenLookupFailsAfterCreate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    PositionDefinitionCenter positionDefinitionCenter = mock(PositionDefinitionCenter.class);
    PositionDefinition positionDefinition = mock(PositionDefinition.class);
    when(positionDefinitionCenter.getDefinition("member")).thenReturn(Optional.of(positionDefinition));
    when(positionDefinition.getKey()).thenReturn("member");
    when(positionDefinition.getName()).thenReturn("成员");
    when(manageFacade.createPosition(org.mockito.ArgumentMatchers.any())).thenReturn("position-1");
    when(queryFacade.getPositionById("position-1")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade, positionDefinitionCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/positions")
            .contentType("application/json")
            .content("{\"positionKey\":\"member\",\"name\":\"成员\",\"departmentId\":\"dept-1\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.msg").value("创建岗位后查询失败"));
  }

  @Test
  void createPositionShouldAllowLegacyFreeFormPositionWhenNoDefinitionMatches() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    PositionDefinitionCenter positionDefinitionCenter = mock(PositionDefinitionCenter.class);
    when(positionDefinitionCenter.getDefinition(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
    when(positionDefinitionCenter.getDefinitions()).thenReturn(Collections.emptyList());
    when(manageFacade.createPosition(org.mockito.ArgumentMatchers.any())).thenReturn("position-1");
    when(queryFacade.getPositionById("position-1")).thenReturn(Optional.of(new Position() {
      @Override
      public String getPositionName() {
        return "自定义岗位";
      }

      @Override
      public String getLevel() {
        return null;
      }

      @Override
      public String getDescription() {
        return null;
      }

      @Override
      public Integer getHeadcount() {
        return null;
      }

      @Override
      public Integer getActualCount() {
        return 0;
      }

      @Override
      public String getId() {
        return "position-1";
      }

      @Override
      public String getLeaderEmployee() {
        return null;
      }

      @Override
      public Employee getLeaderEmployeeDetail() {
        return null;
      }

      @Override
      public String getParent() {
        return "dept-1";
      }

      @Override
      public com.ouroboros.organizationstructure.OrganizationNode getParentDetail() {
        return null;
      }
    }));

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade, positionDefinitionCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/positions")
            .contentType("application/json")
            .content("{\"name\":\"自定义岗位\",\"departmentId\":\"dept-1\"}"))
        .andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(PositionEntity.class);
    verify(manageFacade).createPosition(captor.capture());
    assertNull(captor.getValue().getPositionKey());
    assertEquals("自定义岗位", captor.getValue().getPositionName());
  }

  @Test
  void createPositionShouldReturnHttp400WhenExplicitPositionKeyIsInvalid() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    PositionDefinitionCenter positionDefinitionCenter = mock(PositionDefinitionCenter.class);
    when(positionDefinitionCenter.getDefinition("missing-key")).thenReturn(Optional.empty());
    when(positionDefinitionCenter.getDefinitions()).thenReturn(Collections.emptyList());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade, positionDefinitionCenter);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/positions")
            .contentType("application/json")
            .content("{\"name\":\"自定义岗位\",\"positionKey\":\"missing-key\",\"departmentId\":\"dept-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("岗位定义不存在"));

    verifyNoInteractions(manageFacade);
  }

  @Test
  void createCompanyShouldReturnHttp500WhenLookupFailsAfterCreate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(manageFacade.createCompany(org.mockito.ArgumentMatchers.any())).thenReturn("company-1");
    when(queryFacade.getCompanyById("company-1")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/companies")
            .contentType("application/json")
            .content("{\"name\":\"Acme\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.msg").value("创建公司后查询失败"));
  }

  @Test
  void createDepartmentShouldReturnHttp500WhenLookupFailsAfterCreate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(manageFacade.createDepartment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn("department-1");
    when(queryFacade.getDepartmentById("department-1")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/departments")
            .contentType("application/json")
            .content("{\"name\":\"Engineering\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.msg").value("创建部门后查询失败"));
  }

  @Test
  void createAppointmentShouldReturnHttp500WhenLookupFailsAfterCreate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(manageFacade.createAppointment(org.mockito.ArgumentMatchers.any())).thenReturn("appointment-1");
    when(queryFacade.getAppointmentById("appointment-1")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/appointments")
            .contentType("application/json")
            .content("{\"employeeId\":\"emp-1\",\"positionId\":\"pos-1\",\"startDate\":\"2026-03-29\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.msg").value("创建任职后查询失败"));
  }

  @Test
  void updateCompanyShouldReturnHttp404WhenFacadeSignalsMissingNode() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    doThrow(new BusinessException(404, "组织节点不存在"))
        .when(manageFacade)
        .updateNode(org.mockito.ArgumentMatchers.eq("missing"), org.mockito.ArgumentMatchers.any());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/companies/missing")
            .contentType("application/json")
            .content("{\"name\":\"Acme\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("组织节点不存在"));
  }

  @Test
  void updateCompanyShouldReturnHttp404WhenLookupFailsAfterUpdate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getCompanyById("company-1")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/companies/company-1")
            .contentType("application/json")
            .content("{\"name\":\"Acme\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("公司不存在"));
  }

  @Test
  void updateDepartmentShouldReturnHttp404WhenFacadeSignalsMissingNode() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    doThrow(new BusinessException(404, "组织节点不存在"))
        .when(manageFacade)
        .updateNode(org.mockito.ArgumentMatchers.eq("missing"), org.mockito.ArgumentMatchers.any());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/departments/missing")
            .contentType("application/json")
            .content("{\"name\":\"R&D\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("组织节点不存在"));
  }

  @Test
  void updateDepartmentShouldReturnHttp404WhenLookupFailsAfterUpdate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getDepartmentById("department-1")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/departments/department-1")
            .contentType("application/json")
            .content("{\"name\":\"R&D\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("更新后部门不存在"));
  }

  @Test
  void updatePositionShouldReturnHttp404WhenFacadeSignalsMissingPosition() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    doThrow(new BusinessException(404, "岗位不存在"))
        .when(manageFacade)
        .updatePosition(org.mockito.ArgumentMatchers.eq("missing"), org.mockito.ArgumentMatchers.any());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/positions/missing")
            .contentType("application/json")
            .content("{\"name\":\"Architect\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("岗位不存在"));
  }

  @Test
  void updatePositionShouldReturnHttp404WhenLookupFailsAfterUpdate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getPositionById("position-1")).thenReturn(Optional.empty());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/organization/positions/position-1")
            .contentType("application/json")
            .content("{\"name\":\"Architect\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("更新后岗位不存在"));
  }

  @Test
  void assignPositionShouldReturnHttp400WhenPrimaryAlreadyExistsOnTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.PRIMARY, AppointmentType.PRIMARY, "该员工已在此岗位担任主岗");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenSecondaryRequestedOnPrimaryTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.PRIMARY, AppointmentType.SECONDARY, "该员工已在此岗位担任主岗，无法再设置副岗");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenActingRequestedOnPrimaryTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.PRIMARY, AppointmentType.ACTING, "该员工已在此岗位担任主岗，无法再设置代理");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenPrimaryRequestedOnSecondaryTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.SECONDARY, AppointmentType.PRIMARY, "该员工已在此岗位担任副岗，请先删除副岗任职后再设置主岗");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenSecondaryAlreadyExistsOnTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.SECONDARY, AppointmentType.SECONDARY, "该员工已在此岗位担任副岗");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenActingRequestedOnSecondaryTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.SECONDARY, AppointmentType.ACTING, "该员工已在此岗位担任副岗，无法再设置代理");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenPrimaryRequestedOnActingTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.ACTING, AppointmentType.PRIMARY, "该员工已在此岗位担任代理，请先删除代理任职后再设置主岗");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenSecondaryRequestedOnActingTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.ACTING, AppointmentType.SECONDARY, "该员工已在此岗位担任代理，无法再设置副岗");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenActingAlreadyExistsOnTargetPosition() throws Exception {
    assertAssignPositionConflict(AppointmentType.ACTING, AppointmentType.ACTING, "该员工已在此岗位担任代理");
  }

  @Test
  void assignPositionShouldReturnHttp400WhenPositionIdIsBlank() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getCurrentAppointmentsByEmployee("employee-1")).thenReturn(Collections.emptyList());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/employees/employee-1/assign-position")
            .contentType("application/json")
            .content("{\"positionId\":\"\",\"appointmentType\":\"PRIMARY\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("岗位ID不能为空"));
  }

  @Test
  void assignPositionShouldReturnHttp400WhenAppointmentTypeIsMissing() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getCurrentAppointmentsByEmployee("employee-1")).thenReturn(Collections.emptyList());

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/employees/employee-1/assign-position")
            .contentType("application/json")
            .content("{\"positionId\":\"position-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("任职类型不能为空"));
  }

  @Test
  void assignPositionShouldStopBeforeCreateWhenPrimaryCleanupFails() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    Appointment existingPrimary = mock(Appointment.class);
    when(existingPrimary.isCurrent()).thenReturn(true);
    when(existingPrimary.isPrimary()).thenReturn(true);
    when(existingPrimary.getPosition()).thenReturn("old-position");
    when(existingPrimary.getId()).thenReturn("appointment-1");
    when(queryFacade.getCurrentAppointmentsByEmployee("employee-1")).thenReturn(Collections.singletonList(existingPrimary));
    doThrow(new BusinessException(404, "任职不存在"))
        .when(manageFacade)
        .removeAppointment("appointment-1");

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/employees/employee-1/assign-position")
            .contentType("application/json")
            .content("{\"positionId\":\"new-position\",\"appointmentType\":\"PRIMARY\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("任职不存在"));

    verify(manageFacade, never()).createAppointment(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void assignPositionShouldOnlyRemoveOtherCurrentPrimaryBeforeCreate() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    Appointment currentPrimary = mock(Appointment.class);
    when(currentPrimary.isCurrent()).thenReturn(true);
    when(currentPrimary.isPrimary()).thenReturn(true);
    when(currentPrimary.getPosition()).thenReturn("old-primary-position");
    when(currentPrimary.getId()).thenReturn("primary-1");

    Appointment currentSecondary = mock(Appointment.class);
    when(currentSecondary.isCurrent()).thenReturn(true);
    when(currentSecondary.isPrimary()).thenReturn(false);
    when(currentSecondary.getPosition()).thenReturn("secondary-position");
    when(currentSecondary.getId()).thenReturn("secondary-1");

    Appointment inactivePrimary = mock(Appointment.class);
    when(inactivePrimary.isCurrent()).thenReturn(false);
    when(inactivePrimary.isPrimary()).thenReturn(true);
    when(inactivePrimary.getPosition()).thenReturn("inactive-primary-position");
    when(inactivePrimary.getId()).thenReturn("inactive-1");

    when(queryFacade.getCurrentAppointmentsByEmployee("employee-1"))
        .thenReturn(Arrays.asList(currentPrimary, currentSecondary, inactivePrimary));
    when(manageFacade.createAppointment(org.mockito.ArgumentMatchers.any())).thenReturn("created-1");

    Appointment createdAppointment = mock(Appointment.class);
    when(createdAppointment.getId()).thenReturn("created-1");
    when(createdAppointment.getEmployee()).thenReturn("employee-1");
    when(createdAppointment.getPosition()).thenReturn("new-position");
    when(createdAppointment.getType()).thenReturn(AppointmentType.PRIMARY);
    when(createdAppointment.getStartDate()).thenReturn(LocalDate.of(2026, 3, 29));
    when(createdAppointment.getEndDate()).thenReturn(null);
    when(queryFacade.getAppointmentById("created-1")).thenReturn(Optional.of(createdAppointment));

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/employees/employee-1/assign-position")
            .contentType("application/json")
            .content("{\"positionId\":\"new-position\",\"appointmentType\":\"PRIMARY\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("created-1"))
        .andExpect(jsonPath("$.data.employee").value("employee-1"))
        .andExpect(jsonPath("$.data.position").value("new-position"))
        .andExpect(jsonPath("$.data.type").value("PRIMARY"));

    verify(manageFacade).removeAppointment("primary-1");
    verify(manageFacade, never()).removeAppointment("secondary-1");
    verify(manageFacade, never()).removeAppointment("inactive-1");
    verify(manageFacade).createAppointment(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void assignPositionShouldIgnoreAppointmentsEndedTodayWhenCheckingConflicts() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);

    Appointment endedTodayPrimary = appointment(
        "ended-1",
        "employee-1",
        "position-1",
        AppointmentType.PRIMARY,
        LocalDate.of(2026, 3, 1),
        LocalDate.now());
    when(queryFacade.getAppointmentsByEmployee("employee-1"))
        .thenReturn(Collections.singletonList(endedTodayPrimary));
    when(queryFacade.getCurrentAppointmentsByEmployee("employee-1"))
        .thenReturn(Collections.emptyList());
    when(manageFacade.createAppointment(org.mockito.ArgumentMatchers.any())).thenReturn("created-1");

    Appointment createdAppointment = appointment(
        "created-1",
        "employee-1",
        "position-1",
        AppointmentType.PRIMARY,
        LocalDate.of(2026, 3, 29),
        null);
    when(queryFacade.getAppointmentById("created-1")).thenReturn(Optional.of(createdAppointment));

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/employees/employee-1/assign-position")
            .contentType("application/json")
            .content("{\"positionId\":\"position-1\",\"appointmentType\":\"PRIMARY\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("created-1"))
        .andExpect(jsonPath("$.data.employee").value("employee-1"))
        .andExpect(jsonPath("$.data.position").value("position-1"))
        .andExpect(jsonPath("$.data.type").value("PRIMARY"));

    verify(manageFacade, never()).removeAppointment("ended-1");
    verify(manageFacade).createAppointment(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void assignPositionShouldPreserveCreateFailureStatusAndMessage() throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getCurrentAppointmentsByEmployee("employee-1")).thenReturn(Collections.emptyList());

    OrganizationManagementController controller = spy(new OrganizationManagementController(manageFacade, queryFacade));
    doReturn(WebApiResult.error(409, "任职创建失败: 岗位已关闭"))
        .when(controller)
        .createAppointment(org.mockito.ArgumentMatchers.any());

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/employees/employee-1/assign-position")
            .contentType("application/json")
            .content("{\"positionId\":\"new-position\",\"appointmentType\":\"SECONDARY\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.msg").value("任职创建失败: 岗位已关闭"));
  }

  private void assertAssignPositionConflict(AppointmentType existingType, AppointmentType requestType, String expectedMessage) throws Exception {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    Appointment existingAppointment = mock(Appointment.class);
    when(existingAppointment.isCurrent()).thenReturn(true);
    when(existingAppointment.getPosition()).thenReturn("position-1");
    when(existingAppointment.getType()).thenReturn(existingType);
    when(queryFacade.getCurrentAppointmentsByEmployee("employee-1")).thenReturn(Collections.singletonList(existingAppointment));

    OrganizationManagementController controller = new OrganizationManagementController(manageFacade, queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/organization/employees/employee-1/assign-position")
            .contentType("application/json")
            .content("{\"positionId\":\"position-1\",\"appointmentType\":\"" + requestType.name() + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value(expectedMessage));
  }

  private Appointment appointment(
      String id,
      String employeeId,
      String positionId,
      AppointmentType type,
      LocalDate startDate,
      LocalDate endDate) {
    return new Appointment() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public String getEmployee() {
        return employeeId;
      }

      @Override
      public Employee getEmployeeDetail() {
        return null;
      }

      @Override
      public String getPosition() {
        return positionId;
      }

      @Override
      public Position getPositionDetail() {
        return null;
      }

      @Override
      public AppointmentType getType() {
        return type;
      }

      @Override
      public LocalDate getStartDate() {
        return startDate;
      }

      @Override
      public LocalDate getEndDate() {
        return endDate;
      }
    };
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
