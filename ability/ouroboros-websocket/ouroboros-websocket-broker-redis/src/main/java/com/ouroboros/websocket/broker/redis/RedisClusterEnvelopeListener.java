package com.ouroboros.websocket.broker.redis;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.websocket.BrokerEnvelope;
import com.ouroboros.websocket.BrokerEnvelopeType;
import com.ouroboros.websocket.LocalWebSocketOperations;

public class RedisClusterEnvelopeListener {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
  };

  private final LocalWebSocketOperations localOperations;
  private final ObjectMapper objectMapper;
  private final String instanceId;

  public RedisClusterEnvelopeListener(
      LocalWebSocketOperations localOperations,
      ObjectMapper objectMapper,
      String instanceId) {
    this.localOperations = localOperations;
    this.objectMapper = objectMapper;
    this.instanceId = instanceId;
  }

  public void onMessage(String channel, String message) {
    BrokerEnvelope envelope = fromJson(message);
    String sourceInstanceId = envelope.getSourceInstanceId();
    if (sourceInstanceId != null && sourceInstanceId.equals(instanceId)) {
      return;
    }
    dispatchLocally(envelope);
  }

  private void dispatchLocally(BrokerEnvelope envelope) {
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

  private BrokerEnvelope fromJson(String message) {
    try {
      JsonNode root = objectMapper.readTree(message);
      BrokerEnvelope.Builder builder = BrokerEnvelope.builder()
          .type(parseType(root.path("type").asText(null)))
          .destination(readNullableText(root, "destination"))
          .user(readNullableText(root, "user"))
          .sessionId(readNullableText(root, "sessionId"))
          .messageId(readNullableText(root, "messageId"))
          .sourceInstanceId(readNullableText(root, "sourceInstanceId"));

      JsonNode payloadNode = root.get("payload");
      if (payloadNode != null && !payloadNode.isNull()) {
        builder.payload(objectMapper.convertValue(payloadNode, Object.class));
      }

      JsonNode headersNode = root.get("headers");
      if (headersNode != null && headersNode.isObject()) {
        builder.headers(objectMapper.convertValue(headersNode, MAP_TYPE));
      }

      return builder.build();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize broker envelope", e);
    }
  }

  private BrokerEnvelopeType parseType(String value) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("Envelope type is missing");
    }
    return BrokerEnvelopeType.valueOf(value);
  }

  private String readNullableText(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    if (node == null || node.isNull()) {
      return null;
    }
    return node.asText();
  }
}
