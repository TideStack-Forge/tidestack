package com.ouroboros.mq.rabbit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.ouroboros.mq.binder.MQBinderReadyChecker;

@Component
public class RabbitMQBinderReadyChecker implements MQBinderReadyChecker {
  @Autowired
  Environment environment;

  @Override
  public boolean isReady(String binderName, Binder<?, ?, ?> binder) {
    if (!(binder instanceof RabbitMessageChannelBinder rabbitMQ)) {
      return false;
    }
    // 如果有 binderName，且不是默认的 rabbit 则表示配置过绑定，则直接返回 true
    if (StringUtils.isNotBlank(binderName) && !"rabbit".equals(binderName)) {
      return true;
    }

    // 否则判断是否配置了 rabbitmq
    return isRabbitMQConfigured() || isDefaultBinderConfigured() || isRabbitBinderConfigured();
  }

  private boolean isRabbitMQConfigured() {
    return environment.containsProperty("spring.rabbitmq.host")
        || environment.containsProperty("spring.rabbitmq.addresses");
  }

  private boolean isDefaultBinderConfigured() {
    return environment.containsProperty("spring.cloud.stream.rabbit.connection.host")
        || environment.containsProperty("spring.cloud.stream.rabbit.connection.addresses");
  }

  private boolean isRabbitBinderConfigured() {
    return environment.containsProperty("spring.cloud.stream.binders.rabbit.environment.spring.rabbitmq.host")
        || environment.containsProperty("spring.cloud.stream.binders.rabbit.environment.spring.rabbitmq.addresses");
  }
}
