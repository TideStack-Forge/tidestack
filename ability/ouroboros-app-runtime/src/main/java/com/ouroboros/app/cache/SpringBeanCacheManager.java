package com.ouroboros.app.cache;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.Duration;

import jakarta.annotation.Priority;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import com.ouroboros.cache.AbstractSpringCacheManagerAdapter;

@Component
public class SpringBeanCacheManager extends AbstractSpringCacheManagerAdapter {
  private final CacheManager cacheManager;
  private final RedisAtomicCacheOperations redis;

  public SpringBeanCacheManager(ObjectProvider<CacheManager> cacheManagerProvider,
                                ObjectProvider<RedisConnectionFactory> connections) {
    cacheManager = cacheManagerProvider.getIfAvailable(ConcurrentMapCacheManager::new);
    var factory = connections.getIfAvailable();
    redis = factory == null ? null : new RedisAtomicCacheOperations(factory);
    SingletonHolder.INSTANCE = this;
  }

  @Override
  protected CacheManager getSpringCacheManager() {
    return cacheManager;
  }

  @Override
  public Capabilities capabilities() {
    if (cacheManager instanceof RedisCacheManager) {
      return new Capabilities(redis != null, true);
    }
    return new Capabilities(cacheManager instanceof ConcurrentMapCacheManager local && !local.isStoreByValue(), false);
  }

  private RedisCache redisCache(String name) {
    var cache = cacheManager.getCache(name);
    if (!(cache instanceof RedisCache region) || redis == null) {
      throw new IllegalStateException("Redis atomic cache requires a region and connection factory: " + name);
    }
    return region;
  }

  @Override
  public boolean putIfAbsent(String name, String key, String value, Duration ttl) {
    return cacheManager instanceof RedisCacheManager
        ? redisOperations().create(redisCache(name), key, value, ttl)
        : super.putIfAbsent(name, key, value, ttl);
  }

  @Override
  public boolean compareAndSet(String name, String key, String expected, String replacement, Duration ttl) {
    return cacheManager instanceof RedisCacheManager
        ? redisOperations().replace(redisCache(name), key, expected, replacement, ttl)
        : super.compareAndSet(name, key, expected, replacement, ttl);
  }

  @Override
  public boolean evictIfEquals(String name, String key, String expected) {
    return cacheManager instanceof RedisCacheManager
        ? redisOperations().remove(redisCache(name), key, expected)
        : super.evictIfEquals(name, key, expected);
  }

  @Override
  public Map<String, Object> getMany(String name, List<String> keys) {
    return cacheManager instanceof RedisCacheManager
        ? redisOperations().readMany(redisCache(name), keys)
        : super.getMany(name, keys);
  }

  @Override
  public Optional<Object> getAndEvict(String name, String key) {
    return cacheManager instanceof RedisCacheManager
        ? Optional.ofNullable(redisOperations().take(redisCache(name), key))
        : super.getAndEvict(name, key);
  }

  @Override
  public void put(String name, String key, Object value, long timeout) {
    if (cacheManager instanceof RedisCacheManager) {
      redisOperations().put(redisCache(name), key, value, Duration.ofMillis(timeout));
    } else {
      super.put(name, key, value, timeout);
    }
  }

  @Override
  public void renew(String name, String key, long timeout) {
    if (cacheManager instanceof RedisCacheManager) {
      redisOperations().renew(redisCache(name), key, Duration.ofMillis(timeout));
    } else {
      super.renew(name, key, timeout);
    }
  }

  private RedisAtomicCacheOperations redisOperations() {
    if (redis == null) {
      throw new IllegalStateException("Redis connection factory is unavailable");
    }
    return redis;
  }

  @Priority(1)
  public static class SingletonHolder implements com.ouroboros.cache.CacheManager {
    static SpringBeanCacheManager INSTANCE;

    private SpringBeanCacheManager delegate() {
      if (INSTANCE == null) {
        throw new IllegalStateException("Spring cache manager is not initialized");
      }
      return INSTANCE;
    }

    @Override
    public Capabilities capabilities() {
      return delegate().capabilities();
    }

    @Override
    public boolean putIfAbsent(String name, String key, String value, Duration ttl) {
      return delegate().putIfAbsent(name, key, value, ttl);
    }

    @Override
    public boolean compareAndSet(String name, String key, String expected, String replacement, Duration ttl) {
      return delegate().compareAndSet(name, key, expected, replacement, ttl);
    }

    @Override
    public boolean evictIfEquals(String name, String key, String expected) {
      return delegate().evictIfEquals(name, key, expected);
    }

    @Override
    public Map<String, Object> getMany(String name, List<String> keys) {
      return delegate().getMany(name, keys);
    }

    @Override
    public Optional<Object> getAndEvict(String name, String key) {
      return delegate().getAndEvict(name, key);
    }

    @Override
    public Optional<Object> get(String name, String key) {
      return Optional.ofNullable(INSTANCE).flatMap(ins -> ins.get(name, key));
    }

    @Override
    public void evict(String name, String key) {
      Optional.ofNullable(INSTANCE).ifPresent(ins -> ins.evict(name, key));
    }

    @Override
    public void clear() {
      Optional.ofNullable(INSTANCE).ifPresent(AbstractSpringCacheManagerAdapter::clear);
    }

    @Override
    public void clear(String name) {
      Optional.ofNullable(INSTANCE).ifPresent(ins -> ins.clear(name));
    }

    @Override
    public void put(String name, String key, Object value) {
      Optional.ofNullable(INSTANCE).ifPresent(ins -> ins.put(name, key, value));
    }

    @Override
    public void put(String name, String key, Object value, long timeout) {
      Optional.ofNullable(INSTANCE).ifPresent(ins -> ins.put(name, key, value, timeout));
    }

    @Override
    public void renew(String name, String key, long timeout) {
      Optional.ofNullable(INSTANCE).ifPresent(ins -> ins.renew(name, key, timeout));
    }
  }
}
