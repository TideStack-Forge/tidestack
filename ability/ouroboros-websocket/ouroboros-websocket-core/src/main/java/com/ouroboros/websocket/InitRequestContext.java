package com.ouroboros.websocket;

import java.util.Map;

public interface InitRequestContext {
  String getSessionId();

  String getUser();

  String getDestination();

  Map<String, Object> getHeaders();
}
