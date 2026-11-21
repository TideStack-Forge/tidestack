package com.ouroboros.websocket;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

class DefaultStompInitSupportTest {

  private static final String INIT_REQUEST_CLASS = "com.ouroboros.websocket.InitRequest";
  private static final String INIT_REQUEST_CONTEXT_CLASS = "com.ouroboros.websocket.InitRequestContext";
  private static final String INIT_REQUEST_HANDLER_CLASS = "com.ouroboros.websocket.InitRequestHandler";
  private static final String INIT_REPLY_SENDER_CLASS = "com.ouroboros.websocket.InitReplySender";
  private static final String INIT_ROUTE_DEFINITION_CLASS = "com.ouroboros.websocket.InitRouteDefinition";
  private static final String INIT_REPLY_FRAME_CLASS = "com.ouroboros.websocket.InitReplyFrame";

  private DefaultStompInitSupport initSupport;
  private DefaultInitRequestValidator initRequestValidator;
  private Object request;
  private Object context;
  private Object handler;
  private Object replySender;
  private List<Object> sentFrames;
  private Class<?> requestClass;
  private Class<?> contextClass;
  private Class<?> handlerClass;
  private Class<?> replySenderClass;
  private Class<?> routeDefinitionClass;

  @BeforeEach
  void setUp() {
    initRequestValidator = new DefaultInitRequestValidator();
    initSupport = new DefaultStompInitSupport(initRequestValidator);
    requestClass = loadClass(INIT_REQUEST_CLASS);
    contextClass = loadClass(INIT_REQUEST_CONTEXT_CLASS);
    handlerClass = loadClass(INIT_REQUEST_HANDLER_CLASS);
    replySenderClass = loadClass(INIT_REPLY_SENDER_CLASS);
    routeDefinitionClass = loadClass(INIT_ROUTE_DEFINITION_CLASS);
    loadClass(INIT_REPLY_FRAME_CLASS);

    request = proxy(requestClass, (proxy, method, args) -> {
      if ("getSubscriptionToken".equals(method.getName())) {
        return "token-1";
      }
      return method.getDefaultValue();
    });
    context = proxy(contextClass, (proxy, method, args) -> {
      switch (method.getName()) {
        case "getSessionId":
          return "session-1";
        case "getUser":
          return "user-1";
        case "getDestination":
          return "/app/init";
        case "getHeaders":
          Map<String, Object> headers = new LinkedHashMap<String, Object>();
          headers.put("simpSessionId", "session-1");
          return headers;
        default:
          return method.getDefaultValue();
      }
    });
    sentFrames = new ArrayList<Object>();
    replySender = proxy(replySenderClass, (proxy, method, args) -> {
      if ("send".equals(method.getName())) {
        sentFrames.add(args[0]);
        return null;
      }
      return method.getDefaultValue();
    });
  }

  @Test
  void shouldEmitInitDataFramesThenSingleInitComplete() {
    handler = handler(routeDefinition("/app/init", "/queue/init", "token-1"), Arrays.<Object>asList("data-1", "data-2"), null, null);

    invokeInit(request, context, handler, replySender);

    assertThat(sentFrames).hasSize(3);
    assertThat(sentFrames).extracting(this::kind).containsExactly("init-data", "init-data", "init-complete");
    assertThat(sentFrames).extracting(this::payload).containsExactly("data-1", "data-2", null);
    assertThat(sentFrames).extracting(this::sessionId).containsOnly("session-1");
    assertThat(sentFrames).extracting(this::destination).containsOnly("/queue/init");
    assertThat(headers(sentFrames.get(0)).get("subscriptionToken")).isEqualTo("token-1");
    assertThat(headers(sentFrames.get(0)).get("phase")).isEqualTo("init-data");
    assertThat(headers(sentFrames.get(2)).get("subscriptionToken")).isEqualTo("token-1");
    assertThat(headers(sentFrames.get(2)).get("phase")).isEqualTo("init-complete");
  }

  @Test
  void shouldEmitOnlyInitCompleteWhenInitialPayloadIsEmpty() {
    handler = handler(routeDefinition("/app/init", "/queue/init", "token-1"), Collections.emptyList(), null, null);

    invokeInit(request, context, handler, replySender);

    assertThat(sentFrames).hasSize(1);
    assertThat(kind(sentFrames.get(0))).isEqualTo("init-complete");
    assertThat(payload(sentFrames.get(0))).isNull();
    assertThat(sessionId(sentFrames.get(0))).isEqualTo("session-1");
    assertThat(destination(sentFrames.get(0))).isEqualTo("/queue/init");
    assertThat(headers(sentFrames.get(0)).get("subscriptionToken")).isEqualTo("token-1");
    assertThat(headers(sentFrames.get(0)).get("phase")).isEqualTo("init-complete");
  }

  @Test
  void shouldEmitSingleInitErrorWithoutTrailingInitCompleteOnBusinessFailure() {
    handler = handler(routeDefinition("/app/init", "/queue/init", "token-1"), null, null, new IllegalStateException("business validation failed"));

    invokeInit(request, context, handler, replySender);

    assertThat(sentFrames).hasSize(1);
    assertThat(kind(sentFrames.get(0))).isEqualTo("init-error");
    assertThat(payload(sentFrames.get(0))).isEqualTo("business validation failed");
    assertThat(sessionId(sentFrames.get(0))).isEqualTo("session-1");
    assertThat(destination(sentFrames.get(0))).isEqualTo("/queue/init");
    assertThat(headers(sentFrames.get(0)).get("subscriptionToken")).isEqualTo("token-1");
    assertThat(headers(sentFrames.get(0)).get("phase")).isEqualTo("init-error");
  }

