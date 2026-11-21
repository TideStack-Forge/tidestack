package com.ouroboros.websocket.config;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenService;
import com.ouroboros.websocket.OuroborosWebSocketAutoConfiguration;
import com.ouroboros.websocket.WebSocketBrokerStrategy;
import com.ouroboros.websocket.WebSocketBrokerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(
    classes = WebSocketBrokerSelectionTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WebSocketBrokerSelectionTest {

  private static final String SIMPLE_STRATEGY_CLASS = "com.ouroboros.websocket.broker.simple.SimpleBrokerStrategy";
  private static final String RABBITMQ_STRATEGY_CLASS = "com.ouroboros.websocket.RabbitMqStompRelayBrokerStrategy";
  private static final String REDIS_STRATEGY_CLASS = "com.ouroboros.websocket.RedisPubSubBrokerStrategy";

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withBean(TokenService.class, () -> mock(TokenService.class))
      .withBean(UserCenter.class, () -> mock(UserCenter.class))
      .withConfiguration(AutoConfigurations.of(OuroborosWebSocketAutoConfiguration.class, com.ouroboros.websocket.WebSocketBrokerStrategySelectionConfiguration.class));

  @Test
  void shouldDefaultToSimpleBroker() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(WebSocketBrokerStrategy.class);
      assertThat(context.getBeansOfType(WebSocketBrokerStrategy.class)).hasSize(1);

      WebSocketBrokerStrategy strategy = context.getBean(WebSocketBrokerStrategy.class);
      assertThat(strategy.getType()).isEqualTo(WebSocketBrokerType.SIMPLE);
      assertThat(strategy.getClass().getName()).isEqualTo(SIMPLE_STRATEGY_CLASS);
      assertThat(beanClassNames(context))
          .contains(SIMPLE_STRATEGY_CLASS)
          .doesNotContain(RABBITMQ_STRATEGY_CLASS)
          .doesNotContain(REDIS_STRATEGY_CLASS);
    });
  }

  @Test
  void shouldSelectRabbitMqBrokerWhenConfigured() {
    contextRunner
        .withPropertyValues("ouroboros.websocket.broker-type=rabbitmq-stomp-relay")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(messageChain(context))
              .contains("rabbitmq-stomp-relay")
              .contains("ouroboros-websocket-broker-rabbitmq");
        });
  }

  @Test
  void shouldSelectRedisBrokerWhenConfigured() {
    contextRunner
        .withPropertyValues("ouroboros.websocket.broker-type=redis-pubsub")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(messageChain(context))
              .contains("redis-pubsub")
              .contains("ouroboros-websocket-broker-redis");
        });
  }

  @Test
  void shouldFailWhenRabbitMqSelectedButModuleMissing() {
    contextRunner
        .withPropertyValues("ouroboros.websocket.broker-type=rabbitmq-stomp-relay")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(messageChain(context))
              .contains("rabbitmq-stomp-relay")
              .contains("ouroboros-websocket-broker-rabbitmq");
        });
  }

  @Test
  void shouldFailWhenRedisSelectedButModuleMissing() {
    contextRunner
        .withPropertyValues("ouroboros.websocket.broker-type=redis-pubsub")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(messageChain(context))
              .contains("redis-pubsub")
              .contains("ouroboros-websocket-broker-redis");
        });
  }

  @Test
  void shouldValidateRabbitMqPropertiesWhenSelected() {
    contextRunner
        .withPropertyValues(
            "ouroboros.websocket.broker-type=rabbitmq-stomp-relay",
            "ouroboros.websocket.rabbitmq.host=",
            "ouroboros.websocket.rabbitmq.port=0",
            "ouroboros.websocket.rabbitmq.system-passcode=",
            "ouroboros.websocket.rabbitmq.client-passcode="
        )
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(messageChain(context))
              .contains("rabbitmq-stomp-relay")
              .contains("ouroboros-websocket-broker-rabbitmq");
        });
  }

  @Test
  void shouldValidateRedisPropertiesWhenSelected() {
    contextRunner
        .withPropertyValues(
            "ouroboros.websocket.broker-type=redis-pubsub",
            "ouroboros.websocket.redis.topic-broadcast-channel=",
            "ouroboros.websocket.redis.user-broadcast-channel=",
            "ouroboros.websocket.redis.registry-key-prefix=",
            "ouroboros.websocket.redis.heartbeat-interval="
        )
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(messageChain(context))
              .contains("redis-pubsub")
              .contains("ouroboros-websocket-broker-redis");
        });
  }

  private static Set<String> beanClassNames(AssertableApplicationContext context) {
    Map<String, Object> beans = context.getBeansOfType(Object.class);
    Set<String> classNames = new LinkedHashSet<>();
    for (Object bean : beans.values()) {
      classNames.add(bean.getClass().getName());
    }
    return classNames;
  }

  private static String messageChain(AssertableApplicationContext context) {
    Throwable startupFailure = context.getStartupFailure();
    if (startupFailure == null) {
      return "";
    }

    StringBuilder builder = new StringBuilder();
    Throwable current = startupFailure;
    while (current != null) {
      if (builder.length() > 0) {
        builder.append(" -> ");
      }
      builder.append(current.getClass().getSimpleName());
      if (current.getMessage() != null) {
        builder.append(": ").append(current.getMessage());
      }
      current = current.getCause();
    }
    return builder.toString();
  }

  @SpringBootConfiguration
  static class TestApplication {
  }
}
