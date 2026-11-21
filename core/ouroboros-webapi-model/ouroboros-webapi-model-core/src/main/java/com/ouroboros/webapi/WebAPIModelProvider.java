package com.ouroboros.webapi;

import java.util.Map;

public interface WebAPIModelProvider {
  Map<String, WebAPIModel> getWebAPIModelMap();

  default boolean isBusinessModelProjection() { return false; }
}
