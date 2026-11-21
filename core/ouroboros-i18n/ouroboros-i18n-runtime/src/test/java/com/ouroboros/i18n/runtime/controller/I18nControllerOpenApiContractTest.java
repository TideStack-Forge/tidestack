package com.ouroboros.i18n.runtime.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.runtime.dto.SyncRequest;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractPathParameterAndResponsesForGetLocale() throws Exception {
    Method method = I18nLocaleController.class.getMethod("getLocale", String.class);

    SimpleWebAPIModel model = extract("GET", "/api/i18n/locale/{locale}", mapping("/api/i18n/locale/{locale}", RequestMethod.GET), I18nLocaleController.class, method);

    assertEquals("获取语言包", model.getSummary());
    assertEquals(Collections.singletonList("i18n"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "locale").getIn());
    assertEquals("语言代码", parameter(model, "locale").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "locale").getRequired());
    assertEquals("成功返回语言包", model.getResponses().get("200").getDescription());
    assertEquals("语言包不存在", model.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractRequestBodyAndResponsesForSaveLocale() throws Exception {
    Method method = I18nLocaleManageController.class.getMethod("saveLocale", I18nLocale.class);

    SimpleWebAPIModel model = extract("POST", "/api/i18n/manage/locale", mapping("/api/i18n/manage/locale", RequestMethod.POST), I18nLocaleManageController.class, method);

    assertEquals("保存语言包", model.getSummary());
    assertEquals(Collections.singletonList("i18n"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("语言包定义", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("语言包保存成功", model.getResponses().get("200").getDescription());
    assertEquals("语言包参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractMultipartRequestBodyAndResponsesForImportLocale() throws Exception {
    Method method = I18nLocaleManageController.class.getMethod("importLocale", MultipartFile.class);

    SimpleWebAPIModel model = extract("POST", "/api/i18n/manage/import", mapping("/api/i18n/manage/import", RequestMethod.POST, "multipart/form-data"), I18nLocaleManageController.class, method);

    assertEquals("导入语言文件", model.getSummary());
    assertEquals(Collections.singletonList("i18n"), model.getTags());
    assertTrue(model.getParameters().isEmpty());
    assertNotNull(model.getRequestBody());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertNotNull(model.getRequestBody().getContent().get("multipart/form-data"));
    assertEquals("object", model.getRequestBody().getContent().get("multipart/form-data").getSchema().getType());
    assertTrue(model.getRequestBody().getContent().get("multipart/form-data").getSchema().getRequired().contains("file"));
    assertEquals("语言文件", model.getRequestBody().getContent().get("multipart/form-data").getSchema().getProperties().get("file").getDescription());
    assertEquals("binary", model.getRequestBody().getContent().get("multipart/form-data").getSchema().getProperties().get("file").getFormat());
    assertEquals("语言文件导入成功", model.getResponses().get("200").getDescription());
    assertEquals("上传文件为空或格式错误", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractRequestBodyAndResponsesForSyncFromExtractors() throws Exception {
    Method method = I18nLocaleManageController.class.getMethod("syncFromExtractors", SyncRequest.class);

    SimpleWebAPIModel model = extract("POST", "/api/i18n/manage/sync", mapping("/api/i18n/manage/sync", RequestMethod.POST), I18nLocaleManageController.class, method);

    assertEquals("同步提取器文案", model.getSummary());
    assertEquals(Collections.singletonList("i18n"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("文案同步请求", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("文案同步成功", model.getResponses().get("200").getDescription());
    assertEquals("同步参数不合法", model.getResponses().get("400").getDescription());
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
