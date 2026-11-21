package com.ouroboros.mq.rabbit;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RabbitMQBinderReadyCheckerContractTest {

  @Test
  void customBinderNameCountsAsReadyForRabbitBinder() {
    RabbitMQBinderReadyChecker checker = newChecker(new MockEnvironment());

    assertTrue(checker.isReady("orders", mock(org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder.class)));
  }

  @Test
  void defaultRabbitBinderNeedsRabbitConnectionProperties() {
    RabbitMQBinderReadyChecker checker = newChecker(new MockEnvironment());

    assertFalse(checker.isReady("rabbit", mock(org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder.class)));
    assertFalse(checker.isReady(null, mock(org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder.class)));
  }

  @Test
  void rabbitConnectionPropertiesMarkDefaultBinderAsReady() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("spring.rabbitmq.host", "localhost")
        .withProperty("spring.cloud.stream.binders.rabbit.environment.spring.rabbitmq.addresses", "localhost:5672");
    RabbitMQBinderReadyChecker checker = newChecker(environment);

    assertTrue(checker.isReady("rabbit", mock(org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder.class)));
    assertTrue(checker.isReady(null, mock(org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder.class)));
  }

  @Test
  void nonRabbitBinderIsNeverReady() {
    RabbitMQBinderReadyChecker checker = newChecker(new MockEnvironment());

    assertFalse(checker.isReady("rabbit", mock(Binder.class)));
  }

  private RabbitMQBinderReadyChecker newChecker(MockEnvironment environment) {
    RabbitMQBinderReadyChecker checker = new RabbitMQBinderReadyChecker();
    checker.environment = environment;
    return checker;
  }
}
