package com.ouroboros.cache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerDefaultMethodsTest {

  @Test
  void getOrPut_returnsExistingValueAndAvoidsSupplierCall() {
    var manager = new FakeCacheManager();
    manager.put("users", "u:1", "alice");
    var calls = new AtomicInteger();

    var value = manager.getOrPut("users", "u:1", String.class, () -> {
      calls.incrementAndGet();
      return "bob";
    });

    assertEquals("alice", value);
    assertEquals(0, calls.get());
  }

  @Test
  void getOrPut_typedVariant_evictsTypeMismatchAndRepopulates() {
    var manager = new FakeCacheManager();
    manager.put("users", "u:1", Integer.valueOf(1));

    var value = manager.getOrPut("users", "u:1", String.class, () -> "alice");

    assertEquals("alice", value);
    assertEquals(1, manager.evictCount);
    assertEquals("alice", manager.get("users", "u:1").orElse(null));
  }

  @Test
  void getOrPut_deprecatedVariant_withTimeout_coversCacheMissPath() {
    var manager = new FakeCacheManager();

    var value = manager.getOrPut("users", "u:2", (Supplier<String>) () -> "bob", 100L);

    assertEquals("bob", value);
    assertEquals(1, manager.putWithTimeoutCount);
  }

  @Test
  void defaultUtilityMethods_coverGetAndPutOverloads() {
    var manager = new FakeCacheManager();

    manager.put("users", "a", "A");
    assertEquals(Optional.of("A"), manager.get("users", "a"));
    assertEquals(Optional.empty(), manager.get("missing"));

    manager.put("users", "b", "B", Duration.ofMillis(50));
    manager.put("users", "c", "C", LocalDateTime.now().plusSeconds(1));
    manager.put("users", "d", "D", new Date(System.currentTimeMillis() + 1000));

    assertEquals("B", manager.get("users", "b").orElse(null));
    assertEquals("C", manager.get("users", "c").orElse(null));
    assertEquals("D", manager.get("users", "d").orElse(null));

    var numbers = new ArrayList<>();
    numbers.add(1);
    numbers.add(2);
    manager.put("users", "list", numbers);

    var scoreMap = new HashMap<>();
    scoreMap.put("x", 1);
    manager.put("users", "map", scoreMap);

    manager.put("users", "array", new int[]{1, 2, 3});
  }

  @Test
  void getAndEvict_defaultMethod_removesValueAfterRead() {
    var manager = new FakeCacheManager();
    manager.put("users", "u:3", "charlie");

    var value = manager.getAndEvict("users", "u:3");

    assertEquals(Optional.of("charlie"), value);
    assertEquals(Optional.empty(), manager.get("users", "u:3"));
  }

  private static class FakeCacheManager implements CacheManager {
    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();
    private int evictCount;
    private int putWithTimeoutCount;

    @Override
    public Optional<Object> get(String name, String key) {
      return Optional.ofNullable(store.get(name + "::" + key));
    }

    @Override
    public void evict(String name, String key) {
      evictCount++;
      store.remove(name + "::" + key);
    }

    @Override
    public void clear() {
      store.clear();
    }

    @Override
    public void clear(String name) {
      var prefix = name + "::";
      store.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public void put(String name, String key, Object value) {
      store.put(name + "::" + key, value);
    }

    @Override
    public void put(String name, String key, Object value, long timeout) {
      putWithTimeoutCount++;
      put(name, key, value);
    }

    @Override
    public void renew(String name, String key, long timeout) {
      // no-op for default-method coverage tests
    }
  }
}
