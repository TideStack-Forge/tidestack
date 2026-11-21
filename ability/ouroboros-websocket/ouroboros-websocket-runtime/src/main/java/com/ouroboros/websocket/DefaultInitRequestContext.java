package com.ouroboros.websocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultInitRequestContext implements InitRequestContext {
  private final String sessionId;
  private final String user;
  private final String destination;
  private final Map<String, Object> headers;

  public DefaultInitRequestContext(String sessionId, String user, String destination, Map<String, Object> headers) {
    this.sessionId = sessionId;
    this.user = user;
    this.destination = destination;
    this.headers = Collections.unmodifiableMap(headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers));
  }

  @Override
  public String getSessionId() {
    return sessionId;
  }

  @Override
  public String getUser() {
    return user;
  }

  @Override
  public String getDestination() {
    return destination;
  }

  @Override
  public Map<String, Object> getHeaders() {
    return headers;
  }
}
