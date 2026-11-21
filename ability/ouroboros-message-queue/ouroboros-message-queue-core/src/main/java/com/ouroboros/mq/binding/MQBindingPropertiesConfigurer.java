package com.ouroboros.mq.binding;

@FunctionalInterface
public interface MQBindingPropertiesConfigurer {
  void configBinding(MQBindingProperties<?> properties);
}
