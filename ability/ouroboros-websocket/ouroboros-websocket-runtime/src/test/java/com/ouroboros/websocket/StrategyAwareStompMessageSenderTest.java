package com.ouroboros.websocket;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StrategyAwareStompMessageSenderTest {

  private static final String SENDER_CLASS_NAME = "com.ouroboros.websocket.StrategyAwareStompMessageSender";
  private static final String SOURCE_INSTANCE_ID = "instance-a";

  private WebSocketBrokerStrategy mockStrategy;
  private LocalWebSocketOperations localOperations;
  private Object sender;

  @BeforeEach
  void setUp() {
    mockStrategy = mock(WebSocketBrokerStrategy.class);
    localOperations = mock(LocalWebSocketOperations.class);
    sender = instantiateStrategyAwareSender(mockStrategy, localOperations, SOURCE_INSTANCE_ID);
  }

  @Test
  void shouldDelegateTopicSendToStrategy() {
    Object payload = new Object();

    invoke(sender, "send", new Class<?>[]{String.class, Object.class}, "/topic/test", payload);

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(mockStrategy).send(envelopeCaptor.capture(), same(localOperations));

    BrokerEnvelope envelope = envelopeCaptor.getValue();
    assertThat(envelope.getType()).isEqualTo(BrokerEnvelopeType.TOPIC);
    assertThat(envelope.getDestination()).isEqualTo("/topic/test");
    assertThat(envelope.getPayload()).isSameAs(payload);
  }

  @Test
  void shouldDelegateUserSendToStrategy() {
    Object payload = new Object();

    invoke(sender, "sendToUser",
        new Class<?>[]{String.class, String.class, Object.class},
        "user123", "/queue/test", payload);

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(mockStrategy).send(envelopeCaptor.capture(), same(localOperations));

    BrokerEnvelope envelope = envelopeCaptor.getValue();
    assertThat(envelope.getType()).isEqualTo(BrokerEnvelopeType.USER);
    assertThat(envelope.getUser()).isEqualTo("user123");
    assertThat(envelope.getDestination()).isEqualTo("/queue/test");
    assertThat(envelope.getPayload()).isSameAs(payload);
  }

  @Test
  void shouldDelegateSessionSendToStrategy() {
    Object payload = new Object();

    invoke(sender, "sendToQueue",
        new Class<?>[]{String.class, String.class, Object.class},
        "session123", "/queue/test", payload);

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(mockStrategy).send(envelopeCaptor.capture(), same(localOperations));

    BrokerEnvelope envelope = envelopeCaptor.getValue();
    assertThat(envelope.getType()).isEqualTo(BrokerEnvelopeType.SESSION);
    assertThat(envelope.getSessionId()).isEqualTo("session123");
    assertThat(envelope.getDestination()).isEqualTo("/queue/test");
    assertThat(envelope.getPayload()).isSameAs(payload);
  }

  @Test
  void shouldIncludeSourceInstanceIdInEnvelope() {
    invoke(sender, "send", new Class<?>[]{String.class, Object.class}, "/topic/one", "payload-1");
    invoke(sender, "sendToUser",
        new Class<?>[]{String.class, String.class, Object.class},
        "user123", "/queue/two", "payload-2");
    invoke(sender, "sendToQueue",
        new Class<?>[]{String.class, String.class, Object.class},
        "session123", "/queue/three", "payload-3");

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(mockStrategy, times(3)).send(envelopeCaptor.capture(), same(localOperations));

    List<BrokerEnvelope> envelopes = envelopeCaptor.getAllValues();
    assertThat(envelopes)
        .extracting(BrokerEnvelope::getSourceInstanceId)
        .containsExactly(SOURCE_INSTANCE_ID, SOURCE_INSTANCE_ID, SOURCE_INSTANCE_ID);
  }

  @Test
  void shouldGenerateUniqueMessageId() {
    invoke(sender, "send", new Class<?>[]{String.class, Object.class}, "/topic/one", "payload-1");
    invoke(sender, "send", new Class<?>[]{String.class, Object.class}, "/topic/two", "payload-2");

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(mockStrategy, times(2)).send(envelopeCaptor.capture(), same(localOperations));

    List<BrokerEnvelope> envelopes = envelopeCaptor.getAllValues();
    assertThat(envelopes.get(0).getMessageId()).isNotBlank();
    assertThat(envelopes.get(1).getMessageId()).isNotBlank();
    assertThat(envelopes.get(0).getMessageId()).isNotEqualTo(envelopes.get(1).getMessageId());
  }

  private static Object instantiateStrategyAwareSender(WebSocketBrokerStrategy strategy,
                                                       LocalWebSocketOperations localOperations,
                                                       String sourceInstanceId) {
    try {
      Class<?> senderClass = Class.forName(SENDER_CLASS_NAME);
      Constructor<?> constructor = senderClass.getDeclaredConstructor(
          WebSocketBrokerStrategy.class,
          LocalWebSocketOperations.class,
          String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(strategy, localOperations, sourceInstanceId);
    } catch (ClassNotFoundException e) {
      fail("StrategyAwareStompMessageSender must exist in ouroboros-websocket-runtime", e);
    } catch (NoSuchMethodException e) {
      fail("StrategyAwareStompMessageSender must expose constructor (WebSocketBrokerStrategy, LocalWebSocketOperations, String)", e);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      fail("StrategyAwareStompMessageSender could not be instantiated for contract testing", e);
    }
    return null;
  }

  private static void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
    try {
      Method method = target.getClass().getMethod(methodName, parameterTypes);
      method.invoke(target, args);
    } catch (NoSuchMethodException e) {
      fail("Expected method was not found on StrategyAwareStompMessageSender: " + methodName, e);
    } catch (IllegalAccessException e) {
      fail("Expected method was not accessible on StrategyAwareStompMessageSender: " + methodName, e);
    } catch (InvocationTargetException e) {
      Throwable targetException = e.getTargetException();
      fail("StrategyAwareStompMessageSender threw an exception while invoking " + methodName, targetException);
    }
  }
}
