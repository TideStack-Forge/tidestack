package com.ouroboros.notification.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.AddressNotificationDispatcher;
import com.ouroboros.notification.DispatchOptions;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerHttpStatusTest {

  @AfterEach
  void tearDown() {
    OrganizationContextHolder.clear();
  }

  @Test
  void sendShouldReturnHttp400WhenEmployeeIdsAndAddressesAreBothEmpty() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"tpl\",\"employeeIds\":[],\"addresses\":{}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("员工和地址不能同时为空"));
  }

  @Test
  void sendShouldReturnHttp400WhenTemplateIsBlank() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"\",\"employeeIds\":[\"emp-1\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("模板不能为空"));
  }

  @Test
  void sendShouldReturnHttp400WhenTemplateIsWhitespaceOnly() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"   \",\"employeeIds\":[\"emp-1\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("模板不能为空"));
  }

  @Test
  void sendShouldReturnHttp400WhenCurrentUserIsMissing() throws Exception {
    NotificationController controller = new NotificationController();

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"tpl\",\"employeeIds\":[\"emp-1\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("当前用户不存在"));
  }

  @Test
  void sendShouldReturnHttp200AndForwardCallerDispatchOptionsToEmployeesWithCurrentUserOverride() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();
    EmployeeNotificationDispatcher employeeDispatcher = mock(EmployeeNotificationDispatcher.class);
    AddressNotificationDispatcher addressDispatcher = mock(AddressNotificationDispatcher.class);
    controller.employeeNotificationDispatcher = employeeDispatcher;
    controller.addressNotificationDispatcher = addressDispatcher;

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"tpl\",\"employeeIds\":[\"emp-2\"],\"dispatchOptions\":{\"channels\":[\"test-channel\"],\"channelOptions\":{\"test-channel\":{}},\"sendByEmployee\":\"emp-0\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(true));

    verify(employeeDispatcher, timeout(1000)).send(
        eq(Collections.singletonList("emp-2")),
        eq("tpl"),
        argThat(Map::isEmpty),
        argThat(options -> hasForwardedDispatchOptions(options, "emp-1")));
    verifyNoInteractions(addressDispatcher);
  }

  @Test
  void sendShouldReturnHttp200AndForwardCallerDispatchOptionsToAddressesWithCurrentUserOverride() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();
    EmployeeNotificationDispatcher employeeDispatcher = mock(EmployeeNotificationDispatcher.class);
    AddressNotificationDispatcher addressDispatcher = mock(AddressNotificationDispatcher.class);
    controller.employeeNotificationDispatcher = employeeDispatcher;
    controller.addressNotificationDispatcher = addressDispatcher;
    Map<String, List<String>> addresses = Collections.singletonMap("email", Collections.singletonList("test@example.com"));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"tpl\",\"addresses\":{\"email\":[\"test@example.com\"]},\"dispatchOptions\":{\"channels\":[\"test-channel\"],\"channelOptions\":{\"test-channel\":{}},\"sendByEmployee\":\"emp-0\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(true));

    verify(addressDispatcher, timeout(1000)).send(
        eq((Map<String, java.util.Collection<String>>) (Map<String, ?>) addresses),
        eq("tpl"),
        argThat(Map::isEmpty),
        argThat(options -> hasForwardedDispatchOptions(options, "emp-1")));
    verifyNoInteractions(employeeDispatcher);
  }

  @Test
  void sendShouldReturnHttp200AndForwardCallerDispatchOptionsToEmployeesAndAddressesWithCurrentUserOverride() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();
    EmployeeNotificationDispatcher employeeDispatcher = mock(EmployeeNotificationDispatcher.class);
    AddressNotificationDispatcher addressDispatcher = mock(AddressNotificationDispatcher.class);
    controller.employeeNotificationDispatcher = employeeDispatcher;
    controller.addressNotificationDispatcher = addressDispatcher;
    Map<String, List<String>> addresses = Collections.singletonMap("email", Collections.singletonList("test@example.com"));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"tpl\",\"employeeIds\":[\"emp-2\"],\"addresses\":{\"email\":[\"test@example.com\"]},\"dispatchOptions\":{\"channels\":[\"test-channel\"],\"channelOptions\":{\"test-channel\":{}},\"sendByEmployee\":\"emp-0\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(true));

    verify(employeeDispatcher, timeout(1000)).send(
        eq(Collections.singletonList("emp-2")),
        eq("tpl"),
        argThat(Map::isEmpty),
        argThat(options -> hasForwardedDispatchOptions(options, "emp-1")));
    verify(addressDispatcher, timeout(1000)).send(
        eq((Map<String, java.util.Collection<String>>) (Map<String, ?>) addresses),
        eq("tpl"),
        argThat(Map::isEmpty),
        argThat(options -> hasForwardedDispatchOptions(options, "emp-1")));
  }

  @Test
  void sendShouldReturnHttp200AndDispatchToEmployees() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();
    EmployeeNotificationDispatcher employeeDispatcher = mock(EmployeeNotificationDispatcher.class);
    AddressNotificationDispatcher addressDispatcher = mock(AddressNotificationDispatcher.class);
    controller.employeeNotificationDispatcher = employeeDispatcher;
    controller.addressNotificationDispatcher = addressDispatcher;

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"tpl\",\"employeeIds\":[\"emp-2\"],\"payload\":{\"key\":\"value\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(true));

    verify(employeeDispatcher, timeout(1000)).send(
        eq(Collections.singletonList("emp-2")),
        eq("tpl"),
        argThat(payload -> "value".equals(payload.get("key"))),
        argThat(options -> options instanceof DispatchOptions && "emp-1".equals(((DispatchOptions) options).getSendByEmployee())));
    verifyNoInteractions(addressDispatcher);
  }

  @Test
  void sendShouldReturnHttp200AndDispatchToAddresses() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();
    EmployeeNotificationDispatcher employeeDispatcher = mock(EmployeeNotificationDispatcher.class);
    AddressNotificationDispatcher addressDispatcher = mock(AddressNotificationDispatcher.class);
    controller.employeeNotificationDispatcher = employeeDispatcher;
    controller.addressNotificationDispatcher = addressDispatcher;
    Map<String, List<String>> addresses = Collections.singletonMap("email", Collections.singletonList("test@example.com"));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"tpl\",\"addresses\":{\"email\":[\"test@example.com\"]}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(true));

    verify(addressDispatcher, timeout(1000)).send(
        eq((Map<String, java.util.Collection<String>>) (Map<String, ?>) addresses),
        eq("tpl"),
        argThat(Map::isEmpty),
        argThat(options -> options instanceof DispatchOptions && "emp-1".equals(((DispatchOptions) options).getSendByEmployee())));
    verifyNoInteractions(employeeDispatcher);
  }

  @Test
  void sendShouldReturnHttp200AndDispatchToEmployeesAndAddressesTogether() throws Exception {
    OrganizationContextHolder.setContext(new TestOrganizationContext("emp-1"));
    NotificationController controller = new NotificationController();
    EmployeeNotificationDispatcher employeeDispatcher = mock(EmployeeNotificationDispatcher.class);
    AddressNotificationDispatcher addressDispatcher = mock(AddressNotificationDispatcher.class);
    controller.employeeNotificationDispatcher = employeeDispatcher;
    controller.addressNotificationDispatcher = addressDispatcher;
    Map<String, List<String>> addresses = Collections.singletonMap("email", Collections.singletonList("test@example.com"));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/notification/send")
            .contentType("application/json")
            .content("{\"template\":\"tpl\",\"employeeIds\":[\"emp-2\"],\"addresses\":{\"email\":[\"test@example.com\"]}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(true));

    verify(employeeDispatcher, timeout(1000)).send(
        eq(Collections.singletonList("emp-2")),
        eq("tpl"),
        argThat(Map::isEmpty),
        argThat(options -> options instanceof DispatchOptions && "emp-1".equals(((DispatchOptions) options).getSendByEmployee())));
    verify(addressDispatcher, timeout(1000)).send(
        eq((Map<String, java.util.Collection<String>>) (Map<String, ?>) addresses),
        eq("tpl"),
        argThat(Map::isEmpty),
        argThat(options -> options instanceof DispatchOptions && "emp-1".equals(((DispatchOptions) options).getSendByEmployee())));
  }

  private boolean hasForwardedDispatchOptions(Object options, String currentEmployee) {
    if (!(options instanceof DispatchOptions)) {
      return false;
    }
    DispatchOptions dispatchOptions = (DispatchOptions) options;
    return currentEmployee.equals(dispatchOptions.getSendByEmployee())
        && dispatchOptions.getChannels() != null
        && dispatchOptions.getChannels().contains("test-channel")
        && dispatchOptions.getChannelOptions() != null
        && dispatchOptions.getChannelOptions().containsKey("test-channel")
        && dispatchOptions.getChannelOptions().get("test-channel") != null;
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
