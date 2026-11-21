package com.ouroboros.uischema.controller;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UISchemaControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractPathParameterForGetUISchema() throws Exception {
    Method method = UISchemaController.class.getMethod("getUISchema", String.class, Map.class);

    SimpleWebAPIModel model = extract("GET", "/api/ui-schema/{path}", mapping("/api/ui-schema/{path}", RequestMethod.GET), method);

    assertEquals("按路径获取界面 Schema", model.getSummary());
    assertEquals(Collections.singletonList("ui-schema"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "path").getIn());
    assertEquals("界面 Schema 资源路径", parameter(model, "path").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "path").getRequired());
    assertEquals("成功返回界面 Schema", model.getResponses().get("200").getDescription());
    assertEquals("未找到对应界面 Schema", model.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractQueryParameterForGetUISchemaByQuery() throws Exception {
    Method method = UISchemaController.class.getMethod("getUISchema2", String.class, Map.class);

    SimpleWebAPIModel model = extract("GET", "/api/ui-schema", mapping("/api/ui-schema", RequestMethod.GET), method);

    assertEquals("按查询参数获取界面 Schema", model.getSummary());
    assertEquals(Collections.singletonList("ui-schema"), model.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(model, "path").getIn());
    assertEquals("界面 Schema 资源路径", parameter(model, "path").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "path").getRequired());
    assertEquals("成功返回界面 Schema", model.getResponses().get("200").getDescription());
    assertEquals("未找到对应界面 Schema", model.getResponses().get("404").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        UISchemaController.class,
        method
    ).build();
  }

  private RequestMappingInfo mapping(String path, RequestMethod method) {
    return RequestMappingInfo.paths(path)
        .methods(method)
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
