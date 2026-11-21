package com.ouroboros.websocket.broker.rabbitmq;

import com.ouroboros.websocket.DistributedSessionRegistry;

public interface RabbitMqDistributedSessionRegistry extends DistributedSessionRegistry {
  int getSessionCount();
}
