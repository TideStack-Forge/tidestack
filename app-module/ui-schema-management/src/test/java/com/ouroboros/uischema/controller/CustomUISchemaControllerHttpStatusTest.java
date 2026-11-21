package com.ouroboros.uischema.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.vavr.control.Try;

import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.model.CustomUISchema;
import com.ouroboros.uischema.service.CustomUISchemaService;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

class CustomUISchemaControllerHttpStatusTest {

  @Test
  void searchShouldExecuteTypedQueryWithFiltersPaginationAndOrder() throws Exception {
    DataModel model = mock(DataModel.class, CALLS_REAL_METHODS);
    when(model.count(anyMap())).thenReturn(Try.success(1L));
    when(model.query(anyMap())).thenReturn(Try.success(RecordList.of(List.of(
        Map.of("id", "schema-1", "path", "test/page")))));
    var controller = new CustomUISchemaController();
    ReflectionTestUtils.setField(controller, "dataModel", new BaseTypedDataModel<>(model, CustomUISchema.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    mockMvc.perform(get("/api/sys/custom-ui-schema")
            .param("path", "test/page").param("page", "2").param("pageSize", "5").param("sort", "path desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].path").value("test/page"));

    ArgumentCaptor<Map<String, Object>> query = ArgumentCaptor.captor();
    verify(model).query(query.capture());
    verify(model).count(Map.of("path", "test/page"));
    assertEquals(Map.of("path", "test/page"), query.getValue().get("WHERE"));
    assertEquals(Map.of("path", "desc"), query.getValue().get("ORDER"));
    assertEquals(5, query.getValue().get("OFFSET"));
    assertEquals(5, query.getValue().get("LIMIT"));
  }

  @Test
  void missingCustomSchemaShouldReturnHttp404() throws Exception {
    CustomUISchemaService service = mock(CustomUISchemaService.class);
    when(service.delete(eq("missing"), anyString())).thenReturn(false);
    when(service.update(eq("missing"), any(), anyString())).thenReturn(false);
    when(service.getById("missing")).thenReturn(null);

    CustomUISchemaController controller = new CustomUISchemaController();
    ReflectionTestUtils.setField(controller, "service", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/sys/custom-ui-schema/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("自定义界面 Schema 不存在"));

    mockMvc.perform(put("/api/sys/custom-ui-schema/missing")
            .contentType("application/json")
            .content("{\"name\":\"missing\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("自定义界面 Schema 不存在"));

    mockMvc.perform(delete("/api/sys/custom-ui-schema/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("自定义界面 Schema 不存在"));
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
