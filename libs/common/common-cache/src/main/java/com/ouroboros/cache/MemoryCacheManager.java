package com.ouroboros.cache;

import java.util.Optional;
import java.time.Duration;
import java.util.function.LongSupplier;

import jakarta.annotation.Priority;

import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

public class MemoryCacheManager extends AbstractSpringCacheManagerAdapter {
  private final LongSupplier clock;

  public MemoryCacheManager() {
    this(System::nanoTime);
  }

  MemoryCacheManager(LongSupplier clock) {
    this.clock = clock;
  }

  @Override
  protected long nanoTime() {
    return clock.getAsLong();
  }

  @Override
  public Capabilities capabilities() {
    return new Capabilities(true, false);
  }

  ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();

  @Override
  public org.springframework.cache.CacheManager getSpringCacheManager() {
    return cacheManager;
  }

  @Priority(0)
  public static class SingletonHolder implements CacheManager {
    private final static MemoryCacheManager INSTANCE = new MemoryCacheManager();

    @Override
    public Capabilities capabilities() {
      return INSTANCE.capabilities();
    }

    @Override
    public boolean putIfAbsent(String name, String key, String value, Duration ttl) {
      return INSTANCE.putIfAbsent(name, key, value, ttl);
    }

    @Override
    public boolean compareAndSet(String name, String key, String expected, String replacement, Duration ttl) {
      return INSTANCE.compareAndSet(name, key, expected, replacement, ttl);
    }

    @Override
    public boolean evictIfEquals(String name, String key, String expected) {
      return INSTANCE.evictIfEquals(name, key, expected);
    }

    @Override
    public Optional<Object> getAndEvict(String name, String key) {
      return INSTANCE.getAndEvict(name, key);
    }

    @Override
    public Optional<Object> get(String name, String key) {
      return INSTANCE.get(name, key);
    }

    @Override
    public void evict(String name, String key) {
      INSTANCE.evict(name, key);
    }

    @Override
    public void clear() {
      INSTANCE.clear();
    }

    @Override
    public void clear(String name) {
      INSTANCE.clear(name);
    }

    @Override
    public void put(String name, String key, Object value) {
      INSTANCE.put(name, key, value);
    }

    @Override
    public void put(String name, String key, Object value, long timeout) {
      INSTANCE.put(name, key, value, timeout);
    }

    @Override
    public void renew(String name, String key, long timeout) {
      INSTANCE.renew(name, key, timeout);
    }
  }
}
