package com.ouroboros.app.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Real Redis contract; run with -Dlogicflow.redis.port=<isolated Redis port>. */
@EnabledIfSystemProperty(named = "logicflow.redis.port", matches = "[0-9]+")
class SpringBeanCacheManagerRedisIT {
  @Test
  void canonicalCacheFacadeUsesConfiguredRedisForAtomicSharedControl() {
    try (var fixture = new Fixture()) {
      var facade = com.ouroboros.cache.CacheManager.getCacheManager();
      var peer = fixture.clients.getFirst();
      assertTrue(facade.capabilities().shared());
      assertTrue(facade.capabilities().atomicUpdates());
      assertTrue(facade.putIfAbsent(fixture.region, "claim", "first", Duration.ofSeconds(30)));
      assertEquals("first", peer.get(fixture.region, "claim").orElseThrow());
      assertTrue(peer.compareAndSet(fixture.region, "claim", "first", "next", Duration.ofSeconds(30)));
      assertEquals(java.util.Map.of("claim", "next"), facade.getMany(fixture.region, List.of("claim", "absent")));
      assertFalse(facade.evictIfEquals(fixture.region, "claim", "first"));
      assertTrue(facade.evictIfEquals(fixture.region, "claim", "next"));
      facade.put(fixture.region, "packet", "data", 30000);
      facade.renew(fixture.region, "packet", 30000);
      assertEquals("data", facade.getAndEvict(fixture.region, "packet").orElseThrow());
      assertTrue(peer.get(fixture.region, "packet").isEmpty());
    }
  }

  @Test
  void twoClientsCompeteAndStaleOwnersCannotReplaceOrRelease() {
    try (var fixture = new Fixture()) {
      var wins = new AtomicInteger();
      var tasks = new ArrayList<CompletableFuture<Void>>();
      for (int i = 0; i < 100; i++) {
        int index = i;
        tasks.add(CompletableFuture.runAsync(() -> {
          if (fixture.clients.get(index % 2).putIfAbsent(fixture.region, "target", "owner-" + index, Duration.ofSeconds(30))) {
            wins.incrementAndGet();
          }
        }));
      }
      CompletableFuture.allOf(tasks.toArray(CompletableFuture<?>[]::new)).join();
      assertEquals(1, wins.get());
      var first = fixture.clients.getFirst();
      var second = fixture.clients.getLast();
      String winner = (String) first.get(fixture.region, "target").orElseThrow();
      assertEquals(winner, second.get(fixture.region, "target").orElseThrow());
      assertTrue(first.compareAndSet(fixture.region, "target", winner, "next", Duration.ofSeconds(30)));
      assertFalse(second.evictIfEquals(fixture.region, "target", winner));
      assertFalse(second.compareAndSet(fixture.region, "target", winner, "stale", Duration.ofSeconds(30)));
      assertEquals("next", second.get(fixture.region, "target").orElseThrow());
      assertTrue(second.evictIfEquals(fixture.region, "target", "next"));
      assertTrue(first.get(fixture.region, "target").isEmpty());
      assertTrue(first.capabilities().shared());
    }
  }

  @Test
  void nativeExpiryAndRenewalAreVisibleAcrossClients() throws InterruptedException {
    try (var fixture = new Fixture()) {
      var first = fixture.clients.getFirst();
      var second = fixture.clients.getLast();
      assertTrue(first.putIfAbsent(fixture.region, "target", "old", Duration.ofMillis(50)));
      Thread.sleep(150);
      assertFalse(second.compareAndSet(fixture.region, "target", "old", "revived", Duration.ofSeconds(30)));
      assertTrue(second.get(fixture.region, "target").isEmpty());
      assertTrue(second.putIfAbsent(fixture.region, "target", "new", Duration.ofSeconds(10)));
      first.renew(fixture.region, "target", 50);
      Thread.sleep(150);
      assertTrue(second.get(fixture.region, "target").isEmpty());
    }
  }

  @Test
  void customPrefixAndValueSerializerAreUsedByAtomicOperations() {
    try (var fixture = new Fixture()) {
      var first = fixture.clients.getFirst();
      var second = fixture.clients.getLast();
      assertTrue(first.putIfAbsent(fixture.stringRegion, "key", "value", Duration.ofSeconds(30)));
      assertTrue(second.compareAndSet(fixture.stringRegion, "key", "value", "changed", Duration.ofSeconds(30)));
      assertEquals(java.util.Map.of("key", "changed"), second.getMany(fixture.stringRegion, List.of("missing", "key")));
      assertTrue(first.getMany(fixture.region, List.of()).isEmpty());
      assertEquals("changed", first.getAndEvict(fixture.stringRegion, "key").orElseThrow());
      assertTrue(second.get(fixture.stringRegion, "key").isEmpty());
      assertThrows(IllegalArgumentException.class,
          () -> first.putIfAbsent(fixture.region, "invalid", "value", Duration.ZERO));
      assertTrue(second.get(fixture.region, "invalid").isEmpty());
    }
  }

  @Test
  void mismatchedConnectionDatabaseFailsBeforeMutatingDomainKey() {
    try (var fixture = new Fixture()) {
      var wrong = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("logicflow.redis.port")));
      wrong.setDatabase(1);
      wrong.afterPropertiesSet();
      try {
        var adapter = adapter(fixture.managers.getFirst(), wrong);
        assertThrows(IllegalStateException.class,
            () -> adapter.putIfAbsent(fixture.region, "target", "owner", Duration.ofSeconds(30)));
        assertTrue(fixture.clients.getFirst().get(fixture.region, "target").isEmpty());
      } finally {
        wrong.destroy();
      }
    }
  }

  private static SpringBeanCacheManager adapter(CacheManager manager, RedisConnectionFactory factory) {
    var beans = new StaticListableBeanFactory();
    beans.addBean("cacheManager", manager);
    beans.addBean("connections", factory);
    return new SpringBeanCacheManager(beans.getBeanProvider(CacheManager.class), beans.getBeanProvider(RedisConnectionFactory.class));
  }

  private static final class Fixture implements AutoCloseable {
    final String region = "logicflow-it-" + UUID.randomUUID();
    final String stringRegion = region + "-strings";
    final List<LettuceConnectionFactory> factories = new ArrayList<>();
    final List<RedisCacheManager> managers = new ArrayList<>();
    final List<SpringBeanCacheManager> clients = new ArrayList<>();

    Fixture() {
      for (int i = 0; i < 2; i++) {
        var factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("logicflow.redis.port")));
        factory.afterPropertiesSet();
        factories.add(factory);
        var config = RedisCacheConfiguration.defaultCacheConfig().computePrefixWith(name -> "custom/" + name + "/");
        var manager = RedisCacheManager.builder(factory).cacheDefaults(config)
            .withCacheConfiguration(stringRegion, config.serializeValuesWith(SerializationPair.fromSerializer(new StringRedisSerializer())))
            .build();
        manager.afterPropertiesSet();
        managers.add(manager);
        clients.add(adapter(manager, factory));
      }
    }

    @Override
    public void close() {
      managers.getFirst().getCache(region).clear();
      managers.getFirst().getCache(stringRegion).clear();
      factories.forEach(LettuceConnectionFactory::destroy);
    }
  }
}
