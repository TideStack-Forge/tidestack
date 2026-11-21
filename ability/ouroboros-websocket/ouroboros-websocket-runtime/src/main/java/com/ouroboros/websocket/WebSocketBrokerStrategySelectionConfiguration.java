package com.ouroboros.websocket;

import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;

@AutoConfiguration(afterName = {
    "com.ouroboros.websocket.broker.simple.SimpleBrokerAutoConfiguration",
    "com.ouroboros.websocket.broker.redis.RedisBrokerAutoConfiguration",
    "com.ouroboros.websocket.broker.rabbitmq.RabbitMqBrokerAutoConfiguration"
})
public class WebSocketBrokerStrategySelectionConfiguration {

  private static final String SIMPLE_STRATEGY_CLASS = "com.ouroboros.websocket.broker.simple.SimpleBrokerStrategy";

  @Bean
  @ConditionalOnMissingBean(WebSocketBrokerStrategy.class)
  public WebSocketBrokerStrategy webSocketBrokerStrategy(WebSocketBrokerProperties properties) {
    if (properties.getBrokerType() == WebSocketBrokerType.SIMPLE) {
      return instantiateSimpleBrokerStrategy();
    }

    throw new IllegalStateException(
        "No WebSocketBrokerStrategy bean found for broker type '" + toPropertyName(properties.getBrokerType())
            + "'. Add module '" + requiredModuleName(properties.getBrokerType()) + "' to the classpath.");
  }

  private WebSocketBrokerStrategy instantiateSimpleBrokerStrategy() {
    try {
      Class<?> strategyClass = Class.forName(SIMPLE_STRATEGY_CLASS);
      if (!WebSocketBrokerStrategy.class.isAssignableFrom(strategyClass)) {
        throw new IllegalStateException("Class '" + SIMPLE_STRATEGY_CLASS + "' does not implement WebSocketBrokerStrategy");
      }
      return WebSocketBrokerStrategy.class.cast(strategyClass.getDeclaredConstructor().newInstance());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "No WebSocketBrokerStrategy bean found for broker type 'simple'. Add module '"
              + requiredModuleName(WebSocketBrokerType.SIMPLE) + "' to the classpath.",
          e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to instantiate simple WebSocket broker strategy", e);
    }
  }

  private String toPropertyName(WebSocketBrokerType brokerType) {
    return brokerType.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private String requiredModuleName(WebSocketBrokerType brokerType) {
    if (brokerType == WebSocketBrokerType.RABBITMQ_STOMP_RELAY) {
      return "ouroboros-websocket-broker-rabbitmq";
    }
    if (brokerType == WebSocketBrokerType.REDIS_PUBSUB) {
      return "ouroboros-websocket-broker-redis";
    }
    if (brokerType == WebSocketBrokerType.SIMPLE) {
      return "ouroboros-websocket-broker-simple";
    }
    throw new IllegalStateException("Unsupported websocket broker type: " + brokerType);
  }
}
