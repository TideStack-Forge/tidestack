package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageHandler;

import com.ouroboros.dev.app.config.DevAppStompCompensationRegistrar;
import com.ouroboros.dev.app.service.AppLogCacheService;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQConsumer;
import com.ouroboros.mq.binding.MQConsumerProperties;
import com.ouroboros.mq.builder.MQConsumerBuilder;
import com.ouroboros.websocket.StompCompensationRegistrar;

@ExtendWith(MockitoExtension.class)
class AppDebugSupportControllerTest {

  @Mock
  private DevAppStompCompensationRegistrar stompCompensationRegistrar;

  @Mock
  private StompCompensationRegistrar.UserTopicChannel appLogsChannel;

  @Mock
  private AppLogCacheService appLogCacheService;

  private AppDebugSupportController controller;

  @BeforeEach
  void setUp() {
    controller = new AppDebugSupportController(stompCompensationRegistrar, appLogCacheService);
  }

  @Test
  void shouldBindMqConsumerWithAppLogTopicRoutingKey() {
    MQBridge bridge = org.mockito.Mockito.mock(MQBridge.class);
    AtomicReference<MQConsumerProperties> boundProperties = new AtomicReference<>();
    AtomicReference<MessageHandler> subscribedHandler = new AtomicReference<>();
    MQConsumerBuilder builder = new MQConsumerBuilder("app-debug-log", properties -> {
      boundProperties.set(properties);
      return new MQConsumer() {
        @Override
        public void subscribe(MessageHandler messageHandler) {
          subscribedHandler.set(messageHandler);
        }

        @Override
        public void unsubscribe(MessageHandler messageHandler) {
        }
      };
    });
    org.mockito.Mockito.when(bridge.bindingConsumer("app-debug-log")).thenReturn(builder);

    controller.initMq(bridge);

    MQConsumerProperties properties = boundProperties.get();
    assertNotNull(properties);
    assertEquals("ouroboros.app.log", properties.getDestination());
    assertEquals("app.#.profile.#.log", properties.getExtraProp("bindingRoutingKey").orElse(null));
    assertEquals(Boolean.TRUE, properties.getExtraProp("declareExchange").orElse(null));
    assertEquals("topic", properties.getExtraProp("exchangeType").orElse(null));
    assertNotNull(subscribedHandler.get());
  }

  @Test
  void shouldPublishLiveLogsToUserScopedLane() {
    org.mockito.Mockito.when(stompCompensationRegistrar.appLogsChannel()).thenReturn(appLogsChannel);
    org.springframework.messaging.Message<byte[]> message =
        org.springframework.messaging.support.MessageBuilder.withPayload("[INFO ]hello\n".getBytes())
            .setHeader("amqp_appId", "crm")
            .setHeader("amqp_receivedRoutingKey", "app.crm.profile.alice.log")
            .build();

    controller.writeLog(message);

    verify(appLogCacheService).addLog("alice", "crm", "[INFO ]hello\n");
    verify(appLogsChannel).sendToUser("alice", java.util.Collections.singletonMap("appName", "crm"), "[INFO ]hello\n");
  }

  @Test
  void shouldHandleShortLogLinesWithoutSubstringFailure() {
    org.mockito.Mockito.when(stompCompensationRegistrar.appLogsChannel()).thenReturn(appLogsChannel);
    org.springframework.messaging.Message<byte[]> message =
        org.springframework.messaging.support.MessageBuilder.withPayload("hi".getBytes())
            .setHeader("amqp_appId", "crm")
            .setHeader("amqp_receivedRoutingKey", "app.crm.profile.alice.log")
            .build();

    controller.writeLog(message);

    verify(appLogCacheService).addLog("alice", "crm", "hi");
    verify(appLogsChannel).sendToUser("alice", java.util.Collections.singletonMap("appName", "crm"), "hi");
  }

  @Test
  void shouldResolveAppAndUserFromSeparateAmqpHeaders() {
    org.mockito.Mockito.when(stompCompensationRegistrar.appLogsChannel()).thenReturn(appLogsChannel);
    org.springframework.messaging.Message<byte[]> message =
        org.springframework.messaging.support.MessageBuilder.withPayload("[INFO ]hello\n".getBytes())
            .setHeader("amqp_appId", "test-beta3")
            .setHeader("amqp_receivedRoutingKey", "app.test-beta3.profile.admin-user.dev.log")
            .build();

    controller.writeLog(message);

    verify(appLogCacheService).addLog("admin-user.dev", "test-beta3", "[INFO ]hello\n");
    verify(appLogsChannel).sendToUser("admin-user.dev", java.util.Collections.singletonMap("appName", "test-beta3"), "[INFO ]hello\n");
  }

  @Test
  void shouldIgnoreLogsWithUnexpectedRoutingKey() {
    org.springframework.messaging.Message<byte[]> message =
        org.springframework.messaging.support.MessageBuilder.withPayload("[INFO ]hello\n".getBytes())
            .setHeader("amqp_appId", "test-beta3")
            .setHeader("amqp_receivedRoutingKey", "admin-user.dev")
            .build();

    controller.writeLog(message);

    verifyNoInteractions(appLogCacheService, appLogsChannel);
  }

  @Test
  void shouldIgnoreLogsWithMismatchedAppRoutingKey() {
    org.springframework.messaging.Message<byte[]> message =
        org.springframework.messaging.support.MessageBuilder.withPayload("[INFO ]hello\n".getBytes())
            .setHeader("amqp_appId", "test-beta3")
            .setHeader("amqp_receivedRoutingKey", "app.other-app.profile.admin-user.dev.log")
            .build();

    controller.writeLog(message);

    verifyNoInteractions(appLogCacheService, appLogsChannel);
  }

  @Test
  void shouldIgnoreLogsWithoutLogRoutingKeySuffix() {
    org.springframework.messaging.Message<byte[]> message =
        org.springframework.messaging.support.MessageBuilder.withPayload("[INFO ]hello\n".getBytes())
            .setHeader("amqp_appId", "test-beta3")
            .setHeader("amqp_receivedRoutingKey", "app.test-beta3.profile.admin-user.dev")
            .build();

    controller.writeLog(message);

    verifyNoInteractions(appLogCacheService, appLogsChannel);
  }

  @Test
  void shouldIgnoreLogsWithoutAppIdHeader() {
    org.springframework.messaging.Message<byte[]> message =
        org.springframework.messaging.support.MessageBuilder.withPayload("[INFO ]hello\n".getBytes())
            .setHeader("amqp_receivedRoutingKey", "test-beta3-admin-user")
            .build();

    controller.writeLog(message);

    verifyNoInteractions(appLogCacheService, appLogsChannel);
  }
}
