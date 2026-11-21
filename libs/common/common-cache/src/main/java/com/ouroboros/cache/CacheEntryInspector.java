package com.ouroboros.cache;

import org.springframework.cache.Cache;

public interface CacheEntryInspector {
  boolean supports(Cache cache);

  CacheRegionDescriptor describeRegion(String cacheName, Cache cache);

  CacheEntryPage inspect(String cacheName, Cache cache, CacheEntryQuery query);

  String getInspectorId();

  String serializeKeyPayload(String cacheName, Cache cache, Object rawKey);

  Object deserializeKeyPayload(String cacheName, Cache cache, String keyPayload);
}
