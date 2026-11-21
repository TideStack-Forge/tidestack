package com.ouroboros.websocket.broker.simple;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import com.ouroboros.websocket.BrokerEnvelope;
import com.ouroboros.websocket.BrokerHealthInfo;
import com.ouroboros.websocket.BrokerHealthStatus;
import com.ouroboros.websocket.BrokerEnvelopeType;
import com.ouroboros.websocket.LocalWebSocketOperations;
import com.ouroboros.websocket.WebSocketBrokerContext;
import com.ouroboros.websocket.WebSocketBrokerStrategy;
import com.ouroboros.websocket.WebSocketBrokerType;

public class SimpleBrokerStrategy implements WebSocketBrokerStrategy {

  @Override
  public WebSocketBrokerType getType() {
    return WebSocketBrokerType.SIMPLE;
  }

  @Override
  public void configure(MessageBrokerRegistry registry, WebSocketBrokerContext context) {
    registry.enableSimpleBroker("/topic", "/queue");
  }

  @Override
  public void send(BrokerEnvelope envelope, LocalWebSocketOperations localOperations) {
    Map<String, Object> headers = envelope.getHeaders() == null
        ? new LinkedHashMap<>()
        : new LinkedHashMap<>(envelope.getHeaders());

    BrokerEnvelopeType type = envelope.getType();
    if (type == BrokerEnvelopeType.TOPIC) {
      localOperations.send(envelope.getDestination(), envelope.getPayload(), headers);
      return;
    }
    if (type == BrokerEnvelopeType.USER) {
      localOperations.sendToUser(envelope.getUser(), envelope.getDestination(), envelope.getPayload(), headers);
      return;
    }
    if (type == BrokerEnvelopeType.SESSION) {
      localOperations.sendToSession(envelope.getSessionId(), envelope.getDestination(), envelope.getPayload(), headers);
      return;
    }
    throw new IllegalArgumentException("Unsupported envelope type: " + envelope.getType());
  }

  @Override
  public BrokerHealthInfo getHealthInfo() {
    return new BrokerHealthInfo(
        WebSocketBrokerType.SIMPLE,
        BrokerHealthStatus.UP,
        "Simple broker (single instance)",
        Collections.emptyMap());
  }
}
