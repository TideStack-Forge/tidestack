package com.ouroboros.app.cache;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.SimpleKey;

import com.ouroboros.cache.CacheAdministration;
import com.ouroboros.cache.ConcurrentMapCacheEntryInspector;
import com.ouroboros.cache.CacheEntryPage;
import com.ouroboros.cache.CacheEntryQuery;
import com.ouroboros.cache.CacheRegionFailure;
import com.ouroboros.cache.ClearAllResult;
import com.ouroboros.cache.DefaultCacheEntryTokenCodec;

import static org.junit.jupiter.api.Assertions.*;

class SpringCacheAdministrationTest {

  @Test
  void cacheAdministrationConfiguration_doesNotActivateWhenCacheManagementModuleAbsent() throws Exception {
    Class<?> configurationClass = Class.forName("com.ouroboros.app.cache.PlatformCacheAdministrationConfiguration");

    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(configurationClass, TestCacheAdministrationConfiguration.class);
      context.refresh();

      assertTrue(context.getBeansOfType(CacheAdministration.class).isEmpty());
    }
  }

  @Test
  void listRegions_marksUnsupportedCacheWhenNoInspectorMatches() {
    var cacheManager = new ConcurrentMapCacheManager();
    cacheManager.getCache("users").put("u:1", "alice");
    var administration = new SpringCacheAdministration(
        cacheManager,
        Collections.emptyList(),
        new DefaultCacheEntryTokenCodec("platform-cache-secret")
    );

    var regions = administration.listRegions();

    assertEquals(1, regions.size());
    assertFalse(regions.get(0).getInspectable());
    assertEquals("UNSUPPORTED", regions.get(0).getEntryCountMode());
  }

  @Test
  void listEntries_returnsUnsupportedPageWhenNoInspectorMatches() {
    var cacheManager = new ConcurrentMapCacheManager();
    cacheManager.getCache("users").put("u:1", "alice");
    var administration = new SpringCacheAdministration(
        cacheManager,
        Collections.emptyList(),
        new DefaultCacheEntryTokenCodec("platform-cache-secret")
    );

    CacheEntryPage page = administration.listEntries("users", new CacheEntryQuery(null, 1, 20));

    assertFalse(page.getInspectable());
    assertEquals(Long.valueOf(0), page.getTotal());
    assertTrue(page.getItems().isEmpty());
  }

  @Test
  void evictEntry_rejectsInvalidToken() {
    var cacheManager = new ConcurrentMapCacheManager();
    cacheManager.getCache("users").put("u:1", "alice");
    var administration = new SpringCacheAdministration(
        cacheManager,
        Collections.emptyList(),
        new DefaultCacheEntryTokenCodec("platform-cache-secret")
    );

    assertThrows(IllegalArgumentException.class, () -> administration.evictEntry("users", "invalid"));
  }

  @Test
  void evictEntry_removesListedEntryWhenUsingInspectableCache() {
    var cacheManager = new ConcurrentMapCacheManager();
    Cache cache = cacheManager.getCache("users");
    assertNotNull(cache);
    cache.put("u:1", "alice");
    var administration = new SpringCacheAdministration(
        cacheManager,
        Collections.singletonList(new ConcurrentMapCacheEntryInspector()),
        new DefaultCacheEntryTokenCodec("platform-cache-secret")
    );

    CacheEntryPage page = administration.listEntries("users", new CacheEntryQuery(null, 1, 20));

    assertEquals(Long.valueOf(1), page.getTotal());
    assertEquals("\"alice\"", page.getItems().get(0).getValueJson());
    administration.evictEntry("users", page.getItems().get(0).getEntryToken());
    assertNull(cache.get("u:1"));
  }

  @Test
  void evictEntry_removesListedEntryWhenCacheUsesSimpleKey() {
    var cacheManager = new ConcurrentMapCacheManager();
    Cache cache = cacheManager.getCache("users");
    assertNotNull(cache);
    cache.put(SimpleKey.EMPTY, "alice");
    var administration = new SpringCacheAdministration(
        cacheManager,
        Collections.singletonList(new ConcurrentMapCacheEntryInspector()),
        new DefaultCacheEntryTokenCodec("platform-cache-secret")
    );

    CacheEntryPage page = administration.listEntries("users", new CacheEntryQuery(null, 1, 20));

    assertEquals(Long.valueOf(1), page.getTotal());
    administration.evictEntry("users", page.getItems().get(0).getEntryToken());
    assertNull(cache.get(SimpleKey.EMPTY));
  }

  @Test
  void evictEntry_removesListedEntryWhenSerializableKeyFallsBackToRegistry() {
    var cacheManager = new ConcurrentMapCacheManager();
    Cache cache = cacheManager.getCache("users");
    assertNotNull(cache);
    var key = new BrokenSerializableKey("u:1");
    cache.put(key, "alice");
    var administration = new SpringCacheAdministration(
        cacheManager,
        Collections.singletonList(new ConcurrentMapCacheEntryInspector()),
        new DefaultCacheEntryTokenCodec("platform-cache-secret")
    );

    CacheEntryPage page = administration.listEntries("users", new CacheEntryQuery(null, 1, 20));

    assertEquals(Long.valueOf(1), page.getTotal());
    administration.evictEntry("users", page.getItems().get(0).getEntryToken());
    assertNull(cache.get(key));
  }

  @Test
  void clearAll_returnsPartialFailureResultWhenOneRegionThrows() {
    var administration = new SpringCacheAdministration(
        new TestCacheManager(),
        Collections.emptyList(),
        new DefaultCacheEntryTokenCodec("platform-cache-secret")
    );

    ClearAllResult result = administration.clearAll();

    assertEquals(2, result.getTotalRegions());
    assertEquals(Collections.singletonList("users"), result.getClearedRegions());
    assertEquals(1, result.getFailedRegions().size());
    CacheRegionFailure failure = result.getFailedRegions().get(0);
    assertEquals("sessions", failure.getName());
    assertTrue(failure.getError().contains("Cache clear failed"));
  }

  @Test
  void getCacheManagerType_returnsCacheManagerSimpleName() {
    var administration = new SpringCacheAdministration(
        new ConcurrentMapCacheManager(),
        Collections.emptyList(),
        new DefaultCacheEntryTokenCodec("platform-cache-secret")
    );

    assertEquals("ConcurrentMapCacheManager", administration.getCacheManagerType());
  }

  static class TestCacheManager implements org.springframework.cache.CacheManager {
    @Override
    public Cache getCache(String name) {
      if ("users".equals(name)) {
        return new ConcurrentMapCache("users");
      }
      if ("sessions".equals(name)) {
        return new FailingCache("sessions");
      }
      return null;
    }

    @Override
    public java.util.Collection<String> getCacheNames() {
      return new LinkedHashSet<>(Arrays.asList("users", "sessions"));
    }
  }

  static class FailingCache extends ConcurrentMapCache {
    FailingCache(String name) {
      super(name);
    }

    @Override
    public void clear() {
      throw new IllegalStateException("Cache clear failed");
    }
  }

  @Configuration
  static class TestCacheAdministrationConfiguration {
    @Bean
    CacheManager cacheManager() {
      var cacheManager = new ConcurrentMapCacheManager();
      cacheManager.getCache("users");
      return cacheManager;
    }
  }

  static class BrokenSerializableKey implements Serializable {
    private final String value;
    private final Object nonSerializableDependency = new Object();

    BrokenSerializableKey(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof BrokenSerializableKey)) {
        return false;
      }
      BrokenSerializableKey other = (BrokenSerializableKey) obj;
      return java.util.Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(value);
    }

    @Override
    public String toString() {
      return value;
    }
  }
}
