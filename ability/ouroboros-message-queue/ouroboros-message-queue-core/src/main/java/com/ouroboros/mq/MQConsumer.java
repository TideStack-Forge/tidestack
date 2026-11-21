package com.ouroboros.mq;

import org.springframework.messaging.MessageHandler;

public interface MQConsumer {
  void subscribe(MessageHandler messageHandler);

  void unsubscribe(MessageHandler messageHandler);
}
