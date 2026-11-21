package com.ouroboros.websocket.broker.rabbitmq;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.websocket.SessionRoute;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

public class RabbitMqRegistryListener {

  private static final String REGISTER_PREFIX = "websocket.session.register.";
  private static final String UNREGISTER_PREFIX = "websocket.session.unregister.";

  private final ObjectMapper objectMapper;
  private final Map<String, SessionRoute> localCache = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> userSessionIndex = new ConcurrentHashMap<>();

  public RabbitMqRegistryListener(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @RabbitListener(queues = "#{rabbitMqRegistryQueue.name}")
  public void handleRegistryEvent(
      String message,
      @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
    if (routingKey != null && routingKey.startsWith(REGISTER_PREFIX)) {
      applyRegister(fromJson(message));
      return;
    }

    if (routingKey != null && routingKey.startsWith(UNREGISTER_PREFIX)) {
      String sessionId = routingKey.substring(UNREGISTER_PREFIX.length());
      if (sessionId.isEmpty()) {
        sessionId = message;
      }
      applyUnregister(sessionId);
    }
  }

  public void applyRegister(SessionRoute route) {
    SessionRoute normalized = normalizeRoute(route);
    SessionRoute previous = localCache.put(normalized.getSessionId(), normalized);
    if (previous != null && previous.getUserId() != null) {
      removeSessionFromUser(previous.getUserId(), previous.getSessionId());
    }

    if (normalized.getUserId() != null) {
      userSessionIndex
          .computeIfAbsent(normalized.getUserId(), key -> Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>()))
          .add(normalized.getSessionId());
    }
  }

  public void applyUnregister(String sessionId) {
    SessionRoute removed = localCache.remove(sessionId);
    if (removed != null && removed.getUserId() != null) {
      removeSessionFromUser(removed.getUserId(), sessionId);
    }
  }

  public Optional<SessionRoute> findSession(String sessionId) {
    return Optional.ofNullable(localCache.get(sessionId));
  }

  public Collection<SessionRoute> findUserSessions(String userId) {
    Set<String> sessionIds = userSessionIndex.get(userId);
    if (sessionIds == null || sessionIds.isEmpty()) {
      return Collections.emptyList();
    }

    Collection<SessionRoute> routes = new ArrayList<>();
    for (String sessionId : sessionIds) {
      SessionRoute route = localCache.get(sessionId);
      if (route != null) {
        routes.add(route);
      }
    }
    return routes;
  }

  public int getSessionCount() {
    return localCache.size();
  }

  private SessionRoute normalizeRoute(SessionRoute route) {
    Instant connectedAt = route.getConnectedAt() == null ? Instant.now() : route.getConnectedAt();
    return new SessionRoute(route.getSessionId(), route.getUserId(), route.getInstanceId(), connectedAt);
  }

  private SessionRoute fromJson(String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      String sessionId = readRequiredText(root, "sessionId");
      String userId = readNullableText(root, "userId");
      String instanceId = readNullableText(root, "instanceId");
      String connectedAtRaw = readNullableText(root, "connectedAt");
      Instant connectedAt = connectedAtRaw == null ? Instant.now() : Instant.parse(connectedAtRaw);
      return new SessionRoute(sessionId, userId, instanceId, connectedAt);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize RabbitMQ session route", e);
    }
  }

  private String readRequiredText(JsonNode root, String fieldName) {
    String value = readNullableText(root, fieldName);
    if (value == null || value.isEmpty()) {
      throw new IllegalStateException("Missing required field in route json: " + fieldName);
    }
    return value;
  }

  private String readNullableText(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    if (node == null || node.isNull()) {
      return null;
    }
    return node.asText();
  }

  private void removeSessionFromUser(String userId, String sessionId) {
    userSessionIndex.computeIfPresent(userId, (key, sessions) -> {
      sessions.remove(sessionId);
      return sessions.isEmpty() ? null : sessions;
    });
  }
}
