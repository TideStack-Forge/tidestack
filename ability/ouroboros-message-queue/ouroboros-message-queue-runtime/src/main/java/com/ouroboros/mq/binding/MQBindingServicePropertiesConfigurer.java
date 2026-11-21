package com.ouroboros.mq.binding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.stereotype.Component;

@Component
@Order(-1)
public class MQBindingServicePropertiesConfigurer extends AbstractMQBindingPropertiesConfigurer {
  final static Logger logger = LoggerFactory.getLogger(MQBindingServicePropertiesConfigurer.class);
  @Autowired(required = false)
  BindingServiceProperties bindingServiceProperties;

  @Override
  protected Class<?> getBinderType() {
    return null;
  }

  @Override
  protected ConversionService getConversionService() {
    ConversionService conversionService = super.getConversionService();
    if (conversionService != null) {
      return conversionService;
    }
    return DefaultConversionService.getSharedInstance();
  }

  @Override
  protected void doConfigBinding(MQBindingProperties<?> properties) {
    if (bindingServiceProperties == null) {
      logger.error("bindingServiceProperties 为空, 无法注入");
      return;
    }
    var bindingName = properties.getBindingName();

    var newProperties = buildBindingProperties(properties);

    // 如果不存在直接赋值
    var oldProperties = bindingServiceProperties.getBindings().get(bindingName);
    if (oldProperties == null) {
      applyDeliveryMode(properties, newProperties);
      bindingServiceProperties.getBindings().put(bindingName, newProperties);
      return;
    }

    // 如果存在, 则合并
    if (oldProperties.getConsumer() == null) {
      oldProperties.setConsumer(newProperties.getConsumer());
    }
    if (oldProperties.getProducer() == null) {
      oldProperties.setProducer(newProperties.getProducer());
    }
    if (oldProperties.getDestination() == null) {
      oldProperties.setDestination(newProperties.getDestination());
    }
    if (oldProperties.getGroup() == null) {
      oldProperties.setGroup(newProperties.getGroup());
    }
    if (oldProperties.getContentType() == null) {
      oldProperties.setContentType(newProperties.getContentType());
    }
    if (oldProperties.getBinder() == null) {
      oldProperties.setBinder(newProperties.getBinder());
    }
    applyDeliveryMode(properties, oldProperties);
  }

  private void applyDeliveryMode(MQBindingProperties<?> source, BindingProperties target) {
    if (source instanceof MQConsumerProperties && Boolean.TRUE.equals(source.getBroadcast())) {
      // Anonymous groups give every process its own subscription without creating new topics.
      target.setGroup(null);
    }
  }

  private BindingProperties buildBindingProperties(MQBindingProperties<?> properties) {
    var bindingProperties = new BindingProperties();
    setProperties(properties, bindingProperties);
    if (properties instanceof MQConsumerProperties consumerProperties) {
      ConsumerProperties consumer = new ConsumerProperties();
      setProperties(consumerProperties, consumer);
      bindingProperties.setConsumer(consumer);
    }
    if (properties instanceof MQProducerProperties producerProperties) {
      ProducerProperties producer = new ProducerProperties();
      setProperties(producerProperties, producer);
      bindingProperties.setProducer(producer);
    }
    return bindingProperties;
  }
}
