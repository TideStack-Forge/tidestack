package com.ouroboros.security.rbac.controller;

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

class RbacControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractRequestBodyAndResponsesForAddUser() throws Exception {
    Method method = UserController.class.getMethod("add", UserController.AddUserVO.class);
    SimpleWebAPIModel model = extract("POST", "/api/sys/user", mapping("/api/sys/user", RequestMethod.POST), method);

    assertEquals("创建用户", model.getSummary());
    assertEquals(Collections.singletonList("rbac-admin"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("新建用户请求", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("用户创建成功", model.getResponses().get("201").getDescription());
    assertEquals("用户参数不合法或用户已存在", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractQueryParametersForSearchUser() throws Exception {
    Method method = UserController.class.getMethod("search", String.class, int.class, int.class);
    SimpleWebAPIModel model = extract("GET", "/api/sys/user", mapping("/api/sys/user", RequestMethod.GET), method);

    assertEquals("分页查询用户", model.getSummary());
    assertEquals(Collections.singletonList("rbac-admin"), model.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(model, "userIdentity").getIn());
    assertEquals("用户标识关键字", parameter(model, "userIdentity").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(model, "page").getIn());
    assertEquals("页码", parameter(model, "page").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(model, "perPage").getIn());
    assertEquals("每页条数", parameter(model, "perPage").getDescription());
    assertEquals("成功返回用户分页结果", model.getResponses().get("200").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder().api(httpMethod, path).scope(WebAPIScope.INTERNAL).handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        UserController.class,
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
