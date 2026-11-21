package com.ouroboros.mq;

import java.util.function.BiConsumer;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class DefaultProducerCreator extends AbstractProducerCreator {
  @Override
  protected boolean support(Binder<?, ?, ?> binder, String bindingName) {
    return true;
  }

  @Override
  protected BiConsumer<String, Message<?>> getGenderWithRoutingKey(Binder<?, ?, ?> binder, String bindingName) {
    var defaultSender = getSender(binder, bindingName);
    return (routingKey, message) -> defaultSender.accept(message);
  }
}
