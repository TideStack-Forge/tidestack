package com.ouroboros.mq.builder;

import java.util.function.Function;

import com.ouroboros.mq.MQProducer;
import com.ouroboros.mq.binding.MQProducerProperties;

public class MQProducerBuilder extends AbstractMQBindingBuilder<MQProducer, MQProducerBuilder, MQProducerProperties> {
  private final Function<MQProducerProperties, MQProducer> producerCreator;

  public MQProducerBuilder(String bindingName, Function<MQProducerProperties, MQProducer> producerCreator) {
    super(bindingName, new MQProducerProperties());
    this.producerCreator = producerCreator;
  }

  @Override
  protected MQProducer doBuild(MQProducerProperties properties) {
    return producerCreator.apply(properties);
  }
}