  @Test
  void shouldNotEmitInitErrorWhenProtocolValidationFailsBeforeRouteDerivation() {
    handler = handler(null, null, new IllegalArgumentException("invalid protocol request"), null);

    assertThatThrownBy(() -> invokeInit(request, context, handler, replySender))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid protocol request");

    assertThat(sentFrames).isEmpty();
  }

  @Test
  void shouldShortCircuitWithoutFramesWhenSessionIdIsInvalid() {
    handler = handler(routeDefinition("/app/init", "/queue/init", "token-1"), Collections.emptyList(), null, null);

    context = proxy(contextClass, (proxy, method, args) -> {
      switch (method.getName()) {
        case "getSessionId":
          return "   ";
        case "getUser":
          return "user-1";
        case "getDestination":
          return "/app/init";
        case "getHeaders":
          Map<String, Object> headers = new LinkedHashMap<String, Object>();
          headers.put("simpSessionId", "   ");
          return headers;
        default:
          return method.getDefaultValue();
      }
    });

    assertThatThrownBy(() -> invokeInit(request, context, handler, replySender))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sessionId must not be blank");

    assertThat(sentFrames).isEmpty();
  }

  @Test
  void shouldShortCircuitWithoutFramesWhenSubscriptionTokenIsInvalid() {
    handler = handler(routeDefinition("/app/init", "/queue/init", "token-1"), Collections.emptyList(), null, null);

    request = proxy(requestClass, (proxy, method, args) -> {
      if ("getSubscriptionToken".equals(method.getName())) {
        return "bad token";
      }
      return method.getDefaultValue();
    });

    assertThatThrownBy(() -> invokeInit(request, context, handler, replySender))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("subscriptionToken must be path-safe");

    assertThat(sentFrames).isEmpty();
  }

  @Test
  void shouldShortCircuitWithoutFramesWhenRouteIsPathUnsafe() {
    handler = handler(routeDefinition("/app/../init", "/queue/init", "token-1"), Collections.emptyList(), null, null);

    assertThatThrownBy(() -> invokeInit(request, context, handler, replySender))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("request destination must be path-safe");

    assertThat(sentFrames).isEmpty();
  }

  private Object handler(Object routeDefinition, Iterable<?> initialPayloadItems, RuntimeException routeError, RuntimeException payloadError) {
    return proxy(handlerClass, (proxy, method, args) -> {
      switch (method.getName()) {
        case "getRouteDefinition":
          if (routeError != null) {
            throw routeError;
          }
          return routeDefinition;
        case "getInitialPayloadItems":
          if (payloadError != null) {
            throw payloadError;
          }
          if (initialPayloadItems != null) {
            return initialPayloadItems;
          }
          throw new IllegalStateException("business validation failed");
        default:
          return method.getDefaultValue();
      }
    });
  }

  private Object routeDefinition(String requestDestination, String replyDestination, String subscriptionToken) {
    try {
      Constructor<?> constructor = routeDefinitionClass.getDeclaredConstructor(String.class, String.class, String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(requestDestination, replyDestination, subscriptionToken);
    } catch (NoSuchMethodException e) {
      fail("InitRouteDefinition must expose constructor (String, String, String)", e);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      fail("InitRouteDefinition could not be instantiated for contract testing", e);
    }
    return null;
  }

  private void invokeInit(Object request, Object context, Object handler, Object replySender) {
    try {
      Method init = initSupport.getClass().getMethod("init", requestClass, contextClass, handlerClass, replySenderClass);
      init.invoke(initSupport, request, context, handler, replySender);
    } catch (NoSuchMethodException e) {
      fail("DefaultStompInitSupport must expose init(InitRequest, InitRequestContext, InitRequestHandler, InitReplySender)", e);
    } catch (IllegalAccessException e) {
      fail("DefaultStompInitSupport init method must be accessible", e);
    } catch (InvocationTargetException e) {
      Throwable targetException = e.getTargetException();
      if (targetException instanceof RuntimeException) {
        throw (RuntimeException) targetException;
      }
      fail("DefaultStompInitSupport threw a checked exception", targetException);
    }
  }

  private String kind(Object frame) {
    return (String) invoke(frame, "getKind");
  }

  private Object payload(Object frame) {
    return invoke(frame, "getPayload");
  }

  private String sessionId(Object frame) {
    return (String) invoke(frame, "getSessionId");
  }

  private String destination(Object frame) {
    return (String) invoke(frame, "getDestination");
  }

  private Map<?, ?> headers(Object frame) {
    return (Map<?, ?>) invoke(frame, "getHeaders");
  }

  private Object invoke(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException e) {
      fail("Expected method was not found on " + target.getClass().getName() + ": " + methodName, e);
    } catch (IllegalAccessException e) {
      fail("Expected method was not accessible on " + target.getClass().getName() + ": " + methodName, e);
    } catch (InvocationTargetException e) {
      fail("Unexpected exception invoking " + methodName + " on " + target.getClass().getName(), e.getTargetException());
    }
    return null;
  }

  private static Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      fail(className + " must exist for init support contract testing", e);
    }
    return null;
  }

  private static Object proxy(Class<?> interfaceClass, InvocationHandler handler) {
    return Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[]{interfaceClass}, handler);
  }
}
