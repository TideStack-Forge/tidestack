package com.ouroboros.businessmodel.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessModelExportControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractPathAndQueryParametersForExportExcel() throws Exception {
    Method method = DefaultBusinessModelServerSideExportController.class.getMethod(
        "exportExcel", String.class, java.util.Map.class, HttpServletResponse.class);

    SimpleWebAPIModel model = extractor.extract(
        SimpleWebAPIModel.builder()
            .api("GET", "/api/default-business-model-data-export/{fullName}/export-excel")
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        RequestMappingInfo.paths("/api/default-business-model-data-export/{fullName}/export-excel")
            .methods(RequestMethod.GET)
            .produces("application/json")
            .build(),
        DefaultBusinessModelServerSideExportController.class,
        method
    ).build();

    assertEquals("导出业务模型 Excel", model.getSummary());
    assertEquals(Collections.singletonList("business-model"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "fullName").getIn());
    assertEquals("业务模型全名", parameter(model, "fullName").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(model, "params").getIn());
    assertEquals("导出查询条件", parameter(model, "params").getDescription());
    assertEquals("Excel 导出成功", model.getResponses().get("200").getDescription());
    assertEquals("导出参数不合法或业务模型不存在", model.getResponses().get("400").getDescription());
  }

  private com.ouroboros.webapi.openapi.ParameterModel parameter(SimpleWebAPIModel model, String name) {
    return model.getParameters().stream()
        .filter(parameter -> name.equals(parameter.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing parameter " + name));
  }
}
