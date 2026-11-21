package com.ouroboros.cache;

public interface CacheEntryTokenCodec {
  String encode(String cacheName, String inspectorId, String keyPayload);

  DecodedCacheEntryToken decode(String cacheName, String inspectorId, String token);
}
