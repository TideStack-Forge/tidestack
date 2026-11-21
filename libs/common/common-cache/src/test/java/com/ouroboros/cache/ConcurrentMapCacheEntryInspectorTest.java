package com.ouroboros.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentMapCacheEntryInspectorTest {

  @Test
  void describeRegion_reportsExactCountForConcurrentMapCache() {
    var manager = new ConcurrentMapCacheManager();
    var cache = manager.getCache("users");
    cache.put("u:1", "alice");
    cache.put("u:2", "bob");

    var inspector = new ConcurrentMapCacheEntryInspector();
    var region = inspector.describeRegion("users", cache);

    assertEquals("users", region.getName());
    assertEquals("ConcurrentMapCache", region.getProviderType());
    assertTrue(region.getInspectable());
    assertEquals(Long.valueOf(2), region.getEntryCount());
    assertEquals("EXACT", region.getEntryCountMode());
  }

  @Test
  void inspect_returnsInspectableFalsePageForUnsupportedCache() {
    var page = CacheEntryPage.unsupported("当前缓存实现不支持条目级查看");

    assertFalse(page.getInspectable());
    assertEquals(Long.valueOf(0), page.getTotal());
    assertTrue(page.getItems().isEmpty());
    assertEquals("当前缓存实现不支持条目级查看", page.getMessage());
  }

  @Test
  void inspect_buildsSafeEntryDescriptors() {
    var manager = new ConcurrentMapCacheManager();
    var cache = manager.getCache("users");
    cache.put("u:1", "alice");

    var inspector = new ConcurrentMapCacheEntryInspector();
    var page = inspector.inspect("users", cache, new CacheEntryQuery(null, 1, 20));

    assertTrue(page.getInspectable());
    assertEquals(Long.valueOf(1), page.getTotal());
    assertEquals("u:1", page.getItems().get(0).getKeyDisplay());
    assertEquals("String", page.getItems().get(0).getValueType());
    assertTrue(page.getItems().get(0).getValuePreview().contains("length"));
    assertFalse(page.getItems().get(0).getValuePreview().contains("alice"));
    assertEquals("\"alice\"", page.getItems().get(0).getValueJson());
  }

  @Test
  void inspect_fallsBackToRegistryWhenSerializableKeyCannotBeSerialized() {
    var manager = new ConcurrentMapCacheManager();
    var cache = manager.getCache("users");
    var key = new BrokenSerializableKey("u:1");
    cache.put(key, "alice");

    var inspector = new ConcurrentMapCacheEntryInspector();
    var page = inspector.inspect("users", cache, new CacheEntryQuery(null, 1, 20));

    assertEquals(Long.valueOf(1), page.getTotal());
    assertTrue(page.getItems().get(0).getEntryToken().startsWith("registry:"));
    assertSame(key, inspector.deserializeKeyPayload("users", cache, page.getItems().get(0).getEntryToken()));
  }

  @Test
  void inspect_filtersByKeywordBeforeRenderingValuePayload() {
    var manager = new ConcurrentMapCacheManager();
    var cache = manager.getCache("users");
    cache.put("good-key", "alice");
    cache.put("bad-key", new BrokenValue());

    var inspector = new ConcurrentMapCacheEntryInspector();

    var page = inspector.inspect("users", cache, new CacheEntryQuery("good-key", 1, 20));

    assertEquals(Long.valueOf(1), page.getTotal());
    assertEquals("good-key", page.getItems().get(0).getKeyDisplay());
    assertEquals("\"alice\"", page.getItems().get(0).getValueJson());
  }

  @Test
  void inspect_clearsRegistryEntriesFromPreviousInspectionOfSameCache() {
    var manager = new ConcurrentMapCacheManager();
    var cache = manager.getCache("users");
    var firstKey = new BrokenSerializableKey("u:1");
    var secondKey = new BrokenSerializableKey("u:2");
    cache.put(firstKey, "alice");
    cache.put(secondKey, "bob");

    var inspector = new ConcurrentMapCacheEntryInspector();
    var firstPage = inspector.inspect("users", cache, new CacheEntryQuery("u:1", 1, 20));
    var secondPage = inspector.inspect("users", cache, new CacheEntryQuery("u:2", 1, 20));

    assertThrows(IllegalArgumentException.class,
        () -> inspector.deserializeKeyPayload("users", cache, firstPage.getItems().get(0).getEntryToken()));
    assertSame(secondKey,
        inspector.deserializeKeyPayload("users", cache, secondPage.getItems().get(0).getEntryToken()));
  }

  static class BrokenSerializableKey implements java.io.Serializable {
    private final String value;
    private final Object nonSerializableDependency = new Object();

    BrokenSerializableKey(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  static class BrokenValue {
    @Override
    public String toString() {
      throw new IllegalStateException("toString should not be called for filtered-out values");
    }
  }
}
