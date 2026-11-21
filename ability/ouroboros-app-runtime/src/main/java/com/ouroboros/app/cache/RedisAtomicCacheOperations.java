package com.ouroboros.app.cache;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;

/** Uses each region's actual key prefix and serializers, including custom cache configuration. */
final class RedisAtomicCacheOperations {
  private static final byte[] CAS = script("if redis.call('get',KEYS[1]) == ARGV[1] then redis.call('psetex',KEYS[1],ARGV[3],ARGV[2]); return 1 end; return 0");
  private static final byte[] REMOVE = script("if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) end; return 0");
  private static final byte[] TAKE = script("local v=redis.call('get',KEYS[1]); if v then redis.call('del',KEYS[1]) end; return v");
  private final RedisConnectionFactory connections;
  private final ConcurrentMap<RedisCache, Boolean> verifiedRegions = new ConcurrentHashMap<>();

  RedisAtomicCacheOperations(RedisConnectionFactory connections) {
    this.connections = Objects.requireNonNull(connections);
  }

  void put(RedisCache cache, String key, Object value, Duration ttl) {
    positiveMillis(ttl);
    verify(cache);
    cache.getNativeCache().put(cache.getName(), key(cache, key), value(cache, value), ttl);
  }

  boolean create(RedisCache cache, String key, String value, Duration ttl) {
    positiveMillis(ttl);
    verify(cache);
    return cache.getNativeCache().putIfAbsent(cache.getName(), key(cache, key), value(cache, value), ttl) == null;
  }

  boolean replace(RedisCache cache, String key, String expected, String replacement, Duration ttl) {
    byte[] millis = script(Long.toString(positiveMillis(ttl)));
    verify(cache);
    try (var connection = connections.getConnection()) {
      Long result = connection.scriptingCommands().eval(CAS, ReturnType.INTEGER, 1,
          key(cache, key), value(cache, expected), value(cache, replacement), millis);
      return Long.valueOf(1).equals(result);
    }
  }

  boolean remove(RedisCache cache, String key, String expected) {
    verify(cache);
    try (var connection = connections.getConnection()) {
      Long result = connection.scriptingCommands().eval(REMOVE, ReturnType.INTEGER, 1, key(cache, key), value(cache, expected));
      return Long.valueOf(1).equals(result);
    }
  }

  Object take(RedisCache cache, String key) {
    verify(cache);
    try (var connection = connections.getConnection()) {
      byte[] result = connection.scriptingCommands().eval(TAKE, ReturnType.VALUE, 1, key(cache, key));
      return result == null ? null : cache.getCacheConfiguration().getValueSerializationPair().read(ByteBuffer.wrap(result));
    }
  }

  Map<String, Object> readMany(RedisCache cache, List<String> keys) {
    var result = new LinkedHashMap<String, Object>();
    if (keys.isEmpty()) {
      return result;
    }
    verify(cache);
    try (var connection = connections.getConnection()) {
      var values = connection.stringCommands().mGet(keys.stream().map(key -> key(cache, key)).toArray(byte[][]::new));
      for (int index = 0; index < keys.size(); index++) {
        byte[] value = values.get(index);
        if (value != null) {
          result.put(keys.get(index), cache.getCacheConfiguration().getValueSerializationPair().read(ByteBuffer.wrap(value)));
        }
      }
    }
    return result;
  }

  void renew(RedisCache cache, String key, Duration ttl) {
    long millis = positiveMillis(ttl);
    verify(cache);
    try (var connection = connections.getConnection()) {
      connection.keyCommands().pExpire(key(cache, key), millis);
    }
  }

  // Custom CacheManager and RedisConnectionFactory beans can point at different databases.
  // Fail before a domain mutation when their keyspaces differ; never silently use JVM expiry.
  private void verify(RedisCache cache) {
    verifiedRegions.computeIfAbsent(cache, region -> {
      String probe = "__atomic_probe__" + UUID.randomUUID();
      byte[] key = key(region, probe);
      byte[] value = value(region, probe);
      region.getNativeCache().put(region.getName(), key, value, Duration.ofSeconds(10));
      try (var connection = connections.getConnection()) {
        if (!Arrays.equals(value, connection.stringCommands().get(key))) {
          throw new IllegalStateException("Redis cache and atomic connection use different keyspaces: " + region.getName());
        }
        return true;
      } finally {
        region.getNativeCache().remove(region.getName(), key);
      }
    });
  }

  private byte[] key(RedisCache cache, String key) {
    var config = cache.getCacheConfiguration();
    String prefixed = config.usePrefix() ? config.getKeyPrefixFor(cache.getName()) + key : key;
    return bytes(config.getKeySerializationPair().write(prefixed));
  }

  private byte[] value(RedisCache cache, Object value) {
    return bytes(cache.getCacheConfiguration().getValueSerializationPair().write(Objects.requireNonNull(value)));
  }

  private static byte[] bytes(ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }

  private static byte[] script(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static long positiveMillis(Duration ttl) {
    long millis = Objects.requireNonNull(ttl, "ttl").toMillis();
    if (millis <= 0) {
      throw new IllegalArgumentException("Cache TTL must be at least one millisecond");
    }
    return millis;
  }
}
