package com.ouroboros.websocket;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import com.ouroboros.websocket.config.WebSocketBrokerProperties;

public class DefaultWebSocketBrokerContext implements WebSocketBrokerContext {
  private final WebSocketBrokerProperties properties;
  private final String instanceId;

  public DefaultWebSocketBrokerContext(WebSocketBrokerProperties properties) {
    this(properties, null);
  }

  public DefaultWebSocketBrokerContext(WebSocketBrokerProperties properties, String fallbackInstanceId) {
    this.properties = properties;
    this.instanceId = generateInstanceId(fallbackInstanceId);
  }

  @Override
  public String getInstanceId() {
    return instanceId;
  }

  @Override
  public WebSocketBrokerProperties getProperties() {
    return properties;
  }

  private String generateInstanceId(String fallbackInstanceId) {
    String configured = properties.getInstanceId();
    if (configured != null && !configured.isEmpty()) {
      return configured;
    }
    if (fallbackInstanceId != null && !fallbackInstanceId.isEmpty()) {
      return fallbackInstanceId;
    }
    try {
      return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
    } catch (UnknownHostException e) {
      return "unknown-" + UUID.randomUUID().toString().substring(0, 8);
    }
  }
}
