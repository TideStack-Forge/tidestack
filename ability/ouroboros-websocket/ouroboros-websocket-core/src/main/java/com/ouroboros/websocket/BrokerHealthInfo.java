package com.ouroboros.websocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BrokerHealthInfo {
  private final WebSocketBrokerType brokerType;
  private final BrokerHealthStatus status;
  private final String message;
  private final Map<String, Object> details;

  public BrokerHealthInfo(
      WebSocketBrokerType brokerType,
      BrokerHealthStatus status,
      String message,
      Map<String, Object> details) {
    this.brokerType = brokerType;
    this.status = status;
    this.message = message;
    this.details = Collections.unmodifiableMap(details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details));
  }

  public WebSocketBrokerType getBrokerType() {
    return brokerType;
  }

  public BrokerHealthStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
