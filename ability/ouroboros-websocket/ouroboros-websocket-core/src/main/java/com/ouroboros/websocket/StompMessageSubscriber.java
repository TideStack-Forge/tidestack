package com.ouroboros.websocket;

import org.springframework.messaging.MessageHandler;

public interface StompMessageSubscriber {
  void onSend(String destination, MessageHandler messageHandler);
}
