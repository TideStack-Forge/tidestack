package com.ouroboros.rpc.runtime.controller;

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

class RpcRuntimeControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractPathParameterRequestBodyAndResponsesForUpdatePolicy() throws Exception {
    Method method = RpcRuntimeController.class.getMethod(
        "updatePolicy", String.class, RpcRuntimeController.RpcRuntimePolicyDTO.class);

    SimpleWebAPIModel model = extract(
        "PUT",
        "/api/sys/rpc-runtime/{modelName}/policy",
        mapping("/api/sys/rpc-runtime/{modelName}/policy", RequestMethod.PUT),
        method);

    assertEquals("更新 RPC 运行时策略", model.getSummary());
    assertEquals(Collections.singletonList("rpc"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "modelName").getIn());
    assertEquals("RPC 运行时模型名称", parameter(model, "modelName").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "modelName").getRequired());
    assertNotNull(model.getRequestBody());
    assertEquals("运行时策略配置", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("策略更新成功", model.getResponses().get("200").getDescription());
    assertEquals("指定模型不存在", model.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractOptionalRequestBodyAndResponsesForTestInvoke() throws Exception {
    Method method = RpcRuntimeController.class.getMethod(
        "testInvoke", String.class, RpcRuntimeController.RpcRuntimeTestInvokeRequest.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/sys/rpc-runtime/{modelName}/actions/test-invoke",
        mapping("/api/sys/rpc-runtime/{modelName}/actions/test-invoke", RequestMethod.POST),
        method);

    assertEquals("测试调用 RPC 模型", model.getSummary());
    assertEquals(Collections.singletonList("rpc"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "modelName").getIn());
    assertEquals("RPC 运行时模型名称", parameter(model, "modelName").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "modelName").getRequired());
    assertNotNull(model.getRequestBody());
    assertEquals("测试调用参数", model.getRequestBody().getDescription());
    assertEquals(Boolean.FALSE, model.getRequestBody().getRequired());
    assertEquals("测试调用执行成功", model.getResponses().get("200").getDescription());
    assertEquals("指定模型不存在", model.getResponses().get("404").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        RpcRuntimeController.class,
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
