package com.ouroboros.cache;

import java.util.Optional;
import java.time.Duration;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import java.util.concurrent.*;

import org.springframework.cache.Cache;

import io.vavr.control.Try;

public abstract class AbstractSpringCacheManagerAdapter implements CacheManager {
  private ConcurrentMap<String, ScheduledFuture<?>> scheduledCacheCleanerMap = new ConcurrentHashMap<>();
  private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

  protected abstract org.springframework.cache.CacheManager getSpringCacheManager();

  protected long nanoTime() {
    return System.nanoTime();
  }

  private Optional<ConcurrentMapCacheOperations> local(String name) {
    var cache = getSpringCacheManager().getCache(name);
    return cache instanceof ConcurrentMapCache concurrent && !concurrent.isStoreByValue()
        ? Optional.of(new ConcurrentMapCacheOperations(concurrent.getNativeCache(), this::nanoTime))
        : Optional.empty();
  }

  @Override
  public boolean putIfAbsent(String name, String key, String value, Duration ttl) {
    return local(name).orElseThrow(() -> new UnsupportedOperationException("Atomic updates unavailable for " + name))
        .update(key, null, value, ttl, true);
  }

  @Override
  public boolean compareAndSet(String name, String key, String expected, String replacement, Duration ttl) {
    return local(name).orElseThrow(() -> new UnsupportedOperationException("Atomic updates unavailable for " + name))
        .update(key, expected, replacement, ttl, false);
  }

  @Override
  public boolean evictIfEquals(String name, String key, String expected) {
    return local(name).orElseThrow(() -> new UnsupportedOperationException("Atomic updates unavailable for " + name))
        .remove(key, expected);
  }

  @Override
  public Optional<Object> get(String name, String key) {
    var local = local(name);
    if (local.isPresent()) {
      return local.get().get(key);
    }
    return Optional.ofNullable(getSpringCacheManager().getCache(name))
        .map(cache -> cache.get(key))
        .map(Cache.ValueWrapper::get);
  }

  @Override
  public Optional<Object> getAndEvict(String name, String key) {
    var local = local(name);
    if (local.isPresent()) {
      return local.get().take(key);
    }
    String lockKey = name + "::" + key;
    synchronized (lockKey.intern()) {
      Optional<Object> value = get(name, key);
      value.ifPresent(v -> evict(name, key));
      return value;
    }
  }

  @Override
  public void evict(String name, String key) {
    var local = local(name);
    if (local.isPresent()) {
      local.get().take(key);
      return;
    }
    Optional.ofNullable(getSpringCacheManager().getCache(name))
        .ifPresent(cache -> cache.evict(key));
  }

  @Override
  public void clear() {
    getSpringCacheManager().getCacheNames().forEach(this::clear);
  }

  @Override
  public void clear(String name) {
    var local = local(name);
    if (local.isPresent()) {
      local.get().clear();
      return;
    }
    Optional.ofNullable(getSpringCacheManager().getCache(name))
        .ifPresent(Cache::clear);
  }

  @Override
  public void put(String name, String key, Object value) {
    var local = local(name);
    if (local.isPresent()) {
      local.get().put(key, value, null);
      return;
    }
    Optional.ofNullable(getSpringCacheManager().getCache(name))
        .ifPresent(cache -> cache.put(key, value));
  }

  @Override
  public void put(String name, String key, Object value, long timeout) {
    var local = local(name);
    if (local.isPresent()) {
      local.get().put(key, value, Duration.ofMillis(timeout));
      return;
    }
    Optional.ofNullable(getSpringCacheManager().getCache(name))
        .ifPresent(cache -> cache.put(key, value));
    renew(name, key, timeout);
  }

  @Override
  public void renew(String name, String key, long timeout) {
    var local = local(name);
    if (local.isPresent()) {
      local.get().renew(key, Duration.ofMillis(timeout));
      return;
    }
    var timerKey = name + "::" + key;
    scheduledCacheCleanerMap.compute(timerKey, (k, v) -> {
      if (v != null) {
        Try.run(() -> v.cancel(true));
      }
      return executor.schedule(() -> Try.run(() -> evict(name, key)), timeout, TimeUnit.MILLISECONDS);
    });
  }
}
