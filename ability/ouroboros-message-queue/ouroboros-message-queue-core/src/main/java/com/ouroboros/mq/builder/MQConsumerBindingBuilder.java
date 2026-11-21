package com.ouroboros.mq.builder;

import java.util.function.Consumer;

import com.ouroboros.mq.binding.MQConsumerProperties;

public class MQConsumerBindingBuilder extends AbstractMQBindingBuilder<Void, MQConsumerBindingBuilder, MQConsumerProperties> {
  private final Consumer<MQConsumerProperties> bindingCreator;

  public MQConsumerBindingBuilder(String bindingName, Consumer<MQConsumerProperties> bindingCreator) {
    super(bindingName, new MQConsumerProperties());
    this.bindingCreator = bindingCreator;
  }

  @Override
  protected Void doBuild(MQConsumerProperties properties) {
    bindingCreator.accept(properties);
    return null;
  }
}
