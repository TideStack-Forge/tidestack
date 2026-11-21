package com.ouroboros.websocket.broker.rabbitmq;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.websocket.BrokerEnvelope;
import com.ouroboros.websocket.BrokerEnvelopeType;
import com.ouroboros.websocket.BrokerHealthInfo;
import com.ouroboros.websocket.BrokerHealthStatus;
import com.ouroboros.websocket.LocalWebSocketOperations;
import com.ouroboros.websocket.WebSocketBrokerContext;
import com.ouroboros.websocket.WebSocketBrokerStrategy;
import com.ouroboros.websocket.WebSocketBrokerType;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;

public class RabbitMqStompRelayBrokerStrategy implements WebSocketBrokerStrategy {

  private final RabbitMqDistributedSessionRegistry registry;
  private final WebSocketBrokerProperties.RabbitMqProperties props;

  public RabbitMqStompRelayBrokerStrategy(
      RabbitMqDistributedSessionRegistry registry,
      WebSocketBrokerProperties.RabbitMqProperties props) {
    this.registry = registry;
    this.props = props;
  }

  @Override
  public WebSocketBrokerType getType() {
    return WebSocketBrokerType.RABBITMQ_STOMP_RELAY;
  }

  @Override
  public void configure(MessageBrokerRegistry registry, WebSocketBrokerContext context) {
    StompBrokerRelayRegistration relay = registry.enableStompBrokerRelay("/topic", "/queue")
        .setRelayHost(props.getHost())
        .setRelayPort(props.getPort())
        .setClientLogin(props.getClientLogin())
        .setClientPasscode(props.getClientPasscode())
        .setSystemLogin(props.getSystemLogin())
        .setSystemPasscode(props.getSystemPasscode())
        .setVirtualHost(props.getVirtualHost());

    relay.setUserDestinationBroadcast(props.getUserDestinationBroadcast());
    relay.setUserRegistryBroadcast(props.getUserRegistryBroadcast());
    relay.setSystemHeartbeatSendInterval(props.getSystemHeartbeatSendInterval());
    relay.setSystemHeartbeatReceiveInterval(props.getSystemHeartbeatReceiveInterval());
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
    throw new IllegalArgumentException("Unsupported envelope type: " + type);
  }

  @Override
  public BrokerHealthInfo getHealthInfo() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("host", props.getHost());
    details.put("port", props.getPort());
    details.put("registrySize", registry.getSessionCount());
    return new BrokerHealthInfo(
        WebSocketBrokerType.RABBITMQ_STOMP_RELAY,
        BrokerHealthStatus.UP,
        "RabbitMQ STOMP relay active",
        details);
  }
}
