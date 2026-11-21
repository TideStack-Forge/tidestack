package com.ouroboros.resource.manage.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeResourceControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractQueryParametersAndMultipartBodyForUpload() throws Exception {
    Method method = RuntimeResourceController.class.getMethod("upload", String.class, String.class, MultipartFile.class);

    SimpleWebAPIModel model = extract("POST", "/api/sys/resource/runtime/content", mapping("/api/sys/resource/runtime/content", RequestMethod.POST, "multipart/form-data"), method);

    assertEquals("上传运行时资源覆盖内容", model.getSummary());
    assertEquals(Collections.singletonList("resource"), model.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(model, "scope").getIn());
    assertEquals("资源域", parameter(model, "scope").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(model, "key").getIn());
    assertEquals("资源 key", parameter(model, "key").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "scope").getRequired());
    assertEquals(Boolean.TRUE, parameter(model, "key").getRequired());
    assertEquals("上传参数不合法", model.getResponses().get("400").getDescription());
    assertEquals("运行时资源上传成功", model.getResponses().get("200").getDescription());
  }

  @Test
  void shouldExtractResponsesForGetEffective() throws Exception {
    Method method = RuntimeResourceController.class.getMethod("getEffective", String.class, String.class);

    SimpleWebAPIModel model = extract("GET", "/api/sys/resource/runtime/effective", mapping("/api/sys/resource/runtime/effective", RequestMethod.GET), method);

    assertEquals("获取运行时资源详情", model.getSummary());
    assertEquals(Collections.singletonList("resource"), model.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(model, "scope").getIn());
    assertEquals(ParameterLocation.QUERY, parameter(model, "key").getIn());
    assertEquals("资源域", parameter(model, "scope").getDescription());
    assertEquals("资源 key", parameter(model, "key").getDescription());
    assertEquals("成功返回运行时资源详情", model.getResponses().get("200").getDescription());
    assertEquals("运行时资源不存在", model.getResponses().get("404").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        RuntimeResourceController.class,
        method
    ).build();
  }

  private RequestMappingInfo mapping(String path, RequestMethod method) {
    return mapping(path, method, "application/json");
  }

  private RequestMappingInfo mapping(String path, RequestMethod method, String consumes) {
    return RequestMappingInfo.paths(path)
        .methods(method)
        .consumes(consumes)
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
