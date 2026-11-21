package com.ouroboros.security.authorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.security.controller.AuthProviderController;

class AnnotationAnonymousApiProviderTest {

  @Test
  void shouldExposeAnonymousApisWhenRequestMappingUsesLegacyPatternsCondition() throws Exception {
    Method method = AuthProviderController.class.getMethod("listProviders");
    HandlerMethod handlerMethod = new HandlerMethod(new AuthProviderController(), method);
    RequestMappingInfo requestMappingInfo = new RequestMappingInfo(
        new PatternsRequestCondition("/api/auth/providers"),
        new RequestMethodsRequestCondition(RequestMethod.GET),
        null,
        null,
        null,
        null,
        null
    );

    List<String> anonymousApis = new TestAnnotationAnonymousApiProvider(
        Collections.singletonMap(requestMappingInfo, handlerMethod)
    ).getAnonymousApis();

    assertEquals(Collections.singletonList("GET /api/auth/providers"), anonymousApis);
  }

  private static class TestAnnotationAnonymousApiProvider extends AnnotationAnonymousApiProvider {
    private final Map<RequestMappingInfo, HandlerMethod> handlerMethods;

    private TestAnnotationAnonymousApiProvider(Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
      this.handlerMethods = handlerMethods;
    }

    @Override
    protected Map<RequestMappingInfo, HandlerMethod> getHandlerMethodMap() {
      return handlerMethods;
    }
  }
}
