package com.ouroboros.cache;

public class CacheRegionFailure {
  private String name;
  private String error;

  public String getName() {
    return name;
  }

  public CacheRegionFailure setName(String name) {
    this.name = name;
    return this;
  }

  public String getError() {
    return error;
  }

  public CacheRegionFailure setError(String error) {
    this.error = error;
    return this;
  }
}
