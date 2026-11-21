package com.ouroboros.notification.controller;

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
import com.ouroboros.notification.controller.req.ChannelBindingReq;
import com.ouroboros.notification.service.NotificationChannelEmployeeMapService;
import com.ouroboros.notification.service.model.NotificationChannelEmployeeMapModel;
import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationChannelControllerHttpStatusTest {

  @AfterEach
  void tearDown() {
    OrganizationContextHolder.clear();
  }

  @Test
  void bindShouldReturnHttp400WhenAddressIsEmpty() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationChannelController controller = new NotificationChannelController();
    ReflectionTestUtils.setField(controller, "service", mock(NotificationChannelEmployeeMapService.class));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/email/employee/bind")
            .contentType("application/json")
            .content("{\"address\":\"\",\"remarks\":\"home\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("address 不能为空"));
  }

  @Test
  void bindShouldReturnHttp400WhenAddressIsWhitespaceOnly() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationChannelController controller = new NotificationChannelController();
    ReflectionTestUtils.setField(controller, "service", mock(NotificationChannelEmployeeMapService.class));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/email/employee/bind")
            .contentType("application/json")
            .content("{\"address\":\"   \",\"remarks\":\"home\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("address 不能为空"));
  }

  @Test
  void bindShouldReturnHttp400WhenRemarksIsWhitespaceOnly() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationChannelController controller = new NotificationChannelController();
    ReflectionTestUtils.setField(controller, "service", mock(NotificationChannelEmployeeMapService.class));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/email/employee/bind")
            .contentType("application/json")
            .content("{\"address\":\"user@demo.com\",\"remarks\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("remarks 不能为空"));
  }

  @Test
  void bindShouldReturnHttp400WhenRemarksIsEmpty() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationChannelController controller = new NotificationChannelController();
    ReflectionTestUtils.setField(controller, "service", mock(NotificationChannelEmployeeMapService.class));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/email/employee/bind")
            .contentType("application/json")
            .content("{\"address\":\"a@b.com\",\"remarks\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("remarks 不能为空"));
  }

  @Test
  void bindShouldReturnHttp400WhenCurrentUserIsMissing() throws Exception {
    NotificationChannelController controller = new NotificationChannelController();
    ReflectionTestUtils.setField(controller, "service", mock(NotificationChannelEmployeeMapService.class));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/email/employee/bind")
            .contentType("application/json")
            .content("{\"address\":\"user@demo.com\",\"remarks\":\"home\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("无法找到当前登陆用户"));
  }

  @Test
  void unbindShouldReturnHttp400WhenCurrentUserIsMissing() throws Exception {
    NotificationChannelController controller = new NotificationChannelController();
    ReflectionTestUtils.setField(controller, "service", mock(NotificationChannelEmployeeMapService.class));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/email/employee/unbind"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("无法找到当前登陆用户"));
  }

  @Test
  void bindInfoShouldReturnHttp400WhenCurrentUserIsMissing() throws Exception {
    NotificationChannelController controller = new NotificationChannelController();
    ReflectionTestUtils.setField(controller, "service", mock(NotificationChannelEmployeeMapService.class));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/notification/email/employee/bind/info"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("无法找到当前登陆用户"));
  }

  @Test
  void bindShouldReturnHttp200AndInvokeService() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationChannelController controller = new NotificationChannelController();
    NotificationChannelEmployeeMapService service = mock(NotificationChannelEmployeeMapService.class);
    ReflectionTestUtils.setField(controller, "service", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/email/employee/bind")
            .contentType("application/json")
            .content("{\"address\":\"user@demo.com\",\"remarks\":\"home\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(service).bind("email", "emp-1", "user@demo.com", "home");
  }

  @Test
  void unbindShouldReturnHttp200AndInvokeService() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationChannelController controller = new NotificationChannelController();
    NotificationChannelEmployeeMapService service = mock(NotificationChannelEmployeeMapService.class);
    ReflectionTestUtils.setField(controller, "service", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/email/employee/unbind"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(service).unbind("email", "emp-1");
  }

  @Test
  void bindInfoShouldReturnHttp200AndPayload() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationChannelController controller = new NotificationChannelController();
    NotificationChannelEmployeeMapService service = mock(NotificationChannelEmployeeMapService.class);
    NotificationChannelEmployeeMapModel model = new NotificationChannelEmployeeMapModel();
    model.setId("map-1");
    model.setChannel("email");
    model.setEmployeeId("emp-1");
    model.setAddress("user@demo.com");
    model.setRemarks("home");
    when(service.getByEmployeeId("email", "emp-1")).thenReturn(model);
    ReflectionTestUtils.setField(controller, "service", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/notification/email/employee/bind/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.id").value("map-1"))
        .andExpect(jsonPath("$.data.channel").value("email"))
        .andExpect(jsonPath("$.data.employeeId").value("emp-1"))
        .andExpect(jsonPath("$.data.address").value("user@demo.com"))
        .andExpect(jsonPath("$.data.remarks").value("home"));

    verify(service).getByEmployeeId("email", "emp-1");
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
