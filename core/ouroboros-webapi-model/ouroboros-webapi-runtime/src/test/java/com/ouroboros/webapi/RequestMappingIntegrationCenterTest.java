package com.ouroboros.webapi;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestMappingIntegrationCenterTest {

  @Test
  void shouldPreserveActualRequestPrefixWhenBuildingHighCodeWebApiModel() throws Exception {
    RequestMappingIntegrationCenter integrationCenter = new RequestMappingIntegrationCenter();
    Method buildWebApiModel = RequestMappingIntegrationCenter.class.getDeclaredMethod(
        "buildWebApiModel",
        RequestMappingInfo.class,
        Class.class,
        Method.class
    );
    buildWebApiModel.setAccessible(true);

    Method controllerMethod = SampleController.class.getMethod("create", CreateRequest.class);
    RequestMappingInfo mappingInfo = RequestMappingInfo.paths("/api/document-libraries")
        .methods(RequestMethod.POST)
        .build();

    WebAPIModel model = (WebAPIModel) buildWebApiModel.invoke(
        integrationCenter,
        mappingInfo,
        SampleController.class,
        controllerMethod
    );

    assertEquals("/api/document-libraries", model.getPath());
    assertEquals("POST /api/document-libraries", model.getApi());
  }

  @Test
  void shouldContinueToDownstreamLogicFlowWhenOnlyHighCodeMethodMismatches() throws Exception {
    TestableRequestMappingIntegrationCenter integrationCenter = new TestableRequestMappingIntegrationCenter();
    integrationCenter.register(Collections.singletonList(SimpleWebAPIModel.builder()
        .api("PUT", "/api/uws/weight-ticket/{id}")
        .scope(WebAPIScope.INTERNAL)
        .handlerType(HandlerType.LOGIC_FLOW)
        .build()));

    RequestMappingInfo highCodeGetMapping = RequestMappingInfo.paths("/api/uws/weight-ticket/{id}")
        .methods(RequestMethod.GET)
        .build();
    MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/uws/weight-ticket/123");

    assertNull(integrationCenter.handleNoMatchForTest(Set.of(highCodeGetMapping), request));
  }

  @Test
  void shouldKeepMethodNotAllowedWhenNoDownstreamLogicFlowMatches() {
    TestableRequestMappingIntegrationCenter integrationCenter = new TestableRequestMappingIntegrationCenter();
    RequestMappingInfo highCodeGetMapping = RequestMappingInfo.paths("/api/uws/weight-ticket/{id}")
        .methods(RequestMethod.GET)
        .build();
    MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/uws/weight-ticket/123");

    assertThrows(
        HttpRequestMethodNotSupportedException.class,
        () -> integrationCenter.handleNoMatchForTest(Set.of(highCodeGetMapping), request)
    );
  }

  @Test
  void shouldExposeDeclaredRequestMappingMetadataWhenDefaultRegistrationIsSuppressed() throws Exception {
    TestableRequestMappingIntegrationCenter integrationCenter = new TestableRequestMappingIntegrationCenter();

    assertNull(integrationCenter.getMappingForMethodForTest(
        TestController.class.getMethod("test"),
        TestController.class
    ));

    var mappings = integrationCenter.getRequestMappingMetadata();
    assertEquals(1, mappings.size());
    var mapping = mappings.iterator().next();
    assertEquals(TestController.class, mapping.getHandlerType());
    assertEquals(TestController.class.getMethod("test"), mapping.getMethod());
    assertEquals("GET /api/test/raw", mapping.getApi());
  }

  static class TestableRequestMappingIntegrationCenter extends RequestMappingIntegrationCenter {
    RequestMappingInfo getMappingForMethodForTest(java.lang.reflect.Method method, Class<?> handlerType) {
      return super.getMappingForMethod(method, handlerType);
    }

    Object handleNoMatchForTest(Set<RequestMappingInfo> infos, MockHttpServletRequest request) throws Exception {
      ServletRequestPathUtils.parseAndCache(request);
      return super.handleNoMatch(infos, request.getRequestURI(), request);
    }
  }

  static class SampleController {
    @PostMapping("/api/document-libraries")
    public String create(@RequestBody CreateRequest request) {
      return "ok";
    }
  }

  static class CreateRequest {
    public String name;
  }

  static class TestController {

    @GetMapping("/api/test/raw")
    public void test() {
    }
  }
}
