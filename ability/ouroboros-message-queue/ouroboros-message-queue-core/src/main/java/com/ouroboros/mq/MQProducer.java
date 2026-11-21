package com.ouroboros.mq;

import org.springframework.messaging.Message;

public interface MQProducer {
  void send(Message<?> message);

  void send(String routingKey, Message<?> message);
}
