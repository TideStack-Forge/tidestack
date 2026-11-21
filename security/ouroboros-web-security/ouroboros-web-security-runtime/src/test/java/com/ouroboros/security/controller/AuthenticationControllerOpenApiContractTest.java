package com.ouroboros.security.controller;

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

class AuthenticationControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractRequestBodyAndResponsesForLogin() throws Exception {
    Method method = AuthenticationController.class.getMethod("login", AuthenticationController.LoginDTO.class);
    SimpleWebAPIModel model = extract("POST", "/api/auth/login", mapping("/api/auth/login", RequestMethod.POST), method);

    assertEquals("账号密码登录", model.getSummary());
    assertEquals(Collections.singletonList("authentication"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("本地账号密码登录请求", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("登录成功并返回访问令牌", model.getResponses().get("200").getDescription());
    assertEquals("用户名或密码错误", model.getResponses().get("401").getDescription());
    assertEquals("本地账号密码登录已禁用", model.getResponses().get("403").getDescription());
  }

  @Test
  void shouldExtractPathParameterForRefreshToken() throws Exception {
    Method method = AuthenticationController.class.getMethod("refreshToken", String.class);
    SimpleWebAPIModel model = extract("POST", "/api/auth/refresh-token/{refreshToken}", mapping("/api/auth/refresh-token/{refreshToken}", RequestMethod.POST), method);

    assertEquals("刷新访问令牌", model.getSummary());
    assertEquals(Collections.singletonList("authentication"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "refreshToken").getIn());
    assertEquals("用于换取新令牌的刷新令牌", parameter(model, "refreshToken").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "refreshToken").getRequired());
    assertEquals("刷新成功并返回新的令牌", model.getResponses().get("200").getDescription());
    assertEquals("刷新令牌无效或已过期", model.getResponses().get("401").getDescription());
  }

  @Test
  void shouldExtractHeaderParameterForRevokeToken() throws Exception {
    Method method = AuthenticationController.class.getMethod("revokeToken", String.class);
    SimpleWebAPIModel model = extract("POST", "/api/auth/revoke-token", mapping("/api/auth/revoke-token", RequestMethod.POST), method);

    assertEquals("撤销访问令牌", model.getSummary());
    assertEquals(Collections.singletonList("authentication"), model.getTags());
    assertEquals(ParameterLocation.HEADER, parameter(model, "Authorization").getIn());
    assertEquals("Bearer 访问令牌，格式为 Bearer <token>", parameter(model, "Authorization").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "Authorization").getRequired());
    assertEquals("令牌已撤销", model.getResponses().get("204").getDescription());
    assertEquals("访问令牌无效", model.getResponses().get("401").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder().api(httpMethod, path).scope(WebAPIScope.INTERNAL).handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        AuthenticationController.class,
        method
    ).build();
  }

  private RequestMappingInfo mapping(String path, RequestMethod method) {
    return RequestMappingInfo.paths(path).methods(method).consumes("application/json").produces("application/json").build();
  }

  private com.ouroboros.webapi.openapi.ParameterModel parameter(SimpleWebAPIModel model, String name) {
    return model.getParameters().stream().filter(parameter -> name.equals(parameter.getName())).findFirst().orElseThrow(() -> new AssertionError("missing parameter " + name));
  }
}
