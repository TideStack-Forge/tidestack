package com.ouroboros.websocket;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DefaultStompCompensationRegistrarTest {

  @Test
  void shouldActivateOnDoneWithDefaultRequestDestinationAndUseSenderForTerminalFrames() {
    StompMessageSubscriber subscriber = mock(StompMessageSubscriber.class);
    StompMessageSender sender = mock(StompMessageSender.class);
    DefaultInitRequestValidator validator = new DefaultInitRequestValidator();
    Object registrar = new DefaultStompCompensationRegistrar(subscriber, sender, validator);

    ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
    Object registration = invokeUserTopic(registrar, "/user/queue/app/{appName}/logs");
    invoke(registration, "initialData", new Class<?>[]{java.util.function.Function.class}, new java.util.function.Function<Object, Iterable<?>>() {
      @Override
      public Iterable<?> apply(Object context) {
        return Arrays.<Object>asList(invoke(context, "getSessionId"), invoke(context, "pathVariable", new Class<?>[]{String.class}, "appName"));
      }
    });

    verifyNoInteractions(subscriber);

    invoke(registration, "done");

    verify(subscriber).onSend(eq("/app/user/queue/app/{appName}/logs/init"), handlerCaptor.capture());

    Message<byte[]> message = initMessage("/app/user/queue/app/acme/logs/init", "session-1", "token-1");
    handlerCaptor.getValue().handleMessage(message);

    ArgumentCaptor<InitReplyFrame> frameCaptor = ArgumentCaptor.forClass(InitReplyFrame.class);
    verify(sender, times(3)).sendToQueue(eq("session-1"), eq("/queue/init/user/queue/app/acme/logs/token-1"), frameCaptor.capture());
    assertThat(frameCaptor.getAllValues()).extracting(InitReplyFrame::getKind)
        .containsExactly("init-data", "init-data", "init-complete");
  }

  @Test
  void shouldRespectExplicitInitRequestDestinationOverrideWithoutChangingReplyDestination() {
    StompMessageSubscriber subscriber = mock(StompMessageSubscriber.class);
    StompMessageSender sender = mock(StompMessageSender.class);
    DefaultInitRequestValidator validator = new DefaultInitRequestValidator();
    Object registrar = new DefaultStompCompensationRegistrar(subscriber, sender, validator);

    ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
    Object registration = invokeUserTopic(registrar, "/user/queue/app/{appName}/logs");
    invoke(registration, "requestDestination", new Class<?>[]{String.class}, "/app/custom/{appName}/logs/init");
    verifyNoInteractions(subscriber);
    invoke(registration, "initialData", new Class<?>[]{java.util.function.Function.class}, new java.util.function.Function<Object, Iterable<?>>() {
      @Override
      public Iterable<?> apply(Object context) {
        return Arrays.<Object>asList(invoke(context, "pathVariable", new Class<?>[]{String.class}, "appName"));
      }
    });

    verifyNoInteractions(subscriber);

    invoke(registration, "done");

    verify(subscriber).onSend(eq("/app/custom/{appName}/logs/init"), handlerCaptor.capture());

    Message<byte[]> message = initMessage("/app/custom/acme/logs/init", "session-2", "token-2");
    handlerCaptor.getValue().handleMessage(message);

    ArgumentCaptor<InitReplyFrame> frameCaptor = ArgumentCaptor.forClass(InitReplyFrame.class);
    verify(sender, times(2)).sendToQueue(eq("session-2"), eq("/queue/init/user/queue/app/acme/logs/token-2"), frameCaptor.capture());
    assertThat(frameCaptor.getAllValues()).extracting(InitReplyFrame::getKind)
        .containsExactly("init-data", "init-complete");
  }

  @Test
  void shouldAllowInitialDataBeforeRequestDestinationAndOnlyActivateOnDone() {
    StompMessageSubscriber subscriber = mock(StompMessageSubscriber.class);
    StompMessageSender sender = mock(StompMessageSender.class);
    DefaultInitRequestValidator validator = new DefaultInitRequestValidator();
    Object registrar = new DefaultStompCompensationRegistrar(subscriber, sender, validator);

    ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
    Object registration = invokeUserTopic(registrar, "/user/queue/app/{appName}/logs");
    invoke(registration, "initialData", new Class<?>[]{java.util.function.Function.class}, new java.util.function.Function<Object, Iterable<?>>() {
      @Override
      public Iterable<?> apply(Object context) {
        return Arrays.<Object>asList(invoke(context, "getSessionId"));
      }
    });
    verifyNoInteractions(subscriber);

    invoke(registration, "requestDestination", new Class<?>[]{String.class}, "/app/custom/{appName}/logs/init");
    verifyNoInteractions(subscriber);

    invoke(registration, "done");

    verify(subscriber).onSend(eq("/app/custom/{appName}/logs/init"), handlerCaptor.capture());

    Message<byte[]> message = initMessage("/app/custom/acme/logs/init", "session-3", "token-3");
    handlerCaptor.getValue().handleMessage(message);

    ArgumentCaptor<InitReplyFrame> frameCaptor = ArgumentCaptor.forClass(InitReplyFrame.class);
    verify(sender, times(2)).sendToQueue(eq("session-3"), eq("/queue/init/user/queue/app/acme/logs/token-3"), frameCaptor.capture());
    assertThat(frameCaptor.getAllValues()).extracting(InitReplyFrame::getKind)
        .containsExactly("init-data", "init-complete");
  }

  @Test
  void shouldRejectDoneWithoutInitialData() {
    StompMessageSubscriber subscriber = mock(StompMessageSubscriber.class);
    StompMessageSender sender = mock(StompMessageSender.class);
    DefaultInitRequestValidator validator = new DefaultInitRequestValidator();
    Object registrar = new DefaultStompCompensationRegistrar(subscriber, sender, validator);

    Object registration = invokeUserTopic(registrar, "/user/queue/app/{appName}/logs");
    invoke(registration, "requestDestination", new Class<?>[]{String.class}, "/app/custom/{appName}/logs/init");

    try {
      invoke(registration, "done");
      fail("Expected registration to reject done without an initial data provider");
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).contains("initial data provider");
      assertThat(expected.getMessage()).contains("destination");
    }

    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldRejectBlankRequestDestinationOverride() {
    StompMessageSubscriber subscriber = mock(StompMessageSubscriber.class);
    StompMessageSender sender = mock(StompMessageSender.class);
    DefaultInitRequestValidator validator = new DefaultInitRequestValidator();
    Object registrar = new DefaultStompCompensationRegistrar(subscriber, sender, validator);

    try {
      Object registration = invokeUserTopic(registrar, "/user/queue/app/{appName}/logs");
      invoke(registration, "requestDestination", new Class<?>[]{String.class}, "   ");
      fail("Expected registration to reject blank request destination");
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("request destination");
    }
  }

  private Message<byte[]> initMessage(String destination, String sessionId, String subscriptionToken) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.setLeaveMutable(true);
    accessor.setDestination(destination);
    accessor.setSessionId(sessionId);
    accessor.setNativeHeader("subscriptionToken", subscriptionToken);
    accessor.setUser(() -> "user-1");
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private Object invokeUserTopic(Object registrar, String destination) {
    return invoke(registrar, "userTopic", new Class<?>[]{String.class}, destination);
  }

  private Object invoke(Object target, String methodName) {
    return invoke(target, methodName, new Class<?>[0]);
  }

  private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
    try {
      Method method = target.getClass().getMethod(methodName, parameterTypes);
      return method.invoke(target, args);
    } catch (NoSuchMethodException e) {
      fail("Expected method was not found on " + target.getClass().getName() + ": " + methodName, e);
    } catch (IllegalAccessException e) {
      fail("Expected method was not accessible on " + target.getClass().getName() + ": " + methodName, e);
    } catch (InvocationTargetException e) {
      Throwable targetException = e.getTargetException();
      if (targetException instanceof RuntimeException) {
        throw (RuntimeException) targetException;
      }
      fail("Unexpected exception invoking " + methodName + " on " + target.getClass().getName(), targetException);
    }
    return null;
  }
}
