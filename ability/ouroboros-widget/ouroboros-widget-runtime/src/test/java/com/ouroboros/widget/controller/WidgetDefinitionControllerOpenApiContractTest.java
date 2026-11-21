package com.ouroboros.widget.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetDefinitionControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractSceneFilterParameterAndResponses() throws Exception {
    Method method = WidgetDefinitionController.class.getMethod("getAllWidgetDefinitions", String.class);

    SimpleWebAPIModel model = extractor.extract(
        SimpleWebAPIModel.builder()
            .api("GET", "/api/widget-definition")
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        RequestMappingInfo.paths("/api/widget-definition")
            .methods(RequestMethod.GET)
            .produces("application/json")
            .build(),
        WidgetDefinitionController.class,
        method
    ).build();

    assertEquals("列出组件定义", model.getSummary());
    assertEquals(Collections.singletonList("widget"), model.getTags());
    assertEquals(ParameterLocation.QUERY, onlyQueryParameter(model).getIn());
    assertEquals("组件使用场景", onlyQueryParameter(model).getDescription());
    assertTrue(model.getResponses().containsKey("200"));
    assertEquals("成功返回组件定义列表", model.getResponses().get("200").getDescription());
  }

  private com.ouroboros.webapi.openapi.ParameterModel onlyQueryParameter(SimpleWebAPIModel model) {
    return model.getParameters().stream()
        .filter(parameter -> ParameterLocation.QUERY.equals(parameter.getIn()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing query parameter"));
  }
}
