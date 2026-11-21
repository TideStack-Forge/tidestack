package com.ouroboros.widget.manage.controller;

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
import com.ouroboros.widget.WidgetSchema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WidgetSchemaManageControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractRequestBodyAndResponsesForAddWidgetSchema() throws Exception {
    Method method = WidgetSchemaManageController.class.getMethod("addWidgetSchema", WidgetSchema.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/widget-schema",
        mapping("/api/widget-schema", RequestMethod.POST),
        method);

    assertEquals("创建组件 Schema", model.getSummary());
    assertEquals(Collections.singletonList("widget"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("组件 Schema 定义", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("组件 Schema 创建成功", model.getResponses().get("200").getDescription());
    assertEquals("组件名称已存在或参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractQueryParameterAndResponsesForWidgetSchemaManageList() throws Exception {
    Method method = WidgetSchemaManageController.class.getMethod("getWidgetSchemaManageList", String.class);

    SimpleWebAPIModel model = extract(
        "GET",
        "/api/widget-schema",
        mapping("/api/widget-schema", RequestMethod.GET),
        method);

    assertEquals("查询组件 Schema 管理列表", model.getSummary());
    assertEquals(Collections.singletonList("widget"), model.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(model, "keyword").getIn());
    assertEquals("组件关键字", parameter(model, "keyword").getDescription());
    assertEquals("成功返回组件 Schema 管理列表", model.getResponses().get("200").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        WidgetSchemaManageController.class,
        method
    ).build();
  }

  private RequestMappingInfo mapping(String path, RequestMethod method) {
    return RequestMappingInfo.paths(path)
        .methods(method)
        .consumes("application/json")
        .produces("application/json")
        .build();
  }

  private com.ouroboros.webapi.openapi.ParameterModel parameter(SimpleWebAPIModel model, String name) {
    return model.getParameters().stream()
        .filter(parameter -> name.equals(parameter.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing parameter " + name));
  }
}
