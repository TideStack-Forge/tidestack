package com.ouroboros.websocket.broker.redis;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.websocket.LocalWebSocketOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

class RedisLocalWebSocketOperations implements LocalWebSocketOperations {

  private final SimpMessagingTemplate messagingTemplate;

  RedisLocalWebSocketOperations(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void send(String destination, Object payload, Map<String, Object> headers) {
    messagingTemplate.convertAndSend(destination, payload, headers);
  }

  @Override
  public void sendToUser(String user, String destination, Object payload, Map<String, Object> headers) {
    messagingTemplate.convertAndSendToUser(user, destination, payload, headers);
  }

  @Override
  public void sendToSession(String sessionId, String destination, Object payload, Map<String, Object> headers) {
    String sessionDestination = destination + "-" + sessionId;
    Map<String, Object> effectiveHeaders = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    Object simpOrigDestination = effectiveHeaders.get("simpOrigDestination");
    String originalDestination = simpOrigDestination == null ? destination : String.valueOf(simpOrigDestination);

    messagingTemplate.convertAndSend(sessionDestination, payload, effectiveHeaders, message -> {
      SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
      if (accessor != null) {
        accessor.setNativeHeader("simpOrigDestination", originalDestination);
      }
      return message;
    });
  }
}
