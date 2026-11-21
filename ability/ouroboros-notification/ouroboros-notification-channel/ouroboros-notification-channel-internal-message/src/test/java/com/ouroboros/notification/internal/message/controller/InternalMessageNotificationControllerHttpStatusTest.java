package com.ouroboros.notification.internal.message.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.internal.message.service.NotificationInternalMessageService;
import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalMessageNotificationControllerHttpStatusTest {

  @AfterEach
  void tearDown() {
    OrganizationContextHolder.clear();
  }

  @Test
  void markReadShouldReturnHttp404WhenMessageCannotBeUpdated() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));

    NotificationInternalMessageService service = mock(NotificationInternalMessageService.class);
    when(service.markReadById("emp-1", "missing")).thenReturn(false);

    InternalMessageNotificationController controller = new InternalMessageNotificationController();
    ReflectionTestUtils.setField(controller, "service", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(patch("/api/notification/missing/read"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("站内信不存在、已读或不属于当前用户"));
  }

  @Test
  void markReadShouldReturnHttp204WhenMessageMarkedSuccessfully() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));

    NotificationInternalMessageService service = mock(NotificationInternalMessageService.class);
    when(service.markReadById("emp-1", "message-1")).thenReturn(true);

    InternalMessageNotificationController controller = new InternalMessageNotificationController();
    ReflectionTestUtils.setField(controller, "service", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(patch("/api/notification/message-1/read"))
        .andExpect(status().isNoContent());
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }

  private static class TestOrganizationContext implements OrganizationContext {
    private final Employee employee;

    private TestOrganizationContext(String employeeId) {
      this.employee = new TestEmployee(employeeId);
    }

    @Override
    public Optional<Employee> getEmployee() {
      return Optional.of(employee);
    }

    @Override
    public Optional<Department> getDepartment() {
      return Optional.empty();
    }

    @Override
    public Optional<Company> getCompany() {
      return Optional.empty();
    }

    @Override
    public List<OrganizationNode> getOrgChain() {
      return Collections.emptyList();
    }
  }

  private static class TestEmployee implements Employee {
    private final String id;

    private TestEmployee(String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return "tester";
    }

    @Override
    public String getGender() {
      return null;
    }

    @Override
    public String getMobile() {
      return null;
    }

    @Override
    public String getEmail() {
      return null;
    }

    @Override
    public String getBirthday() {
      return null;
    }

    @Override
    public Company getCompanyDetail() {
      return null;
    }
  }
}
