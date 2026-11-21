package com.ouroboros.businesslog.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.businesslog.BusinessOperationTemplateSource;
import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;
import com.ouroboros.businesslog.service.BusinessOperationTemplateQueryService;
import com.ouroboros.businesslog.service.BusinessOperationTemplateService;
import com.ouroboros.businesslog.service.model.BusinessOperationTemplateSourceView;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.PagedResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

class BusinessOperationTemplateControllerTest {

  private final BusinessOperationTemplateService service = mock(BusinessOperationTemplateService.class);
  private final BusinessOperationTemplateQueryService queryService = mock(BusinessOperationTemplateQueryService.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    BusinessOperationTemplateController controller = new BusinessOperationTemplateController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "queryService", queryService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();
  }

  @Test
  void shouldReturnMergedTemplatesWithSourceMetadata() throws Exception {
    when(queryService.search(null, null, null, null, 1, 20)).thenReturn(PagedResult.of(
        1L,
        Collections.singletonList(view("POST /orders", BusinessOperationTemplateSource.DATABASE))
    ));

    mockMvc.perform(get("/api/sys/business-log-template"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].templateKey").value("POST /orders"))
        .andExpect(jsonPath("$.data.items[0].source").value("DATABASE"))
        .andExpect(jsonPath("$.data.items[0].availableSources[0]").value("DATABASE"))
        .andExpect(jsonPath("$.data.items[0].isControlled").value(true));
  }

  @Test
  void shouldCreateDatabaseOverrideFromSourceTemplate() throws Exception {
    BusinessOperationTemplateModel model = new BusinessOperationTemplateModel();
    model.setHttpMethod("POST");
    model.setUrl("/orders");
    model.setModuleName("订单");
    model.setOperation("创建订单");
    model.setLevel("INFO");

    when(queryService.buildOverrideFromSource(eq("POST /orders"))).thenReturn(model);

    mockMvc.perform(post("/api/sys/business-log-template/override-from-source")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateKey\":\"POST /orders\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.httpMethod").value("POST"))
        .andExpect(jsonPath("$.data.url").value("/orders"))
        .andExpect(jsonPath("$.data.moduleName").value("订单"))
        .andExpect(jsonPath("$.data.operation").value("创建订单"));
  }

  @Test
  void missingTemplateEndpointsShouldReturnHttp404() throws Exception {
    when(service.delete("missing")).thenReturn(false);
    when(service.update(eq("missing"), org.mockito.ArgumentMatchers.any())).thenReturn(false);
    when(queryService.getDetail("missing")).thenReturn(java.util.Optional.empty());
    when(queryService.buildOverrideFromSource("missing")).thenReturn(null);

    mockMvc.perform(delete("/api/sys/business-log-template/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("日志模板不存在"));

    mockMvc.perform(put("/api/sys/business-log-template/missing")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"httpMethod\":\"POST\",\"url\":\"/orders\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("日志模板不存在"));

    mockMvc.perform(get("/api/sys/business-log-template/detail").param("templateKey", "missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("日志模板不存在"));

    mockMvc.perform(post("/api/sys/business-log-template/override-from-source")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateKey\":\"missing\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("来源模板不存在"));
  }

  private BusinessOperationTemplateSourceView view(String templateKey, BusinessOperationTemplateSource source) {
    BusinessOperationTemplateSourceView view = new BusinessOperationTemplateSourceView();
    view.setTemplateKey(templateKey);
    view.setSource(source);
    view.setControlled(source == BusinessOperationTemplateSource.DATABASE);
    view.setAvailableSources(Collections.singletonList(source));
    view.setOverridesSources(Collections.emptyList());
    view.setHttpMethod("POST");
    view.setUrl("/orders");
    view.setModuleName("订单");
    view.setOperation("创建订单");
    view.setLevel("INFO");
    return view;
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
