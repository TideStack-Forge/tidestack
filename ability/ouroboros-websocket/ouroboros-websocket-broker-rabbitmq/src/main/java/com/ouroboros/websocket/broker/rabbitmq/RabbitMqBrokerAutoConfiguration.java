package com.ouroboros.websocket.broker.rabbitmq;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.websocket.WebSocketBrokerStrategy;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ouroboros.websocket.broker-type", havingValue = "rabbitmq-stomp-relay")
public class RabbitMqBrokerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(WebSocketBrokerStrategy.class)
  public RabbitMqStompRelayBrokerStrategy rabbitMqBrokerStrategy(
      RabbitMqDistributedSessionRegistry registry,
      WebSocketBrokerProperties properties) {
    return new RabbitMqStompRelayBrokerStrategy(registry, properties.getRabbitmq());
  }

  @Bean
  public RabbitMqRegistryListener rabbitMqRegistryListener(ObjectMapper objectMapper) {
    return new RabbitMqRegistryListener(objectMapper);
  }

  @Bean
  public RabbitMqDistributedSessionRegistryImpl sessionRegistry(
      AmqpTemplate amqpTemplate,
      ObjectMapper objectMapper,
      WebSocketBrokerProperties properties,
      RabbitMqRegistryListener rabbitMqRegistryListener) {
    return new RabbitMqDistributedSessionRegistryImpl(
        amqpTemplate,
        properties.getRabbitmq(),
        objectMapper,
        rabbitMqRegistryListener);
  }

  @Bean
  public TopicExchange rabbitMqRegistryExchange(WebSocketBrokerProperties properties) {
    return new TopicExchange(properties.getRabbitmq().getRegistryExchange());
  }

  @Bean
  public Queue rabbitMqRegistryQueue() {
    return new Queue("websocket.registry." + UUID.randomUUID(), false, true, true);
  }

  @Bean
  public Binding rabbitMqRegistryBinding(
      Queue rabbitMqRegistryQueue,
      TopicExchange rabbitMqRegistryExchange) {
    return BindingBuilder
        .bind(rabbitMqRegistryQueue)
        .to(rabbitMqRegistryExchange)
        .with("websocket.session.#");
  }
}
