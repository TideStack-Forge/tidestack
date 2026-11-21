package com.ouroboros.data.dict.controller;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractOperationAndParametersForDictionaryDetail() throws Exception {
    Method method = DictController.class.getMethod(
        "detail", String.class, String.class, String.class, String.class, String.class);

    SimpleWebAPIModel model = extract(
        "GET",
        "/api/sys/data-dict/{name}",
        mapping("/api/sys/data-dict/{name}", RequestMethod.GET),
        DictController.class,
        method);

    assertEquals("查询数据字典详情", model.getSummary());
    assertEquals(Collections.singletonList("dict"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "name").getIn());
    assertEquals("数据字典名称", parameter(model, "name").getDescription());
    assertTrue(parameter(model, "name").getRequired());
    assertTrue(hasParameterDescription(model, "树形查询的单个根节点编码"));
    assertTrue(hasParameterDescription(model, "树形查询的多个根节点编码，逗号分隔"));
    assertTrue(hasParameterDescription(model, "字典项关键字"));
    assertTrue(hasParameterDescription(model, "字典项编码列表，逗号分隔"));
    assertEquals("成功返回数据字典项列表", model.getResponses().get("200").getDescription());
    assertEquals("指定数据字典不存在", model.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractRequestBodyAndResponsesForDictionaryAdd() throws Exception {
    Method method = DictManageController.class.getMethod("add", DataDict.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/sys/data-dict-manage",
        mapping("/api/sys/data-dict-manage", RequestMethod.POST),
        DictManageController.class,
        method);

    assertEquals("创建数据字典", model.getSummary());
    assertEquals(Collections.singletonList("dict"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("数据字典定义", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("数据字典创建成功", model.getResponses().get("200").getDescription());
    assertEquals("数据字典参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractPathParameterAndPayloadForDictionaryItemBatchUpdate() throws Exception {
    Method method = DictManageController.class.getMethod("updateItems", String.class, List.class);

    SimpleWebAPIModel model = extract(
        "PUT",
        "/api/sys/data-dict-manage/{name}/items",
        mapping("/api/sys/data-dict-manage/{name}/items", RequestMethod.PUT),
        DictManageController.class,
        method);

    assertEquals("批量更新字典项", model.getSummary());
    assertEquals(Collections.singletonList("dict"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "name").getIn());
    assertEquals("数据字典名称", parameter(model, "name").getDescription());
    assertTrue(parameter(model, "name").getRequired());
    assertNotNull(model.getRequestBody());
    assertEquals("新的字典项列表", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("字典项批量更新成功", model.getResponses().get("200").getDescription());
    assertEquals("数据字典不存在", model.getResponses().get("404").getDescription());
  }

  private SimpleWebAPIModel extract(
      String httpMethod,
      String path,
      RequestMappingInfo mapping,
      Class<?> controllerClass,
      Method method) {
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

  private boolean hasParameterDescription(SimpleWebAPIModel model, String description) {
    return model.getParameters().stream()
        .anyMatch(parameter -> description.equals(parameter.getDescription()));
  }
}
