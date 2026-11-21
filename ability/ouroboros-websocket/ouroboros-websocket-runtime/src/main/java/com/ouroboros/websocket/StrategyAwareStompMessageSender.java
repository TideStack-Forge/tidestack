package com.ouroboros.websocket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StrategyAwareStompMessageSender implements StompMessageSender {

  private final WebSocketBrokerStrategy strategy;
  private final LocalWebSocketOperations localOperations;
  private final String instanceId;

  @Autowired
  public StrategyAwareStompMessageSender(
      WebSocketBrokerStrategy strategy,
      SimpMessagingTemplate messagingTemplate,
      WebSocketBrokerProperties properties) {
    this(strategy, new LocalWebSocketOperationsAdapter(messagingTemplate), properties.getInstanceId());
  }

  StrategyAwareStompMessageSender(
      WebSocketBrokerStrategy strategy,
      LocalWebSocketOperations localOperations,
      String instanceId) {
    this.strategy = strategy;
    this.localOperations = localOperations;
    this.instanceId = instanceId;
  }

  @Override
  public void send(String destination, Object payload) {
    send(destination, payload, null);
  }

  @Override
  public void send(String destination, Object payload, Map<String, Object> headers) {
    BrokerEnvelope envelope = BrokerEnvelope.builder()
        .type(BrokerEnvelopeType.TOPIC)
        .destination(destination)
        .payload(payload)
        .headers(headers)
        .messageId(UUID.randomUUID().toString())
        .sourceInstanceId(instanceId)
        .build();
    strategy.send(envelope, localOperations);
  }

  @Override
  public void sendToUser(String user, String destination, Object payload) {
    sendToUser(user, destination, payload, null);
  }

  @Override
  public void sendToUser(String user, String destination, Object payload, Map<String, Object> headers) {
    BrokerEnvelope envelope = BrokerEnvelope.builder()
        .type(BrokerEnvelopeType.USER)
        .destination(destination)
        .user(user)
        .payload(payload)
        .headers(headers)
        .messageId(UUID.randomUUID().toString())
        .sourceInstanceId(instanceId)
        .build();
    strategy.send(envelope, localOperations);
  }

  @Override
  public void sendToQueue(String sessionId, String destination, Object payload) {
    sendToQueue(sessionId, destination, payload, null);
  }

  @Override
  public void sendToQueue(String sessionId, String destination, Object payload, Map<String, Object> headers) {
    String queueDestination = normalizeQueueDestination(destination);
    Map<String, Object> envelopeHeaders = new LinkedHashMap<>();
    if (headers != null) {
      envelopeHeaders.putAll(headers);
    }
    envelopeHeaders.put("simpOrigDestination", queueDestination);

    BrokerEnvelope envelope = BrokerEnvelope.builder()
        .type(BrokerEnvelopeType.SESSION)
        .destination(queueDestination)
        .sessionId(sessionId)
        .payload(payload)
        .headers(envelopeHeaders)
        .messageId(UUID.randomUUID().toString())
        .sourceInstanceId(instanceId)
        .build();
    strategy.send(envelope, localOperations);
  }

  private String normalizeQueueDestination(String destination) {
    if (destination == null || destination.isEmpty()) {
      return "/queue";
    }
    if (destination.startsWith("/queue/")) {
      return destination;
    }
    if ("/queue".equals(destination)) {
      return destination;
    }
    return "/queue" + (destination.startsWith("/") ? "" : "/") + destination;
  }
}
