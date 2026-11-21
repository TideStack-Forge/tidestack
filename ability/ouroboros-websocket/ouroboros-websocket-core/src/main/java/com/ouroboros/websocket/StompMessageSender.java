package com.ouroboros.websocket;

import java.util.Map;

public interface StompMessageSender {
  void send(String destination, Object payload);

  void send(String destination, Object payload, Map<String, Object> headers);

  void sendToUser(String user, String destination, Object payload);

  void sendToUser(String user, String destination, Object payload, Map<String, Object> headers);

  void sendToQueue(String sessionId, String destination, Object payload);

  void sendToQueue(String sessionId, String destination, Object payload, Map<String, Object> headers);
}
