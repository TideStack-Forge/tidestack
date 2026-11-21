package com.ouroboros.organizationstructure.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.dto.request.CreateEmployeeRequest;
import com.ouroboros.organizationstructure.dto.request.UpdateEmployeeRequest;
import com.ouroboros.organizationstructure.dto.response.EmployeeDTO;
import com.ouroboros.organizationstructure.profile.service.EmployeeProfileValueService;
import com.ouroboros.organizationstructure.service.OrganizationFacadeService;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeControllerProfileValuesTest {

  @Test
  void createAndUpdateRequestsShouldSeparateDynamicProfileValuesFromCoreEmployeeFields() {
    CreateEmployeeRequest createRequest = new CreateEmployeeRequest();
    createRequest.setName("张三");
    createRequest.setGender("男");
    createRequest.putProfileValue("employeeNo", "E001");
    createRequest.putProfileValue("title", "高级工程师");
    createRequest.putProfileValue("name", "错误姓名");

    assertEquals("张三", createRequest.toEntity().getName());
    assertEquals("男", createRequest.toEntity().getGender());
    assertEquals("E001", createRequest.toProfileValues().get("employeeNo"));
    assertEquals("高级工程师", createRequest.toProfileValues().get("title"));
    assertFalse(createRequest.toProfileValues().containsKey("name"));

    UpdateEmployeeRequest updateRequest = new UpdateEmployeeRequest();
    updateRequest.setName("李四");
    updateRequest.putProfileValue("office", "A-301");
    updateRequest.putProfileValue("email", "profile@example.com");

    assertEquals("李四", updateRequest.toEntity().getName());
    assertEquals("A-301", updateRequest.toProfileValues().get("office"));
    assertFalse(updateRequest.toProfileValues().containsKey("email"));
  }

  @Test
  void employeeDtoShouldExposeDynamicProfileValuesAtTopLevel() {
    Map<String, Object> profileValues = new LinkedHashMap<String, Object>();
    profileValues.put("employeeNo", "E001");
    profileValues.put("title", "高级工程师");
    profileValues.put("id", "profile-id");
    profileValues.put("name", "错误姓名");

    EmployeeDTO dto = EmployeeDTO.from(new StubEmployee(), profileValues);

    assertEquals("E001", dto.toProfileValues().get("employeeNo"));
    assertEquals("高级工程师", dto.toProfileValues().get("title"));
    assertFalse(dto.toProfileValues().containsKey("id"));
    assertFalse(dto.toProfileValues().containsKey("name"));
    assertNull(dto.toProfileValues().get("missing"));
  }

  @Test
  void getByIdShouldMergeStoredProfileValuesIntoEmployeeDto() {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    EmployeeProfileValueService profileValueService = mock(EmployeeProfileValueService.class);
    Map<String, Object> profileValues = new LinkedHashMap<String, Object>();
    profileValues.put("employeeNo", "E001");

    when(queryFacade.getEmployeeById("employee-1")).thenReturn(Optional.of(new StubEmployee()));
    when(profileValueService.getProfileValuesByEmployeeId("employee-1")).thenReturn(profileValues);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter, profileValueService);

    EmployeeDTO dto = controller.getById("employee-1").getData();

    assertEquals("E001", dto.toProfileValues().get("employeeNo"));
  }

  @Test
  void createUpdateAndDeleteShouldPersistProfileValuesWithEmployeeLifecycle() {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);
    EmployeeProfileValueService profileValueService = mock(EmployeeProfileValueService.class);
    when(manageFacade.createEmployee(any(), eq("department-1"))).thenReturn("employee-1");

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter, profileValueService);

    CreateEmployeeRequest createRequest = new CreateEmployeeRequest();
    createRequest.setName("张三");
    createRequest.setDepartmentId("department-1");
    createRequest.putProfileValue("employeeNo", "E001");
    controller.create(createRequest);

    verify(profileValueService).saveProfileValuesByEmployeeId(eq("employee-1"), eq(createRequest.toProfileValues()));

    UpdateEmployeeRequest updateRequest = new UpdateEmployeeRequest();
    updateRequest.setName("张三");
    updateRequest.putProfileValue("office", "A-301");
    controller.update("employee-1", updateRequest);

    verify(profileValueService).saveProfileValuesByEmployeeId(eq("employee-1"), eq(updateRequest.toProfileValues()));

    controller.delete("employee-1");

    verify(profileValueService).deleteByEmployeeId("employee-1");
  }

  private static final class StubEmployee implements Employee {
    @Override
    public String getId() {
      return "employee-1";
    }

    @Override
    public String getName() {
      return "张三";
    }

    @Override
    public String getGender() {
      return "男";
    }

    @Override
    public String getMobile() {
      return "13800000000";
    }

    @Override
    public String getEmail() {
      return "zhangsan@example.com";
    }

    @Override
    public String getBirthday() {
      return "2020-01-02";
    }

    @Override
    public Company getCompanyDetail() {
      return null;
    }
  }
}
