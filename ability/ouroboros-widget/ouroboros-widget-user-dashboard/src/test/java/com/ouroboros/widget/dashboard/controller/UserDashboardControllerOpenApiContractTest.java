package com.ouroboros.widget.dashboard.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.widget.dashboard.model.UserDashboardTemplateModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserDashboardControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractRequestBodyAndResponsesForSave() throws Exception {
    Method method = UserDashboardController.class.getMethod("save", UserDashboardController.SaveDashboardRequest.class);

    SimpleWebAPIModel model = extract("POST", "/api/user-dashboard/save", mapping("/api/user-dashboard/save", RequestMethod.POST), UserDashboardController.class, method);

    assertEquals("保存当前用户仪表盘", model.getSummary());
    assertEquals(Collections.singletonList("widget"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("用户仪表盘保存请求", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("用户仪表盘保存成功", model.getResponses().get("200").getDescription());
    assertEquals("仪表盘数据不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractPathParameterAndPayloadForTemplateUpdate() throws Exception {
    Method method = UserDashboardTemplateController.class.getMethod("update", String.class, UserDashboardTemplateModel.class);

    SimpleWebAPIModel model = extract("PUT", "/api/user-dashboard-template/{id}", mapping("/api/user-dashboard-template/{id}", RequestMethod.PUT), UserDashboardTemplateController.class, method);

    assertEquals("更新仪表盘模板", model.getSummary());
    assertEquals(Collections.singletonList("widget"), model.getTags());
    assertEquals(com.ouroboros.webapi.openapi.ParameterLocation.PATH, parameter(model, "id").getIn());
    assertEquals(Boolean.TRUE, parameter(model, "id").getRequired());
    assertNotNull(model.getRequestBody());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("更新后的仪表盘模板定义", model.getRequestBody().getDescription());
    assertEquals("仪表盘模板更新成功", model.getResponses().get("200").getDescription());
    assertEquals("仪表盘模板不存在", model.getResponses().get("404").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Class<?> controllerClass, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        controllerClass,
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
