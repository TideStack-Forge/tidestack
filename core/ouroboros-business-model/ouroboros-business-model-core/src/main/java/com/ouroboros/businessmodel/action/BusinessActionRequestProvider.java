package com.ouroboros.businessmodel.action;

import java.util.Map;

/** Optional synchronous request view supplied by the web integration boundary. */
@FunctionalInterface
public interface BusinessActionRequestProvider {
  Map<String, Object> currentRequest();
}
