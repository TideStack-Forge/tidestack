package com.ouroboros.configuration.controller;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.configuration.service.RuntimeConfigurationService;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigurationControllerHttpStatusTest {

  @Test
  void missingConfigurationShouldReturnHttp404() throws Exception {
    RuntimeConfigurationService service = mock(RuntimeConfigurationService.class);
    when(service.getManagedConfigurationModel("missing")).thenReturn(Optional.empty());

    ConfigurationController controller = new ConfigurationController();
    controller.service = service;

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/sys/configuration/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("指定配置不存在"));
  }
}
