package com.ouroboros.websocket;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

public class DefaultInitReplySender implements InitReplySender {

  private final StompMessageSender stompMessageSender;

  @Autowired
  public DefaultInitReplySender(StompMessageSender stompMessageSender) {
    this.stompMessageSender = stompMessageSender;
  }

  @Override
  public void send(InitReplyFrame frame) {
    Map<String, Object> headers = frame.getHeaders();
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("kind", frame.getKind());
    body.put("subscriptionToken", headers.get("subscriptionToken"));
    body.put("payload", frame.getPayload());
    stompMessageSender.sendToQueue(frame.getSessionId(), frame.getDestination(), body, headers);
  }
}
