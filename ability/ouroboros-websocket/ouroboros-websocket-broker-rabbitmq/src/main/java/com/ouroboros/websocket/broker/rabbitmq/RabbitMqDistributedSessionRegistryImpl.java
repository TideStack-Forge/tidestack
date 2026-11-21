package com.ouroboros.websocket.broker.rabbitmq;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.websocket.SessionRoute;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;
import org.springframework.amqp.core.AmqpTemplate;

public class RabbitMqDistributedSessionRegistryImpl implements RabbitMqDistributedSessionRegistry {

  private final AmqpTemplate amqpTemplate;
  private final WebSocketBrokerProperties.RabbitMqProperties props;
  private final ObjectMapper objectMapper;
  private final RabbitMqRegistryListener registryListener;

  public RabbitMqDistributedSessionRegistryImpl(
      AmqpTemplate amqpTemplate,
      WebSocketBrokerProperties.RabbitMqProperties props,
      ObjectMapper objectMapper,
      RabbitMqRegistryListener registryListener) {
    this.amqpTemplate = amqpTemplate;
    this.props = props;
    this.objectMapper = objectMapper;
    this.registryListener = registryListener;
  }

  @Override
  public void registerSession(String sessionId, String userId, String instanceId) {
    SessionRoute route = new SessionRoute(sessionId, userId, instanceId, Instant.now());
    registryListener.applyRegister(route);
    amqpTemplate.convertAndSend(
        props.getRegistryExchange(),
        "websocket.session.register." + sessionId,
        toJson(route));
  }

  @Override
  public void unregisterSession(String sessionId) {
    registryListener.applyUnregister(sessionId);
    amqpTemplate.convertAndSend(
        props.getRegistryExchange(),
        "websocket.session.unregister." + sessionId,
        sessionId);
  }

  @Override
  public Optional<SessionRoute> findSession(String sessionId) {
    return registryListener.findSession(sessionId);
  }

  @Override
  public Collection<SessionRoute> findUserSessions(String userId) {
    return registryListener.findUserSessions(userId);
  }

  @Override
  public int getSessionCount() {
    return registryListener.getSessionCount();
  }

  private String toJson(SessionRoute route) {
    try {
      return objectMapper.writeValueAsString(route);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize RabbitMQ session route", e);
    }
  }
}
