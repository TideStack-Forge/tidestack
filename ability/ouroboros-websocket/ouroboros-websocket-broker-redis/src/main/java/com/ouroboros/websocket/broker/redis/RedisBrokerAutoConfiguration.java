package com.ouroboros.websocket.broker.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.websocket.LocalWebSocketOperations;
import com.ouroboros.websocket.WebSocketBrokerStrategy;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "ouroboros.websocket.broker-type", havingValue = "redis-pubsub")
public class RedisBrokerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(WebSocketBrokerStrategy.class)
  public RedisPubSubBrokerStrategy redisBrokerStrategy(
      RedisClusterEnvelopePublisher publisher,
      RedisDistributedSessionRegistry registry,
      WebSocketBrokerProperties properties) {
    return new RedisPubSubBrokerStrategy(publisher, registry, properties.getInstanceId());
  }

  @Bean
  public LocalWebSocketOperations localWebSocketOperations(SimpMessagingTemplate messagingTemplate) {
    return new RedisLocalWebSocketOperations(messagingTemplate);
  }

  @Bean
  public RedisClusterEnvelopePublisher envelopePublisher(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      WebSocketBrokerProperties properties) {
    return new RedisClusterEnvelopePublisher(redisTemplate, objectMapper, properties.getRedis());
  }

  @Bean
  public RedisClusterEnvelopeListener envelopeListener(
      LocalWebSocketOperations localOperations,
      ObjectMapper objectMapper,
      WebSocketBrokerProperties properties) {
    return new RedisClusterEnvelopeListener(localOperations, objectMapper, properties.getInstanceId());
  }

  @Bean
  public RedisMessageListenerContainer redisEnvelopeListenerContainer(
      RedisConnectionFactory redisConnectionFactory,
      RedisClusterEnvelopeListener envelopeListener,
      WebSocketBrokerProperties properties) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(redisConnectionFactory);

    WebSocketBrokerProperties.RedisProperties redisProps = properties.getRedis();
    org.springframework.data.redis.connection.MessageListener redisMessageListener = (message, pattern) -> {
      String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
      String body = new String(message.getBody(), StandardCharsets.UTF_8);
      envelopeListener.onMessage(channel, body);
    };
    container.addMessageListener(redisMessageListener, new PatternTopic(redisProps.getTopicBroadcastChannel()));
    container.addMessageListener(redisMessageListener, new PatternTopic(redisProps.getUserBroadcastChannel()));
    container.addMessageListener(redisMessageListener, new PatternTopic(redisProps.getSessionBroadcastChannel()));

    return container;
  }

  @Bean
  public RedisDistributedSessionRegistryImpl sessionRegistry(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      WebSocketBrokerProperties properties) {
    return new RedisDistributedSessionRegistryImpl(redisTemplate, properties.getRedis(), objectMapper, properties.getInstanceId());
  }

  @Bean
  public RedisRegistryHeartbeat registryHeartbeat(
      RedisDistributedSessionRegistry registry,
      WebSocketBrokerProperties properties) {
    return new RedisRegistryHeartbeat(registry, properties.getRedis());
  }
}
