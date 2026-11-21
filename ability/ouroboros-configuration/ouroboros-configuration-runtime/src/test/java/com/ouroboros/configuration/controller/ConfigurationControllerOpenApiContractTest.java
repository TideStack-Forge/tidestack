package com.ouroboros.configuration.controller;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractOperationRequestBodyAndResponsesForUpdate() throws Exception {
    Method method = ConfigurationController.class.getMethod("update", java.util.List.class);

    SimpleWebAPIModel model = extract("PUT", "/api/sys/configuration", mapping("/api/sys/configuration", RequestMethod.PUT), method);

    assertEquals("批量更新运行时配置", model.getSummary());
    assertEquals(Collections.singletonList("configuration-admin"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("待更新的配置项列表", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertTrue(model.getResponses().containsKey("200"));
    assertTrue(model.getResponses().containsKey("400"));
    assertEquals("配置更新成功", model.getResponses().get("200").getDescription());
    assertEquals("配置参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractOperationForList() throws Exception {
    Method method = ConfigurationController.class.getMethod("list");

    SimpleWebAPIModel model = extract("GET", "/api/sys/configuration", mapping("/api/sys/configuration", RequestMethod.GET), method);

    assertEquals("查询全部运行时配置", model.getSummary());
    assertEquals(Collections.singletonList("configuration-admin"), model.getTags());
    assertTrue(model.getResponses().containsKey("200"));
    assertEquals("成功返回配置列表", model.getResponses().get("200").getDescription());
  }

  @Test
  void shouldExtractPathParameterAndResponsesForGet() throws Exception {
    Method method = ConfigurationController.class.getMethod("get", String.class);

    SimpleWebAPIModel model = extract(
        "GET",
        "/api/sys/configuration/{key}",
        mapping("/api/sys/configuration/{key}", RequestMethod.GET),
        method);

    assertEquals("按 key 获取配置值", model.getSummary());
    assertEquals(Collections.singletonList("configuration-admin"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "key").getIn());
    assertEquals("配置项 key", parameter(model, "key").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "key").getRequired());
    assertTrue(model.getResponses().containsKey("200"));
    assertTrue(model.getResponses().containsKey("404"));
    assertEquals("成功返回配置值", model.getResponses().get("200").getDescription());
    assertEquals("指定配置不存在", model.getResponses().get("404").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        ConfigurationController.class,
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
