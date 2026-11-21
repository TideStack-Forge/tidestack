package com.ouroboros.websocket.broker.redis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.websocket.SessionRoute;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisDistributedSessionRegistryImpl implements RedisDistributedSessionRegistry {

  private final StringRedisTemplate redisTemplate;
  private final WebSocketBrokerProperties.RedisProperties props;
  private final ObjectMapper objectMapper;
  private final String localInstanceId;
  private final Set<String> localSessionIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

  public RedisDistributedSessionRegistryImpl(
      StringRedisTemplate redisTemplate,
      WebSocketBrokerProperties.RedisProperties props,
      ObjectMapper objectMapper,
      String localInstanceId) {
    this.redisTemplate = redisTemplate;
    this.props = props;
    this.objectMapper = objectMapper;
    this.localInstanceId = localInstanceId;
  }

  @Override
  public void registerSession(String sessionId, String userId, String instanceId) {
    String sessionKey = sessionKey(sessionId);
    SessionRoute route = new SessionRoute(sessionId, userId, instanceId, Instant.now());

    redisTemplate.opsForValue().set(sessionKey, toJson(route), props.getRegistryEntryTtl());

    if (userId != null) {
      String userKey = userKey(userId);
      redisTemplate.opsForSet().add(userKey, sessionId);
      redisTemplate.expire(userKey, props.getRegistryEntryTtl());
    }

    if (instanceId != null && instanceId.equals(localInstanceId)) {
      localSessionIds.add(sessionId);
    }
  }

  @Override
  public void unregisterSession(String sessionId) {
    Optional<SessionRoute> route = findSession(sessionId);
    redisTemplate.delete(sessionKey(sessionId));
    localSessionIds.remove(sessionId);

    if (route.isPresent() && route.get().getUserId() != null) {
      redisTemplate.opsForSet().remove(userKey(route.get().getUserId()), sessionId);
    }
  }

  @Override
  public Optional<SessionRoute> findSession(String sessionId) {
    String json = redisTemplate.opsForValue().get(sessionKey(sessionId));
    if (json == null) {
      return Optional.empty();
    }
    return Optional.of(fromJson(json));
  }

  @Override
  public Collection<SessionRoute> findUserSessions(String userId) {
    Set<String> sessionIds = redisTemplate.opsForSet().members(userKey(userId));
    if (sessionIds == null || sessionIds.isEmpty()) {
      return Collections.emptyList();
    }
    return sessionIds.stream()
        .map(this::findSession)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  @Override
  public int getSessionCount() {
    return localSessionIds.size();
  }

  @Override
  public void renewLocalSessions() {
    for (String sessionId : new ArrayList<>(localSessionIds)) {
      Optional<SessionRoute> route = findSession(sessionId);
      if (!route.isPresent()) {
        localSessionIds.remove(sessionId);
        continue;
      }

      SessionRoute sessionRoute = route.get();
      if (sessionRoute.getInstanceId() == null || !sessionRoute.getInstanceId().equals(localInstanceId)) {
        localSessionIds.remove(sessionId);
        continue;
      }

      redisTemplate.expire(sessionKey(sessionId), props.getRegistryEntryTtl());
      if (sessionRoute.getUserId() != null) {
        redisTemplate.expire(userKey(sessionRoute.getUserId()), props.getRegistryEntryTtl());
      }
    }
  }

  private String sessionKey(String sessionId) {
    return props.getRegistryKeyPrefix() + ":session:" + sessionId;
  }

  private String userKey(String userId) {
    return props.getRegistryKeyPrefix() + ":user:" + userId;
  }

  private String toJson(SessionRoute route) {
    try {
      return objectMapper.writeValueAsString(route);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize session route", e);
    }
  }

  private SessionRoute fromJson(String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      String sessionId = readRequiredText(root, "sessionId");
      String userId = readNullableText(root, "userId");
      String instanceId = readNullableText(root, "instanceId");
      String connectedAtRaw = readRequiredText(root, "connectedAt");
      Instant connectedAt = Instant.parse(connectedAtRaw);
      return new SessionRoute(sessionId, userId, instanceId, connectedAt);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize session route", e);
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
}
