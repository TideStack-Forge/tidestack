package com.ouroboros.ability.all.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.websocket.WebSocketBrokerStrategy;
import com.ouroboros.websocket.WebSocketBrokerType;
import com.ouroboros.websocket.config.WebSocketBrokerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PublicRuntimeWebSocketBrokerTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(BrokerTestConfiguration.class)
      .withConfiguration(AutoConfigurations.of(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
          .getCandidates().stream()
          .filter(name -> name.startsWith("com.ouroboros.websocket.broker.")
              || name.equals("com.ouroboros.websocket.WebSocketBrokerStrategySelectionConfiguration"))
          .map(name -> ClassUtils.resolveClassName(name, getClass().getClassLoader()))
          .toArray(Class<?>[]::new)));

  @Test
  void defaultSimpleBrokerDoesNotRequireDistributedInfrastructure() {
    runner.run(context -> {
      assertThat(context).hasSingleBean(WebSocketBrokerStrategy.class);
      assertThat(context.getBean(WebSocketBrokerStrategy.class).getType()).isEqualTo(WebSocketBrokerType.SIMPLE);
      assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
      assertThat(context).doesNotHaveBean("sessionRegistry");
    });
  }

  @ParameterizedTest
  @CsvSource({"redis-pubsub, REDIS_PUBSUB", "rabbitmq-stomp-relay, RABBITMQ_STOMP_RELAY"})
  void fullRuntimeSelectsConfiguredDistributedBroker(String property, WebSocketBrokerType expected) {
    runner.withPropertyValues("ouroboros.websocket.broker-type=" + property)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withBean(SimpMessagingTemplate.class, () -> mock(SimpMessagingTemplate.class))
        .withBean(AmqpTemplate.class, () -> mock(AmqpTemplate.class))
        .run(context -> {
          assertThat(context).hasSingleBean(WebSocketBrokerStrategy.class);
          assertThat(context.getBean(WebSocketBrokerStrategy.class).getType()).isEqualTo(expected);
        });
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableConfigurationProperties(WebSocketBrokerProperties.class)
  static class BrokerTestConfiguration {
    // Exercise real broker wiring without opening a Redis connection in a context-level test.
    @Bean
    static BeanPostProcessor mockRedisListenerTransport() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String name) {
          if (bean instanceof RedisMessageListenerContainer) {
            return mock(RedisMessageListenerContainer.class);
          }
          return bean;
        }
      };
    }
  }
}
