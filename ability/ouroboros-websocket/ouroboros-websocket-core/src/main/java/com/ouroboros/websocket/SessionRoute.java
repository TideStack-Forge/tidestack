package com.ouroboros.websocket;

import java.time.Instant;

public final class SessionRoute {
  private final String sessionId;
  private final String userId;
  private final String instanceId;
  private final Instant connectedAt;

  public SessionRoute(String sessionId, String userId, String instanceId, Instant connectedAt) {
    this.sessionId = sessionId;
    this.userId = userId;
    this.instanceId = instanceId;
    this.connectedAt = connectedAt;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getUserId() {
    return userId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public Instant getConnectedAt() {
    return connectedAt;
  }
}
