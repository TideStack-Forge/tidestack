package com.ouroboros.cache;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryCacheManagerTest {

  @Test
  void memoryCacheManager_supportsGetPutEvictAndClear() {
    var manager = new MemoryCacheManager();

    manager.put("users", "u:1", "alice");
    assertEquals(Optional.of("alice"), manager.get("users", "u:1"));

    manager.evict("users", "u:1");
    assertEquals(Optional.empty(), manager.get("users", "u:1"));

    manager.put("users", "u:2", "bob");
    manager.put("roles", "r:1", "admin");
    manager.clear("users");
    assertEquals(Optional.empty(), manager.get("users", "u:2"));
    assertEquals(Optional.of("admin"), manager.get("roles", "r:1"));

    manager.clear();
    assertEquals(Optional.empty(), manager.get("roles", "r:1"));
  }

  @Test
  void memoryCacheManager_getAndEvict_andPutWithTimeout_areEffective() throws InterruptedException {
    var manager = new MemoryCacheManager();

    manager.put("tokens", "once", "value", 20L);
    assertEquals(Optional.of("value"), manager.getAndEvict("tokens", "once"));
    assertEquals(Optional.empty(), manager.get("tokens", "once"));

    manager.put("tokens", "ttl", "will-expire");
    manager.renew("tokens", "ttl", 20L);
    Thread.sleep(60L);

    assertEquals(Optional.empty(), manager.get("tokens", "ttl"));
  }

  @Test
  void singletonHolder_delegatesAllOperationsToSingletonInstance() {
    var singleton = new MemoryCacheManager.SingletonHolder();

    singleton.put("singleton", "k1", "v1");
    assertEquals(Optional.of("v1"), singleton.get("singleton", "k1"));

    singleton.put("singleton", "k2", "v2", 1000L);
    assertEquals(Optional.of("v2"), singleton.get("singleton", "k2"));

    singleton.renew("singleton", "k2", 50L);
    singleton.evict("singleton", "k1");
    assertEquals(Optional.empty(), singleton.get("singleton", "k1"));

    singleton.clear("singleton");
    assertEquals(Optional.empty(), singleton.get("singleton", "k2"));

    singleton.put("singleton", "k3", "v3");
    singleton.clear();
    assertEquals(Optional.empty(), singleton.get("singleton", "k3"));
  }
}
