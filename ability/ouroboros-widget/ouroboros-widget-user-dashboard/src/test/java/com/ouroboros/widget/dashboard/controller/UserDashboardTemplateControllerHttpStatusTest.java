package com.ouroboros.widget.dashboard.controller;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.widget.dashboard.service.UserDashboardTemplateService;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserDashboardTemplateControllerHttpStatusTest {

  @Test
  void missingTemplateShouldReturnHttp404() throws Exception {
    UserDashboardTemplateService service = mock(UserDashboardTemplateService.class);
    when(service.getTemplate("missing")).thenReturn(Optional.empty());

    var controller = new UserDashboardTemplateController(service);
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/user-dashboard-template/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("仪表盘模板不存在"));

    mockMvc.perform(get("/api/user-dashboard-template/missing/load"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("仪表盘模板不存在"));

    mockMvc.perform(put("/api/user-dashboard-template/missing")
            .contentType("application/json")
            .content("{\"name\":\"missing\",\"template\":{}}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("仪表盘模板不存在"));

    mockMvc.perform(delete("/api/user-dashboard-template/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("仪表盘模板不存在"));
  }
}
