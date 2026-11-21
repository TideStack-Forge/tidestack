package com.ouroboros.security.rbac.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.service.RoleService;
import com.ouroboros.security.rbac.service.UserRoleService;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleControllerHttpStatusTest {

  @Test
  void missingRoleEndpointsShouldReturnHttp404() throws Exception {
    var service = mock(RoleService.class);
    when(service.delete("missing")).thenReturn(false);
    when(service.update(org.mockito.ArgumentMatchers.eq("missing"), any())).thenReturn(false);

    var controller = new RoleController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "userRoleService", mock(UserRoleService.class));
    ReflectionTestUtils.setField(controller, "roleAuthorityService", mock(RoleAuthorityService.class));
    ReflectionTestUtils.setField(controller, "userCenter", mock(UserCenter.class));

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(delete("/api/sys/role/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("角色不存在"));

    mockMvc.perform(put("/api/sys/role/missing")
            .contentType("application/json")
            .content("{\"id\":\"missing\",\"name\":\"Missing\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("角色不存在"));
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
