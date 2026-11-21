package com.ouroboros.websocket.broker.redis;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import com.ouroboros.websocket.BrokerEnvelope;
import com.ouroboros.websocket.BrokerEnvelopeType;
import com.ouroboros.websocket.BrokerHealthInfo;
import com.ouroboros.websocket.BrokerHealthStatus;
import com.ouroboros.websocket.LocalWebSocketOperations;
import com.ouroboros.websocket.WebSocketBrokerContext;
import com.ouroboros.websocket.WebSocketBrokerStrategy;
import com.ouroboros.websocket.WebSocketBrokerType;

public class RedisPubSubBrokerStrategy implements WebSocketBrokerStrategy {

  private final RedisClusterEnvelopePublisher publisher;
  private final RedisDistributedSessionRegistry registry;
  private final String instanceId;

  public RedisPubSubBrokerStrategy(
      RedisClusterEnvelopePublisher publisher,
      RedisDistributedSessionRegistry registry,
      String instanceId) {
    this.publisher = publisher;
    this.registry = registry;
    this.instanceId = instanceId;
  }

  @Override
  public WebSocketBrokerType getType() {
    return WebSocketBrokerType.REDIS_PUBSUB;
  }

  @Override
  public void configure(MessageBrokerRegistry registry, WebSocketBrokerContext context) {
    registry.enableSimpleBroker("/topic", "/queue");
  }

  @Override
  public void send(BrokerEnvelope envelope, LocalWebSocketOperations localOperations) {
    dispatchLocally(envelope, localOperations);
    publisher.publish(envelope);
  }

  private void dispatchLocally(BrokerEnvelope envelope, LocalWebSocketOperations ops) {
    Map<String, Object> headers = envelope.getHeaders() == null
        ? new LinkedHashMap<>()
        : new LinkedHashMap<>(envelope.getHeaders());

    BrokerEnvelopeType type = envelope.getType();
    if (type == BrokerEnvelopeType.TOPIC) {
      ops.send(envelope.getDestination(), envelope.getPayload(), headers);
      return;
    }
    if (type == BrokerEnvelopeType.USER) {
      ops.sendToUser(envelope.getUser(), envelope.getDestination(), envelope.getPayload(), headers);
      return;
    }
    if (type == BrokerEnvelopeType.SESSION) {
      ops.sendToSession(envelope.getSessionId(), envelope.getDestination(), envelope.getPayload(), headers);
      return;
    }
    throw new IllegalArgumentException("Unsupported envelope type: " + type);
  }

  @Override
  public BrokerHealthInfo getHealthInfo() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("instanceId", instanceId);
    details.put("registrySize", registry.getSessionCount());
    return new BrokerHealthInfo(
        WebSocketBrokerType.REDIS_PUBSUB,
        BrokerHealthStatus.UP,
        "Redis pub/sub active",
        details);
  }
}
