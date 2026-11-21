package com.ouroboros.usersetting.controller;

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
import com.ouroboros.usersetting.controller.request.SettingValueRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserSettingControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractRequestBodyAndResponsesForSaveSetting() throws Exception {
    Method method = UserSettingController.class.getMethod("saveSetting", SettingValueRequest.class);

    SimpleWebAPIModel model = extract("PATCH", "/api/user-setting", mapping("/api/user-setting", RequestMethod.PATCH), method);

    assertEquals("保存用户设置", model.getSummary());
    assertEquals(Collections.singletonList("user-setting"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("待保存的用户设置", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("设置保存成功", model.getResponses().get("200").getDescription());
    assertEquals("设置参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractQueryParameterForGetSetting() throws Exception {
    Method method = UserSettingController.class.getMethod("getSetting", String.class);

    SimpleWebAPIModel model = extract("GET", "/api/user-setting/get", mapping("/api/user-setting/get", RequestMethod.GET), method);

    assertEquals("获取单个用户设置", model.getSummary());
    assertEquals(Collections.singletonList("user-setting"), model.getTags());
    com.ouroboros.webapi.openapi.ParameterModel queryParameter = onlyQueryParameter(model);
    assertEquals(ParameterLocation.QUERY, queryParameter.getIn());
    assertEquals("设置名称", queryParameter.getDescription());
    assertEquals(Boolean.TRUE, queryParameter.getRequired());
    assertEquals("成功返回设置项", model.getResponses().get("200").getDescription());
    assertEquals("设置项不存在", model.getResponses().get("404").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        UserSettingController.class,
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

  private com.ouroboros.webapi.openapi.ParameterModel onlyQueryParameter(SimpleWebAPIModel model) {
    return model.getParameters().stream()
        .filter(parameter -> ParameterLocation.QUERY.equals(parameter.getIn()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing query parameter"));
  }
}
