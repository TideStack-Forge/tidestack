package com.ouroboros.mq.builder;

public interface MQBindingBuilder<T, B extends MQBindingBuilder<T, B>> {
  B binder(String binder);

  B destination(String destination);

  B group(String group);

  B contentType(String contentType);

  B broadcast(Boolean broadcast);

  B extraProp(String key, Object value);

  T build();
}
