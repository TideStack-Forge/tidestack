package com.ouroboros.websocket;

import com.ouroboros.websocket.config.WebSocketBrokerProperties;

public interface WebSocketBrokerContext {
  String getInstanceId();

  WebSocketBrokerProperties getProperties();
}
