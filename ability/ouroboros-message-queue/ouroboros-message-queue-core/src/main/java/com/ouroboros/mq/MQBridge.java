package com.ouroboros.mq;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

import com.ouroboros.mq.binding.MQBindingProperties;
import com.ouroboros.mq.builder.MQConsumerBuilder;
import com.ouroboros.mq.builder.MQProducerBuilder;

/** 消息绑定、发送与订阅的消费契约；具体后端由运行时装配。 */
public interface MQBridge {
  void prepareBindingProperties(MQBindingProperties<?> bindingProperties);
  void subscribe(String bindingName, MessageHandler handler);
  void unsubscribe(String bindingName, MessageHandler handler);
  void send(String bindingName, Message<?> message);
  void send(String bindingName, String routingKey, Message<?> message);
  MQConsumerBuilder bindingConsumer(String bindingName);
  MQProducerBuilder bindingProducer(String bindingName);
}
