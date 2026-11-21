package com.ouroboros.websocket;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;

class DefaultInitReplySenderTest {

  private static final String SENDER_CLASS_NAME = "com.ouroboros.websocket.DefaultInitReplySender";
  private static final String REPLY_FRAME_CLASS_NAME = "com.ouroboros.websocket.InitReplyFrame";

  private StompMessageSender stompMessageSender;
  private Object sender;

  @BeforeEach
  void setUp() {
    stompMessageSender = mock(StompMessageSender.class);
    sender = instantiateSender(stompMessageSender);
  }

  @Test
  void shouldDelegateInitReplyToSessionQueueSend() {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("kind", "init");
    headers.put("subscriptionToken", "token-123");
    headers.put("traceId", "trace-123");
    Object frame = newReplyFrame("session-1", "/queue/app/init", "kind-a", "payload-1", headers);

    invoke(sender, "send", new Class<?>[]{replyFrameClass()}, frame);

    ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);

    verify(stompMessageSender).sendToQueue(
        sessionIdCaptor.capture(),
        destinationCaptor.capture(),
        payloadCaptor.capture(),
        headersCaptor.capture());

    assertThat(sessionIdCaptor.getValue()).isEqualTo("session-1");
    assertThat(destinationCaptor.getValue()).isEqualTo("/queue/app/init");
    assertThat(payloadCaptor.getValue()).isInstanceOf(Map.class);
    Map<?, ?> body = (Map<?, ?>) payloadCaptor.getValue();
    assertThat(body.get("kind")).isEqualTo("kind-a");
    assertThat(body.get("subscriptionToken")).isEqualTo("token-123");
    assertThat(body.get("payload")).isEqualTo("payload-1");
    Map<?, ?> headersBody = (Map<?, ?>) headersCaptor.getValue();
    assertThat(headersBody.get("kind")).isEqualTo("init");
    assertThat(headersBody.get("traceId")).isEqualTo("trace-123");

    verifyNoMoreInteractions(stompMessageSender);
  }

  private static Object instantiateSender(StompMessageSender stompMessageSender) {
    try {
      Class<?> senderClass = Class.forName(SENDER_CLASS_NAME);
      Constructor<?> constructor = senderClass.getDeclaredConstructor(StompMessageSender.class);
      constructor.setAccessible(true);
      return constructor.newInstance(stompMessageSender);
    } catch (ClassNotFoundException e) {
      fail("DefaultInitReplySender must exist in ouroboros-websocket-runtime", e);
    } catch (NoSuchMethodException e) {
      fail("DefaultInitReplySender must expose constructor (StompMessageSender)", e);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      fail("DefaultInitReplySender could not be instantiated for contract testing", e);
    }
    return null;
  }

  private static Object newReplyFrame(String sessionId,
                                      String destination,
                                      String kind,
                                      Object payload,
                                      Map<String, Object> headers) {
    try {
      Class<?> frameClass = Class.forName(REPLY_FRAME_CLASS_NAME);
      Constructor<?> constructor = frameClass.getDeclaredConstructor(
          String.class,
          String.class,
          String.class,
          Object.class,
          Map.class);
      constructor.setAccessible(true);
      return constructor.newInstance(sessionId, destination, kind, payload, headers);
    } catch (ClassNotFoundException e) {
      fail("InitReplyFrame must exist in ouroboros-websocket-core", e);
    } catch (NoSuchMethodException e) {
      fail("InitReplyFrame must expose constructor (String, String, String, Object, Map)", e);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      fail("InitReplyFrame could not be instantiated for contract testing", e);
    }
    return null;
  }

  private static Class<?> replyFrameClass() {
    try {
      return Class.forName(REPLY_FRAME_CLASS_NAME);
    } catch (ClassNotFoundException e) {
      fail("InitReplyFrame must exist in ouroboros-websocket-core", e);
    }
    return null;
  }

  private static void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
    try {
      Method method = target.getClass().getMethod(methodName, parameterTypes);
      method.invoke(target, args);
    } catch (NoSuchMethodException e) {
      fail("Expected method was not found on DefaultInitReplySender: " + methodName, e);
    } catch (IllegalAccessException e) {
      fail("Expected method was not accessible on DefaultInitReplySender: " + methodName, e);
    } catch (InvocationTargetException e) {
      Throwable targetException = e.getTargetException();
      fail("DefaultInitReplySender threw an exception while invoking " + methodName, targetException);
    }
  }
}
