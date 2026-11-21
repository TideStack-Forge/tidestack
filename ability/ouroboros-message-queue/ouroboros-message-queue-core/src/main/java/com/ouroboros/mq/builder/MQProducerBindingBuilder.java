package com.ouroboros.mq.builder;

import java.util.function.Consumer;

import com.ouroboros.mq.binding.MQProducerProperties;

public class MQProducerBindingBuilder extends AbstractMQBindingBuilder<Void, MQProducerBindingBuilder, MQProducerProperties> {
  private final Consumer<MQProducerProperties> bindingConsumer;

  public MQProducerBindingBuilder(String bindingName, Consumer<MQProducerProperties> bindingConsumer) {
    super(bindingName, new MQProducerProperties());
    this.bindingConsumer = bindingConsumer;
  }

  @Override
  protected Void doBuild(MQProducerProperties properties) {
    bindingConsumer.accept(properties);
    return null;
  }
}
