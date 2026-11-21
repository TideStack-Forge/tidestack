package com.ouroboros.cache;

import java.util.Date;

public class CacheEntryDescriptor {
  private String cacheName;
  private String entryToken;
  private String keyDisplay;
  private String keyType;
  private String valueType;
  private String valuePreview;
  private String valueJson;
  private Long ttlMillis;
  private Date expireAt;

  public String getCacheName() {
    return cacheName;
  }

  public CacheEntryDescriptor setCacheName(String cacheName) {
    this.cacheName = cacheName;
    return this;
  }

  public String getEntryToken() {
    return entryToken;
  }

  public CacheEntryDescriptor setEntryToken(String entryToken) {
    this.entryToken = entryToken;
    return this;
  }

  public String getKeyDisplay() {
    return keyDisplay;
  }

  public CacheEntryDescriptor setKeyDisplay(String keyDisplay) {
    this.keyDisplay = keyDisplay;
    return this;
  }

  public String getKeyType() {
    return keyType;
  }

  public CacheEntryDescriptor setKeyType(String keyType) {
    this.keyType = keyType;
    return this;
  }

  public String getValueType() {
    return valueType;
  }

  public CacheEntryDescriptor setValueType(String valueType) {
    this.valueType = valueType;
    return this;
  }

  public String getValuePreview() {
    return valuePreview;
  }

  public CacheEntryDescriptor setValuePreview(String valuePreview) {
    this.valuePreview = valuePreview;
    return this;
  }

  public String getValueJson() {
    return valueJson;
  }

  public CacheEntryDescriptor setValueJson(String valueJson) {
    this.valueJson = valueJson;
    return this;
  }

  public Long getTtlMillis() {
    return ttlMillis;
  }

  public CacheEntryDescriptor setTtlMillis(Long ttlMillis) {
    this.ttlMillis = ttlMillis;
    return this;
  }

  public Date getExpireAt() {
    return expireAt;
  }

  public CacheEntryDescriptor setExpireAt(Date expireAt) {
    this.expireAt = expireAt;
    return this;
  }
}
