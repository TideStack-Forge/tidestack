package com.ouroboros.mq.rabbit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitBinderConfigurationProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitExtendedBindingProperties;
import org.springframework.stereotype.Component;

import com.ouroboros.mq.binding.AbstractMQBindingPropertiesConfigurer;
import com.ouroboros.mq.binding.MQBindingProperties;
import com.ouroboros.mq.binding.MQConsumerProperties;
import com.ouroboros.mq.binding.MQProducerProperties;

@Component
@EnableConfigurationProperties({RabbitBinderConfigurationProperties.class,
    RabbitExtendedBindingProperties.class})
public class RabbitMQBindingPropertiesConfigurer extends AbstractMQBindingPropertiesConfigurer {
  @Autowired
  RabbitExtendedBindingProperties rabbitExtendedBindingProperties;

  @Override
  protected Class<?> getBinderType() {
    return RabbitMessageChannelBinder.class;
  }

  @Override
  protected void doConfigBinding(MQBindingProperties<?> properties) {
    if (properties instanceof MQProducerProperties) {
      applyMQProducerProperties((MQProducerProperties) properties);
    }
    if (properties instanceof MQConsumerProperties) {
      applyMQConsumerProperties((MQConsumerProperties) properties);
    }
  }

  private void applyMQConsumerProperties(MQConsumerProperties consumerProperties) {
    var oldConsumerProperties = rabbitExtendedBindingProperties
        .getExtendedConsumerProperties(consumerProperties.getBindingName());

    setProperties(consumerProperties, oldConsumerProperties);
  }

  private void applyMQProducerProperties(MQProducerProperties producerProperties) {
    var oldProducerProperties = rabbitExtendedBindingProperties
        .getExtendedProducerProperties(producerProperties.getBindingName());

    setProperties(producerProperties, oldProducerProperties);
  }
}
