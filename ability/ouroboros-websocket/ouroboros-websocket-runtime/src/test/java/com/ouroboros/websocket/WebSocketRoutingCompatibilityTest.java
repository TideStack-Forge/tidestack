package com.ouroboros.websocket;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketRoutingCompatibilityTest {

  private static final String SENDER_CLASS_NAME = "com.ouroboros.websocket.StrategyAwareStompMessageSender";

  @Test
  void shouldRewriteQueueSubscriptionWithSessionId() {
    StompWebSocketChannelInterceptor interceptor = new StompWebSocketChannelInterceptor(
        mock(TokenService.class),
        mock(UserCenter.class));

    Message<byte[]> message = subscribeMessage("/queue/foo", "session123");
    Message<?> rewrittenMessage = interceptor.preSend(message, mock(MessageChannel.class));

    assertThat(StompHeaderAccessor.wrap(rewrittenMessage).getDestination())
        .isEqualTo("/queue/foo-session123");
  }

  @Test
  void shouldPreserveSimpOrigDestinationInSessionSend() {
    WebSocketBrokerStrategy strategy = mock(WebSocketBrokerStrategy.class);
    LocalWebSocketOperations localOperations = mock(LocalWebSocketOperations.class);
    Object sender = newStrategyAwareSender(strategy, localOperations, "instance-a");

    invoke(sender, "sendToQueue",
        new Class<?>[]{String.class, String.class, Object.class},
        "session123", "/queue/test", "payload");

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(strategy).send(envelopeCaptor.capture(), same(localOperations));

    BrokerEnvelope envelope = envelopeCaptor.getValue();
    assertThat(envelope.getType()).isEqualTo(BrokerEnvelopeType.SESSION);
    assertThat(envelope.getSessionId()).isEqualTo("session123");
    assertThat(envelope.getDestination()).isEqualTo("/queue/test");
    assertThat(envelope.getHeaders()).containsEntry("simpOrigDestination", "/queue/test");
  }

  @Test
  void shouldSupportUserDestinationRouting() {
    WebSocketBrokerStrategy strategy = mock(WebSocketBrokerStrategy.class);
    LocalWebSocketOperations localOperations = mock(LocalWebSocketOperations.class);
    Object sender = newStrategyAwareSender(strategy, localOperations, "instance-a");

    invoke(sender, "sendToUser",
        new Class<?>[]{String.class, String.class, Object.class},
        "user123", "/queue/app/crm/logs", "payload");

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(strategy).send(envelopeCaptor.capture(), same(localOperations));

    BrokerEnvelope envelope = envelopeCaptor.getValue();
    assertThat(envelope.getType()).isEqualTo(BrokerEnvelopeType.USER);
    assertThat(envelope.getUser()).isEqualTo("user123");
    assertThat(envelope.getDestination()).isEqualTo("/queue/app/crm/logs");
  }

  @Test
  void shouldSupportTopicBroadcast() {
    WebSocketBrokerStrategy strategy = mock(WebSocketBrokerStrategy.class);
    LocalWebSocketOperations localOperations = mock(LocalWebSocketOperations.class);
    Object sender = newStrategyAwareSender(strategy, localOperations, "instance-a");

    invoke(sender, "send", new Class<?>[]{String.class, Object.class}, "/topic/app/crm/logs", "payload");

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(strategy).send(envelopeCaptor.capture(), same(localOperations));

    BrokerEnvelope envelope = envelopeCaptor.getValue();
    assertThat(envelope.getType()).isEqualTo(BrokerEnvelopeType.TOPIC);
    assertThat(envelope.getDestination()).isEqualTo("/topic/app/crm/logs");
  }

  @Test
  void shouldSupportCurrentFrontendRouteConventions() {
    StompWebSocketChannelInterceptor interceptor = new StompWebSocketChannelInterceptor(
        mock(TokenService.class),
        mock(UserCenter.class));

    Message<byte[]> appSubscription = subscribeMessage("/app/app/crm/logs/subscribe", "session123");
    Message<?> untouchedSubscription = interceptor.preSend(appSubscription, mock(MessageChannel.class));

    assertThat(StompHeaderAccessor.wrap(untouchedSubscription).getDestination())
        .isEqualTo("/app/app/crm/logs/subscribe");

    WebSocketBrokerStrategy strategy = mock(WebSocketBrokerStrategy.class);
    LocalWebSocketOperations localOperations = mock(LocalWebSocketOperations.class);
    Object sender = newStrategyAwareSender(strategy, localOperations, "instance-a");

    invoke(sender, "sendToUser",
        new Class<?>[]{String.class, String.class, Object.class},
        "alice", "/queue/app/crm/logs", "payload");

    ArgumentCaptor<BrokerEnvelope> envelopeCaptor = ArgumentCaptor.forClass(BrokerEnvelope.class);
    verify(strategy).send(envelopeCaptor.capture(), same(localOperations));
    assertThat(envelopeCaptor.getValue().getDestination()).isEqualTo("/queue/app/crm/logs");
  }

  private static Message<byte[]> subscribeMessage(String destination, String sessionId) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setLeaveMutable(true);
    accessor.setDestination(destination);
    accessor.setSessionId(sessionId);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private static Object newStrategyAwareSender(WebSocketBrokerStrategy strategy,
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
      fail("StrategyAwareStompMessageSender must exist to preserve routing compatibility", e);
    } catch (NoSuchMethodException e) {
      fail("StrategyAwareStompMessageSender must expose constructor (WebSocketBrokerStrategy, LocalWebSocketOperations, String)", e);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      fail("StrategyAwareStompMessageSender could not be instantiated for routing compatibility tests", e);
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
