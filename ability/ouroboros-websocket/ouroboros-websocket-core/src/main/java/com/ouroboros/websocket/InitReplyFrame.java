package com.ouroboros.websocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InitReplyFrame {
  private final String sessionId;
  private final String destination;
  private final String kind;
  private final Object payload;
  private final Map<String, Object> headers;

  public InitReplyFrame(
      String sessionId,
      String destination,
      String kind,
      Object payload,
      Map<String, Object> headers) {
    this.sessionId = sessionId;
    this.destination = destination;
    this.kind = kind;
    this.payload = payload;
    this.headers = Collections.unmodifiableMap(headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers));
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getDestination() {
    return destination;
  }

  public String getKind() {
    return kind;
  }

  public Object getPayload() {
    return payload;
  }

  public Map<String, Object> getHeaders() {
    return headers;
  }
}
