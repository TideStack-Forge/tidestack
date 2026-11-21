package com.ouroboros.upload.runtime.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.upload.controller.AbstractUploadController.UploadChunkRequestVO;
import com.ouroboros.upload.model.request.StartChunkRequest;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UploadControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractDownloadPathAndQueryParameters() throws Exception {
    Method method = DownloadFileController.class.getMethod("downloadFile", String.class, String.class, HttpServletResponse.class);

    SimpleWebAPIModel model = extract("GET", "/api/file/{filePath}", mapping("/api/file/{filePath}", RequestMethod.GET), DownloadFileController.class, method);

    assertEquals("下载文件", model.getSummary());
    assertEquals(Collections.singletonList("file-upload"), model.getTags());
    com.ouroboros.webapi.openapi.ParameterModel pathParameter = onlyPathParameter(model);
    assertEquals(ParameterLocation.PATH, pathParameter.getIn());
    assertEquals("文件存储路径", pathParameter.getDescription());
    com.ouroboros.webapi.openapi.ParameterModel queryParameter = onlyQueryParameter(model);
    assertEquals(ParameterLocation.QUERY, queryParameter.getIn());
    assertEquals("下载文件名", queryParameter.getDescription());
  }

  @Test
  void shouldExtractRequestBodyAndResponsesForStartChunk() throws Exception {
    Method method = UploadController.class.getMethod("startChunk", StartChunkRequest.class, HttpServletRequest.class);

    SimpleWebAPIModel model = extract("POST", "/api/upload/start-chunk", mapping("/api/upload/start-chunk", RequestMethod.POST), UploadController.class, method);

    assertEquals("初始化分片上传", model.getSummary());
    assertEquals(Collections.singletonList("file-upload"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("分片上传初始化请求", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("分片上传初始化成功", model.getResponses().get("200").getDescription());
    assertEquals("分片上传参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractMultipartPayloadForUploadChunk() throws Exception {
    Method method = UploadController.class.getMethod("uploadChunk", UploadChunkRequestVO.class, HttpServletRequest.class);

    SimpleWebAPIModel model = extract("POST", "/api/upload/chunk", mapping("/api/upload/chunk", RequestMethod.POST, "multipart/form-data"), UploadController.class, method);

    assertEquals("上传正式文件分片", model.getSummary());
    assertEquals(Collections.singletonList("file-upload"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("分片上传表单请求", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
  }

  @Test
  void shouldExtractMultipartBodyForUpload() throws Exception {
    Method method = UploadController.class.getMethod("upload", MultipartFile.class, HttpServletRequest.class);

    SimpleWebAPIModel model = extract("POST", "/api/upload", mapping("/api/upload", RequestMethod.POST, "multipart/form-data"), UploadController.class, method);

    assertEquals("上传文件", model.getSummary());
    assertEquals(Collections.singletonList("file-upload"), model.getTags());
    assertNotNull(model.getRequestBody());
    assertEquals("正式文件上传表单", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
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

  private com.ouroboros.webapi.openapi.ParameterModel onlyQueryParameter(SimpleWebAPIModel model) {
    return model.getParameters().stream()
        .filter(parameter -> ParameterLocation.QUERY.equals(parameter.getIn()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing query parameter"));
  }

  private com.ouroboros.webapi.openapi.ParameterModel onlyPathParameter(SimpleWebAPIModel model) {
    return model.getParameters().stream()
        .filter(parameter -> ParameterLocation.PATH.equals(parameter.getIn()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing path parameter"));
  }
}
