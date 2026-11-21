package com.ouroboros.businesslog.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.businesslog.dto.request.CreateBusinessOperationTemplateOverrideReq;
import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BusinessLogControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractQueryParametersForBusinessLogSearch() throws Exception {
    Method method = BusinessOperationController.class.getMethod(
        "search", String.class, String.class, String.class, String.class, int.class, int.class);

    SimpleWebAPIModel model = extract(
        "GET",
        "/api/sys/business-log",
        mapping("/api/sys/business-log", RequestMethod.GET),
        BusinessOperationController.class,
        method);

    assertEquals("分页查询业务日志", model.getSummary());
    assertEquals(Collections.singletonList("business-log"), model.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(model, "moduleName").getIn());
    assertEquals("模块名称前缀", parameter(model, "moduleName").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(model, "page").getIn());
    assertEquals("页码", parameter(model, "page").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(model, "perPage").getIn());
    assertEquals("每页条数", parameter(model, "perPage").getDescription());
    assertEquals("成功返回业务日志分页结果", model.getResponses().get("200").getDescription());
  }

  @Test
  void shouldExtractRequestBodyAndResponsesForTemplateAdd() throws Exception {
    Method method = BusinessOperationTemplateController.class.getMethod("add", BusinessOperationTemplateModel.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/sys/business-log-template",
        mapping("/api/sys/business-log-template", RequestMethod.POST),
        BusinessOperationTemplateController.class,
        method);

    assertEquals("创建业务日志模板", model.getSummary());
    assertEquals(Collections.singletonList("business-log"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("日志模板定义", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("日志模板创建成功", model.getResponses().get("201").getDescription());
    assertEquals("日志模板参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractRequestBodyForCreateOverrideFromSource() throws Exception {
    Method method = BusinessOperationTemplateController.class.getMethod(
        "createOverrideFromSource", CreateBusinessOperationTemplateOverrideReq.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/sys/business-log-template/override-from-source",
        mapping("/api/sys/business-log-template/override-from-source", RequestMethod.POST),
        BusinessOperationTemplateController.class,
        method);

    assertEquals("从来源生成覆盖模板", model.getSummary());
    assertEquals(Collections.singletonList("business-log"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("来源模板覆盖请求", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("覆盖模板生成成功", model.getResponses().get("200").getDescription());
    assertEquals("来源模板不存在", model.getResponses().get("404").getDescription());
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
