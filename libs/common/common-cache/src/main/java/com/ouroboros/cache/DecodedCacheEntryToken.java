package com.ouroboros.cache;

public class DecodedCacheEntryToken {
  private final String cacheName;
  private final String inspectorId;
  private final String keyPayload;

  public DecodedCacheEntryToken(String cacheName, String inspectorId, String keyPayload) {
    this.cacheName = cacheName;
    this.inspectorId = inspectorId;
    this.keyPayload = keyPayload;
  }

  public String getCacheName() {
    return cacheName;
  }

  public String getInspectorId() {
    return inspectorId;
  }

  public String getKeyPayload() {
    return keyPayload;
  }
}
