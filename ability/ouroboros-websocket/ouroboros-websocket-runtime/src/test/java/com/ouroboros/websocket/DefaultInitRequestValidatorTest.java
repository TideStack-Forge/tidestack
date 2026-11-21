package com.ouroboros.websocket;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

class DefaultInitRequestValidatorTest {

  private static final String INIT_REQUEST_CLASS_NAME = "com.ouroboros.websocket.InitRequest";
  private static final String INIT_REQUEST_CONTEXT_CLASS_NAME = "com.ouroboros.websocket.InitRequestContext";
  private static final String INIT_ROUTE_DEFINITION_CLASS_NAME = "com.ouroboros.websocket.InitRouteDefinition";

  private DefaultInitRequestValidator validator;

  @BeforeEach
  void setUp() {
    validator = new DefaultInitRequestValidator();
  }

  @Test
  void shouldRejectBlankSessionId() {
    assertThatThrownBy(() -> invokeValidate(
        request("token-1"),
        context("   "),
        routeDefinition("/app/init", "/queue/init", "token-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sessionId must not be blank");
  }

  @Test
  void shouldRejectBlankSubscriptionToken() {
    assertThatThrownBy(() -> invokeValidate(
        request("  "),
        context("session-1"),
        routeDefinition("/app/init", "/queue/init", "  ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("subscriptionToken must not be blank");
  }

  @Test
  void shouldRejectMalformedSubscriptionToken() {
    assertThatThrownBy(() -> invokeValidate(
        request("bad token"),
        context("session-1"),
        routeDefinition("/app/init", "/queue/init", "bad token")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("subscriptionToken must be path-safe");
  }

  @Test
  void shouldRejectRequestDestinationOutsideAppNamespace() {
    assertThatThrownBy(() -> invokeValidate(
        request("token-1"),
        context("session-1"),
        routeDefinition("/topic/init", "/queue/init", "token-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("request destination must stay within /app namespace");
  }

  @Test
  void shouldRejectReplyDestinationOutsideQueueNamespace() {
    assertThatThrownBy(() -> invokeValidate(
        request("token-1"),
        context("session-1"),
        routeDefinition("/app/init", "/topic/init", "token-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reply destination must stay within /queue namespace");
  }

  @Test
  void shouldRejectPathUnsafeRequestDestination() {
    assertThatThrownBy(() -> invokeValidate(
        request("token-1"),
        context("session-1"),
        routeDefinition("/app/../init", "/queue/init", "token-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("request destination must be path-safe");
  }

  @Test
  void shouldRejectPathUnsafeReplyDestination() {
    assertThatThrownBy(() -> invokeValidate(
        request("token-1"),
        context("session-1"),
        routeDefinition("/app/init", "/queue//init", "token-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reply destination must be path-safe");
  }

  @Test
  void shouldAcceptPathSafeInitConfiguration() {
    invokeValidate(
        request("token-1"),
        context("session-1"),
        routeDefinition("/app/init", "/queue/init", "token-1"));
  }

  private void invokeValidate(Object request, Object context, Object routeDefinition) {
    try {
      Class<?> requestClass = Class.forName(INIT_REQUEST_CLASS_NAME);
      Class<?> contextClass = Class.forName(INIT_REQUEST_CONTEXT_CLASS_NAME);
      Class<?> routeDefinitionClass = Class.forName(INIT_ROUTE_DEFINITION_CLASS_NAME);
      Method validate = validator.getClass().getMethod("validate", requestClass, contextClass, routeDefinitionClass);
      validate.invoke(validator, request, context, routeDefinition);
    } catch (ClassNotFoundException e) {
      fail("Init validator contract types must exist in ouroboros-websocket-core", e);
    } catch (NoSuchMethodException e) {
      fail("DefaultInitRequestValidator must expose validate(InitRequest, InitRequestContext, InitRouteDefinition)", e);
    } catch (IllegalAccessException e) {
      fail("DefaultInitRequestValidator validate method must be accessible", e);
    } catch (InvocationTargetException e) {
      Throwable targetException = e.getTargetException();
      if (targetException instanceof RuntimeException) {
        throw (RuntimeException) targetException;
      }
      fail("DefaultInitRequestValidator threw a checked exception", targetException);
    }
  }

  private static Object request(String subscriptionToken) {
    try {
      Class<?> requestClass = Class.forName(INIT_REQUEST_CLASS_NAME);
      InvocationHandler handler = (proxy, method, args) -> {
        if ("getSubscriptionToken".equals(method.getName())) {
          return subscriptionToken;
        }
        return method.getDefaultValue();
      };
      return Proxy.newProxyInstance(
          requestClass.getClassLoader(),
          new Class<?>[]{requestClass},
          handler);
    } catch (ClassNotFoundException e) {
      fail("InitRequest must exist in ouroboros-websocket-core", e);
    }
    return null;
  }

  private static Object context(String sessionId) {
    try {
      Class<?> contextClass = Class.forName(INIT_REQUEST_CONTEXT_CLASS_NAME);
      Map<String, Object> headers = new LinkedHashMap<>();
      headers.put("simpSessionId", sessionId);
      InvocationHandler handler = (proxy, method, args) -> {
        switch (method.getName()) {
          case "getSessionId":
            return sessionId;
          case "getUser":
            return "user-1";
          case "getDestination":
            return "/app/init";
          case "getHeaders":
            return headers;
          default:
            return method.getDefaultValue();
        }
      };
      return Proxy.newProxyInstance(
          contextClass.getClassLoader(),
          new Class<?>[]{contextClass},
          handler);
    } catch (ClassNotFoundException e) {
      fail("InitRequestContext must exist in ouroboros-websocket-core", e);
    }
    return null;
  }

  private static Object routeDefinition(String requestDestination,
                                        String replyDestination,
                                        String subscriptionToken) {
    try {
      Class<?> routeDefinitionClass = Class.forName(INIT_ROUTE_DEFINITION_CLASS_NAME);
      Constructor<?> constructor = routeDefinitionClass.getDeclaredConstructor(String.class, String.class, String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(requestDestination, replyDestination, subscriptionToken);
    } catch (ClassNotFoundException e) {
      fail("InitRouteDefinition must exist in ouroboros-websocket-core", e);
    } catch (NoSuchMethodException e) {
      fail("InitRouteDefinition must expose constructor (String, String, String)", e);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      fail("InitRouteDefinition could not be instantiated for contract testing", e);
    }
    return null;
  }
}
