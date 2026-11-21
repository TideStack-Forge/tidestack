package com.ouroboros.mq.builder;

import com.ouroboros.mq.binding.MQBindingProperties;

public abstract class AbstractMQBindingBuilder<T, B extends AbstractMQBindingBuilder<T, B, P>, P extends MQBindingProperties<?>> implements MQBindingBuilder<T, B> {
  protected P properties;

  protected AbstractMQBindingBuilder(String bindingName, P properties) {
    this.properties = properties;
    this.properties.setBindingName(bindingName);
  }

  @Override
  public B destination(String destination) {
    this.properties.setDestination(destination);
    return (B) this;
  }

  @Override
  public B binder(String binder) {
    this.properties.setBinder(binder);
    return (B) this;
  }

  @Override
  public B group(String group) {
    this.properties.setGroup(group);
    return (B) this;
  }

  @Override
  public B contentType(String contentType) {
    this.properties.setContentType(contentType);
    return (B) this;
  }

  @Override
  public B broadcast(Boolean broadcast) {
    this.properties.setBroadcast(broadcast);
    return (B) this;
  }

  @Override
  public B extraProp(String key, Object value) {
    this.properties.setExtraProp(key, value);
    return (B) this;
  }

  @Override
  public T build() {
    return doBuild(this.properties);
  }

  protected abstract T doBuild(P properties);
}
