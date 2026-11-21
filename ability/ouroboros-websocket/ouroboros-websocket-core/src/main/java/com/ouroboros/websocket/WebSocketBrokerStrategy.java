package com.ouroboros.websocket;

import org.springframework.messaging.simp.config.MessageBrokerRegistry;

public interface WebSocketBrokerStrategy {
  WebSocketBrokerType getType();

  void configure(MessageBrokerRegistry registry, WebSocketBrokerContext context);

  void send(BrokerEnvelope envelope, LocalWebSocketOperations localOperations);

  BrokerHealthInfo getHealthInfo();
}
