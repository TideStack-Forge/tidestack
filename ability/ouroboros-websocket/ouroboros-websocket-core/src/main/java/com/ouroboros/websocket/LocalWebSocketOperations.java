package com.ouroboros.websocket;

import java.util.Map;

public interface LocalWebSocketOperations {
  void send(String destination, Object payload, Map<String, Object> headers);

  void sendToUser(String user, String destination, Object payload, Map<String, Object> headers);

  void sendToSession(String sessionId, String destination, Object payload, Map<String, Object> headers);
}
