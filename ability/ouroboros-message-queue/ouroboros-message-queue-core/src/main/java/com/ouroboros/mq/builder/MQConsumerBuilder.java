package com.ouroboros.mq.builder;

import java.util.function.Function;

import com.ouroboros.mq.MQConsumer;
import com.ouroboros.mq.binding.MQConsumerProperties;

public class MQConsumerBuilder extends AbstractMQBindingBuilder<MQConsumer, MQConsumerBuilder, MQConsumerProperties> {
  private final Function<MQConsumerProperties, MQConsumer> consumerCreator;

  public MQConsumerBuilder(String bindingName, Function<MQConsumerProperties, MQConsumer> consumerCreator) {
    super(bindingName, new MQConsumerProperties());
    this.properties.setBindingName(bindingName);
    this.consumerCreator = consumerCreator;
  }

  @Override
  protected MQConsumer doBuild(MQConsumerProperties properties) {
    return consumerCreator.apply(properties);
  }
}
