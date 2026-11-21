package com.ouroboros.organizationstructure.controller;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.profile.service.PersonnelProfileFormSchemeService;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonnelProfileFormSchemeControllerHttpStatusTest {

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void detailUpdateAndDeleteShouldReturnHttp404WhenSchemeMissingInApp() throws Exception {
    PersonnelProfileFormSchemeService service = mock(PersonnelProfileFormSchemeService.class);
    PersonnelProfileFormSchemeController controller = new PersonnelProfileFormSchemeController(service);

    when(service.getById("missing", "demo-app")).thenReturn(Optional.empty());

    MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build()
        .perform(get("/api/dev/demo-app/organization-management/personnel-profile-form-scheme/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("人员档案表单方案不存在"));

    MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build()
        .perform(put("/api/dev/demo-app/organization-management/personnel-profile-form-scheme/missing")
            .contentType("application/json")
            .content("{\"name\":\"更新方案\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("人员档案表单方案不存在"));

    MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build()
        .perform(delete("/api/dev/demo-app/organization-management/personnel-profile-form-scheme/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("人员档案表单方案不存在"));
  }
  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
