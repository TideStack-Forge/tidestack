package com.ouroboros.cache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.util.SPI;

@SuppressWarnings("unused")
public interface CacheManager {
  record Capabilities(boolean atomicUpdates, boolean shared) {}

  default Capabilities capabilities() {
    return new Capabilities(false, false);
  }

  /** Atomically creates an absent entry with a positive TTL. Unsupported adapters fail explicitly. */
  default boolean putIfAbsent(String name, String key, String value, Duration ttl) {
    throw new UnsupportedOperationException("Atomic cache updates are unavailable");
  }

  /** Replaces an unexpired entry only when its serialized string value matches. */
  default boolean compareAndSet(String name, String key, String expected, String replacement, Duration ttl) {
    throw new UnsupportedOperationException("Atomic cache updates are unavailable");
  }

  /** Removes an unexpired entry only when its value matches. */
  default boolean evictIfEquals(String name, String key, String expected) {
    throw new UnsupportedOperationException("Atomic cache updates are unavailable");
  }

  Optional<Object> get(String name, String key);

  /** Returns present values for the requested keys; missing or expired entries are absent. */
  default Map<String, Object> getMany(String name, List<String> keys) {
    var values = new LinkedHashMap<String, Object>();
    keys.forEach(key -> get(name, key).ifPresent(value -> values.put(key, value)));
    return values;
  }

  default Optional<Object> get(String key) {
    return get("default", key);
  }

  /**
   * @deprecated Use {@link #getOrPut(String, String, Class, Supplier)} for type-safe caching
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  default <T> T getOrPut(String name, String key, Supplier<T> supplier) {
    var log = LoggerFactory.getLogger(CacheManager.class);
    var value = get(name, key);
    if (value.isPresent()) {
      try {
        return (T) value.get();
      } catch (ClassCastException e) {
        log.error("Cache type mismatch for key '{}' in cache '{}': expected type but got {}", 
                  key, name, value.get().getClass().getName(), e);
        evict(name, key);
      }
    }
    var result = supplier.get();
    put(name, key, result);
    return result;
  }

  default <T> T getOrPut(String name, String key, Class<T> type, Supplier<T> supplier) {
    var log = LoggerFactory.getLogger(CacheManager.class);
    var value = get(name, key);
    if (value.isPresent()) {
      var cached = value.get();
      if (type.isInstance(cached)) {
        return type.cast(cached);
      } else {
        log.error("Cache type mismatch for key '{}' in cache '{}': expected {} but got {}", 
                  key, name, type.getName(), cached.getClass().getName());
        evict(name, key);
      }
    }
    var result = supplier.get();
    put(name, key, result);
    return result;
  }

  /**
   * @deprecated Use {@link #getOrPut(String, String, Class, Supplier, long)} for type-safe caching
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  default <T> T getOrPut(String name, String key, Supplier<T> supplier, long timeout) {
    var log = LoggerFactory.getLogger(CacheManager.class);
    var value = get(name, key);
    if (value.isPresent()) {
      try {
        return (T) value.get();
      } catch (ClassCastException e) {
        log.error("Cache type mismatch for key '{}' in cache '{}': expected {} but got {}", 
                  key, name, value.get().getClass().getName(), e);
        evict(name, key);
      }
    }
    var result = supplier.get();
    put(name, key, result, timeout);
    return result;
  }

  /**
   * Atomically get and remove a cache entry. Returns the value if present, then deletes it.
   * Used for one-time token consumption (state, loginCode, bindingToken).
   * Implementations that can provide true atomicity SHOULD override this method.
   */
  default Optional<Object> getAndEvict(String name, String key) {
    var value = get(name, key);
    value.ifPresent(v -> evict(name, key));
    return value;
  }

  void evict(String name, String key);

  void clear();

  void clear(String name);

  void put(String name, String key, Object value);

  void put(String name, String key, Object value, long timeout);

  default void put(String name, String key, Object value, Duration timeout) {
    put(name, key, value, timeout.toMillis());
  }

  default void put(String name, String key, Object value, LocalDateTime expireTime) {
    put(name, key, value, Duration.between(LocalDateTime.now(), expireTime));
  }

  default void put(String name, String key, Object value, Date expireDate) {
    LocalDateTime expireAt = LocalDateTime.ofInstant(expireDate.toInstant(), ZoneId.systemDefault());
    put(name, key, value, Duration.between(LocalDateTime.now(), expireAt));
  }

  void renew(String name, String key, long timeout);

  static CacheManager getCacheManager() {
    return Optional.ofNullable(SPI.getCachedPrimaryService(CacheManager.class))
        .orElseGet(MemoryCacheManager.SingletonHolder::new);
  }
}
