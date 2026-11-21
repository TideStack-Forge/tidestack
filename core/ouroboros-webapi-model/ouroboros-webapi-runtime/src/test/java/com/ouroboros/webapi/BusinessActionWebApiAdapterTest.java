package com.ouroboros.webapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.ouroboros.businessmodel.action.BusinessActionInvocation;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;

class BusinessActionWebApiAdapterTest {
  @Test
  void fileOutputUsesTheSameActionInvocationAndWritesNativeBinaryContent(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var path = java.nio.file.Files.write(directory.resolve("result.tmp"), new byte[]{1, 2, 3});
    var result = com.ouroboros.util.FileResult.temporary(path, "订单.xlsx", "application/octet-stream");
    var runtime = mock(BusinessActionRuntime.class); when(runtime.invoke(any())).thenReturn(result);
    var model = SimpleWebAPIModel.builder().get("/api/orders/{id}").handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("businessModel", "sales.Order").extraProp("action", "getOne").extraProp("actionInputMapping", Map.of()).build();
    var request = request("42");
    var response = BusinessActionWebApiAdapter.buildHandler(model, runtime, behaviors()).handle(request);
    var http = new org.springframework.mock.web.MockHttpServletResponse();
    response.writeTo(request.servletRequest(), http, mock(ServerResponse.Context.class));
    assertEquals("application/octet-stream", http.getContentType()); assertEquals("3", http.getHeader("Content-Length"));
    assertArrayEquals(new byte[]{1, 2, 3}, http.getContentAsByteArray());
    org.junit.jupiter.api.Assertions.assertTrue(http.getHeader("Content-Disposition").contains("filename*="));
    org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(path)); verify(runtime).invoke(any());
  }

  @Test
  void fileResponsesReleaseTheTemporaryFileOnDisconnectOrInvalidHeader(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var path = java.nio.file.Files.writeString(directory.resolve("disconnect.tmp"), "ready");
    var result = com.ouroboros.util.FileResult.temporary(path, "file.txt", "text/plain");
    var response = mock(jakarta.servlet.http.HttpServletResponse.class);
    when(response.getOutputStream()).thenThrow(new java.io.IOException("disconnected"));
    org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> com.ouroboros.web.FileResponses.write(result, response));
    org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(path));
    var invalid = java.nio.file.Files.writeString(directory.resolve("invalid.tmp"), "ready");
    var invalidResult = com.ouroboros.util.FileResult.temporary(invalid, "file.txt", "invalid-media-type");
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> com.ouroboros.web.FileResponses.write(invalidResult, response));
    org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(invalid));
  }

  @Test
  void forwardsMultipartImportFilesAsNativeResourcesToTheCanonicalActionInput() throws Exception {
    BusinessActionRuntime runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any(BusinessActionInvocation.class))).thenReturn(Map.of("imported", 2));
    WebAPIModel model = SimpleWebAPIModel.builder()
        .post("/api/orders/import")
        .handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("businessModel", "sales.Order")
        .extraProp("action", "import")
        .extraProp("actionInputMapping", Map.of("data", "$.body"))
        .build();
    ServerRequest request = request("import");
    when(request.methodName()).thenReturn("POST");
    when(request.headers().contentType()).thenReturn(Optional.of(MediaType.MULTIPART_FORM_DATA));
    var parts = new LinkedMultiValueMap<String, jakarta.servlet.http.Part>();
    byte[] content = {1, 2, 3};
    parts.add("file", new MockPart("file", "orders.xlsx", content));
    when(request.multipartData()).thenReturn(parts);

    assertEquals(200, BusinessActionWebApiAdapter.buildHandler(model, runtime, behaviors()).handle(request).statusCode().value());

    var invocation = org.mockito.ArgumentCaptor.forClass(BusinessActionInvocation.class);
    verify(runtime).invoke(invocation.capture());
    assertEquals("import", invocation.getValue().actionName());
    MultipartFile file = assertInstanceOf(MultipartFile.class, invocation.getValue().input().data() instanceof Map<?, ?> data ? data.get("file") : null);
    assertEquals("orders.xlsx", file.getOriginalFilename());
    assertArrayEquals(content, file.getBytes());
  }

  @Test
  void invokesTheActionRuntimeWithThePublishedModelActionAndPathObjectReference() throws Exception {
    BusinessActionRuntime runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any(BusinessActionInvocation.class))).thenReturn(Map.of("id", "42"));
    WebAPIModel model = SimpleWebAPIModel.builder()
        .get("/api/orders/{id}")
        .handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("businessModel", "sales.Order")
        .extraProp("action", "getOne")
        .extraProp("actionInputMapping", Map.of("params.record.id", "$.pathVariables.id"))
        .build();
    ServerRequest request = request("42", Map.of("comment", "confirmed"));

    ServerResponse response = BusinessActionWebApiAdapter.buildHandler(model, runtime, behaviors()).handle(request);

    assertEquals(200, response.statusCode().value());
    org.mockito.ArgumentCaptor<BusinessActionInvocation> invocation =
        org.mockito.ArgumentCaptor.forClass(BusinessActionInvocation.class);
    verify(runtime).invoke(invocation.capture());
    assertEquals("sales.Order", invocation.getValue().modelFullName());
    assertEquals("getOne", invocation.getValue().actionName());
    assertEquals(Map.of("record", Map.of("id", "42")), invocation.getValue().input().params());
    org.junit.jupiter.api.Assertions.assertFalse(invocation.getValue().input().hasData());
  }

  private static com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider behaviors() {
    var provider = mock(com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider.class);
    var getOne = new com.ouroboros.businessmodel.behavior.ActionDefinition();
    getOne.setName("getOne");
    var upload = new com.ouroboros.businessmodel.behavior.ActionDefinition();
    upload.setName("import");
    var behavior = new com.ouroboros.businessmodel.behavior.BusinessModelBehavior("sales.Order",
        java.util.List.of(getOne, upload), java.util.List.of(), java.util.List.of());
    when(provider.getBusinessModelBehavior("sales.Order")).thenReturn(Optional.of(behavior));
    return provider;
  }

  private static ServerRequest request(String id) throws Exception {
    return request(id, Map.of());
  }

  private static ServerRequest request(String id, Map<String, Object> body) throws Exception {
    ServerRequest request = mock(ServerRequest.class);
    var servlet = new org.springframework.mock.web.MockHttpServletRequest();
    servlet.setContent(new byte[]{1});
    when(request.servletRequest()).thenReturn(servlet);
    ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
    when(request.methodName()).thenReturn("GET");
    when(request.uri()).thenReturn(URI.create("http://localhost/api/orders/" + id));
    when(request.path()).thenReturn("/api/orders/" + id);
    when(request.pathVariables()).thenReturn(Map.of("id", id));
    when(request.remoteAddress()).thenReturn(Optional.empty());
    when(request.headers()).thenReturn(headers);
    when(headers.asHttpHeaders()).thenReturn(new HttpHeaders());
    when(headers.contentType()).thenReturn(Optional.empty());
    when(request.params()).thenReturn(new LinkedMultiValueMap<>());
    when(request.body(Map.class)).thenReturn(body);
    return request;
  }
}
