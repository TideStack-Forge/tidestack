package com.ouroboros.websocket;

import java.util.Collection;
import java.util.Optional;

public interface DistributedSessionRegistry {
  void registerSession(String sessionId, String userId, String instanceId);

  void unregisterSession(String sessionId);

  Optional<SessionRoute> findSession(String sessionId);

  Collection<SessionRoute> findUserSessions(String userId);
}
