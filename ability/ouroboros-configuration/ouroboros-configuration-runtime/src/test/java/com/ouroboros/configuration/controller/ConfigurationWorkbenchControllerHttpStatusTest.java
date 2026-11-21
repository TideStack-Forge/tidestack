package com.ouroboros.configuration.controller;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.configuration.service.ConfigurationWorkbenchService;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigurationWorkbenchControllerHttpStatusTest {

  @Test
  void missingWorkbenchItemShouldReturnHttp404() throws Exception {
    ConfigurationWorkbenchService service = mock(ConfigurationWorkbenchService.class);
    when(service.getItemPanel("missing")).thenReturn(Optional.empty());

    ConfigurationWorkbenchController controller = new ConfigurationWorkbenchController();
    ReflectionTestUtils.setField(controller, "workbenchService", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/sys/configuration/workbench/item/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("配置项不存在"));
  }
}
