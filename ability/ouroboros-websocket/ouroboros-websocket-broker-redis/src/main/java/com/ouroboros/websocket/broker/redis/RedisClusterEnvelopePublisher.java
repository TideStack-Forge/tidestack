package com.ouroboros.websocket.broker.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.websocket.BrokerEnvelope;
import com.ouroboros.websocket.BrokerEnvelopeType;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisClusterEnvelopePublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final WebSocketBrokerProperties.RedisProperties props;

  public RedisClusterEnvelopePublisher(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      WebSocketBrokerProperties.RedisProperties props) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.props = props;
  }

  public void publish(BrokerEnvelope envelope) {
    String channel = getChannelForEnvelope(envelope);
    String json = toJson(envelope);
    redisTemplate.convertAndSend(channel, json);
  }

  private String getChannelForEnvelope(BrokerEnvelope envelope) {
    BrokerEnvelopeType type = envelope.getType();
    if (type == BrokerEnvelopeType.TOPIC) {
      return props.getTopicBroadcastChannel();
    }
    if (type == BrokerEnvelopeType.USER) {
      return props.getUserBroadcastChannel();
    }
    if (type == BrokerEnvelopeType.SESSION) {
      return props.getSessionBroadcastChannel();
    }
    throw new IllegalArgumentException("Unknown envelope type: " + type);
  }

  private String toJson(BrokerEnvelope envelope) {
    try {
      return objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize broker envelope", e);
    }
  }
}
