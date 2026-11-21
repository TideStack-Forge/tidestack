package com.ouroboros.websocket.broker.redis;

import com.ouroboros.websocket.DistributedSessionRegistry;

public interface RedisDistributedSessionRegistry extends DistributedSessionRegistry {
  int getSessionCount();

  void renewLocalSessions();
}
