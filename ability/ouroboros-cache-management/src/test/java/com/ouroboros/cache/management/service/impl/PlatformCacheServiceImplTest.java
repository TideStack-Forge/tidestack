package com.ouroboros.cache.management.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.cache.CacheAdministration;
import com.ouroboros.cache.CacheEntryPage;
import com.ouroboros.cache.CacheEntryQuery;
import com.ouroboros.cache.ClearAllResult;
import com.ouroboros.cache.CacheRegionFailure;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCacheServiceImplTest {

  @Test
  void listEntries_returnsSuccessWithInspectableFalsePage() {
    var service = new PlatformCacheServiceImpl(new UnsupportedCacheAdministrationStub());

    var result = service.listEntries("sessions", 1, 20, null);

    assertEquals(0, result.getCode());
    assertFalse(result.getData().getInspectable());
    assertEquals("当前缓存实现不支持条目级查看", result.getData().getMessage());
  }

  @Test
  void deleteEntry_returnsErrorWhenAdministrationRejectsToken() {
    var service = new PlatformCacheServiceImpl(new UnsupportedCacheAdministrationStub());

    var result = service.deleteEntry("users", "invalid-token");

    assertEquals(-1, result.getCode());
    assertEquals("无效的缓存项标识", result.getMsg());
  }

  @Test
  void clearAll_returnsErrorWhenAnyRegionFails() {
    var service = new PlatformCacheServiceImpl(new UnsupportedCacheAdministrationStub());

    var result = service.clearAll();

    assertEquals(-1, result.getCode());
    assertEquals(1, result.getData().getFailedRegions().size());
    assertEquals("sessions", result.getData().getFailedRegions().get(0).getName());
  }

  @Test
  void listRegions_buildsTreeFromColonSeparatedCacheNames() {
    var service = new PlatformCacheServiceImpl(new TreeCacheAdministrationStub());

    var result = service.listRegions();
    var roots = new ArrayList<>(result.getData().getItems());

    assertEquals(0, result.getCode());
    assertEquals(2, roots.size());

    var ouroboros = roots.get(0);
    assertEquals("ouroboros", ouroboros.getName());
    assertEquals("ouroboros", ouroboros.getDisplayName());
    assertFalse(ouroboros.getHasCache());
    assertEquals(2, ouroboros.getChildren().size());

    var user = ouroboros.getChildren().get(0);
    assertEquals("ouroboros:user", user.getName());
    assertEquals("user", user.getDisplayName());
    assertFalse(user.getHasCache());
    assertEquals(1, user.getChildren().size());

    var role = user.getChildren().get(0);
    assertEquals("ouroboros:user:role", role.getName());
    assertEquals("role", role.getDisplayName());
    assertTrue(role.getHasCache());
    assertTrue(role.getInspectable());
    assertEquals(Long.valueOf(2), role.getEntryCount());

    var users = ouroboros.getChildren().get(1);
    assertEquals("ouroboros:users", users.getName());
    assertEquals("users", users.getDisplayName());
    assertTrue(users.getHasCache());
    assertFalse(users.getInspectable());
    assertTrue(users.getChildren().isEmpty());

    var languages = roots.get(1);
    assertEquals("i18n-languages", languages.getName());
    assertEquals("i18n-languages", languages.getDisplayName());
    assertTrue(languages.getHasCache());
    assertTrue(languages.getChildren().isEmpty());
  }

  @Test
  void getSummary_returnsCacheManagerType() {
    var service = new PlatformCacheServiceImpl(new TreeCacheAdministrationStub());

    var result = service.getSummary();

    assertEquals(0, result.getCode());
    assertEquals("ConcurrentMapCacheManager", result.getData().get("cacheManagerType"));
  }

  static class UnsupportedCacheAdministrationStub implements CacheAdministration {
    @Override
    public java.util.List<com.ouroboros.cache.CacheRegionDescriptor> listRegions() {
      return Collections.emptyList();
    }

    @Override
    public CacheEntryPage listEntries(String cacheName, CacheEntryQuery query) {
      return CacheEntryPage.unsupported("当前缓存实现不支持条目级查看");
    }

    @Override
    public void evictEntry(String cacheName, String entryToken) {
      throw new IllegalArgumentException("无效的缓存项标识");
    }

    @Override
    public void clearRegion(String cacheName) {
    }

    @Override
    public ClearAllResult clearAll() {
      return new ClearAllResult()
          .setTotalRegions(2)
          .setClearedRegions(Collections.singletonList("users"))
          .setFailedRegions(Collections.singletonList(
              new CacheRegionFailure()
                  .setName("sessions")
                  .setError("cache clear failed")
          ));
    }

    public String getCacheManagerType() {
      return "UnsupportedCacheManager";
    }
  }

  static class TreeCacheAdministrationStub implements CacheAdministration {
    @Override
    public java.util.List<com.ouroboros.cache.CacheRegionDescriptor> listRegions() {
      return Arrays.asList(
          new com.ouroboros.cache.CacheRegionDescriptor()
              .setName("ouroboros:user:role")
              .setInspectable(Boolean.TRUE)
              .setProviderType("ConcurrentMapCache")
              .setEntryCount(2L)
              .setEntryCountMode("EXACT"),
          new com.ouroboros.cache.CacheRegionDescriptor()
              .setName("ouroboros:users")
              .setInspectable(Boolean.FALSE)
              .setProviderType("ConcurrentMapCache")
              .setEntryCount(null)
              .setEntryCountMode("UNSUPPORTED"),
          new com.ouroboros.cache.CacheRegionDescriptor()
              .setName("i18n-languages")
              .setInspectable(Boolean.TRUE)
              .setProviderType("ConcurrentMapCache")
              .setEntryCount(1L)
              .setEntryCountMode("EXACT")
      );
    }

    @Override
    public CacheEntryPage listEntries(String cacheName, CacheEntryQuery query) {
      return CacheEntryPage.unsupported("当前缓存实现不支持条目级查看");
    }

    @Override
    public void evictEntry(String cacheName, String entryToken) {
      throw new IllegalArgumentException("无效的缓存项标识");
    }

    @Override
    public void clearRegion(String cacheName) {
    }

    @Override
    public ClearAllResult clearAll() {
      return new ClearAllResult();
    }

    public String getCacheManagerType() {
      return "ConcurrentMapCacheManager";
    }
  }
}
