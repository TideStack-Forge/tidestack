package com.ouroboros.websocket;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

/** Business modules enforce access to their destinations before a frame reaches the broker. */
public interface StompDestinationAuthorizer {
  void authorize(StompHeaderAccessor frame);
}
