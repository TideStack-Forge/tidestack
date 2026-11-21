package com.ouroboros.widget.manage.controller;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Try;

import com.ouroboros.widget.manage.service.WidgetSchemaService;
import com.ouroboros.widget.service.WidgetDefinitionService;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WidgetSchemaManageControllerHttpStatusTest {

  @Test
  void missingWidgetSchemaAndGeneratorShouldReturnHttp404() throws Exception {
    WidgetSchemaService widgetSchemaService = mock(WidgetSchemaService.class);
    when(widgetSchemaService.getWidgetSchema("missing")).thenReturn(Optional.empty());
    when(widgetSchemaService.getWidgetDefinitionGenerators()).thenReturn(Collections.emptyList());
    when(widgetSchemaService.updateWidgetSchema(eq("missing"), any())).thenReturn(Try.success(null));

    WidgetDefinitionService widgetDefinitionService = mock(WidgetDefinitionService.class);

    var controller = new WidgetSchemaManageController(widgetSchemaService, widgetDefinitionService);
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/widget-schema/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("组件 Schema 不存在"));

    mockMvc.perform(put("/api/widget-schema/missing")
            .contentType("application/json")
            .content("{\"name\":\"missing\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("组件 Schema 不存在"));

    mockMvc.perform(get("/api/widget-schema/generators/missing/form-items"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("组件生成器不存在"));
  }
}
