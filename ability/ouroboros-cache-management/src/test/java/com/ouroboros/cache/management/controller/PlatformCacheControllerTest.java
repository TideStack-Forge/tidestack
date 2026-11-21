package com.ouroboros.cache.management.controller;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.cache.CacheEntryPage;
import com.ouroboros.cache.CacheRegionDescriptor;
import com.ouroboros.cache.management.service.PlatformCacheService;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCacheControllerTest {

  @Test
  void deleteEntry_returnsErrorWhenEntryTokenMissing() {
    var controller = new PlatformCacheController(new PlatformCacheServiceStub());

    var result = controller.deleteEntry("users", null);

    assertEquals(-1, result.getCode());
    assertEquals("缓存项标识不能为空", result.getMsg());
  }

  @Test
  void listRegions_returnsItemsWebApiResult() {
    var controller = new PlatformCacheController(new PlatformCacheServiceStub());

    var result = controller.listRegions();

    assertEquals(0, result.getCode());
    assertEquals(1, result.getData().getItems().size());
  }

  @Test
  void clearRegion_returnsErrorWhenCacheNameMissing() {
    var controller = new PlatformCacheController(new PlatformCacheServiceStub());

    var result = controller.clearRegion(null);

    assertEquals(-1, result.getCode());
    assertEquals("缓存空间名称不能为空", result.getMsg());
  }

  @Test
  void summary_returnsCacheManagerType() {
    var controller = new PlatformCacheController(new PlatformCacheServiceStub());

    var result = controller.summary();

    assertEquals(0, result.getCode());
    assertEquals("ConcurrentMapCacheManager", result.getData().get("cacheManagerType"));
  }

  @Test
  void platformCacheResources_existAndExposeExpectedRoutes() {
    assertNotNull(loadResource("META-INF/spring.factories"));
    String authority = loadResource("META-INF/ouroboros/metadata/platformCache.authority.json");
    String appModules = loadResource("META-INF/ouroboros/metadata/app-modules.json");
    assertNotNull(authority);
    assertNotNull(appModules);
    String menuModel = loadResource("META-INF/ouroboros/metadata/systemSetting/platformCache.menu.json");
    String uiModel = loadResource("META-INF/ouroboros/metadata/system-setting/platform-cache.ui-model.json");
    String legacyMenuModel = loadResource("META-INF/ouroboros/metadata/platformCache.menu.json");
    String legacyUiModel = loadResource("META-INF/ouroboros/metadata/platformCache.ui-model.json");
    assertNotNull(menuModel);
    assertNotNull(uiModel);
    assertNull(legacyMenuModel);
    assertNull(legacyUiModel);

    String schema = loadResource("META-INF/ouroboros/metadata/system-setting/platform-cache.ui-schema.json");
    String legacySchema = loadResource("META-INF/ouroboros/metadata/platform-cache.ui-schema.json");
    assertNotNull(schema);
    assertNull(legacySchema);
    assertTrue(appModules.contains("\"fullName\": \"systemSetting.platformCache\""));
    assertTrue(appModules.contains("\"menu\": \"systemSetting.platformCache\""));
    assertTrue(appModules.contains("\"systemSetting.platformCache\""));
    assertTrue(authority.contains("\"fullName\": \"systemSetting.platformCache\""));
    assertTrue(authority.contains("\"uiViews\": ["));
    assertTrue(menuModel.contains("\"fullName\": \"systemSetting.platformCache\""));
    assertTrue(menuModel.contains("\"url\": \"/system-setting/platform-cache\""));
    assertTrue(uiModel.contains("\"path\": \"/system-setting/platform-cache\""));
    assertTrue(authority.contains("GET /api/platform-cache/summary"));
    assertTrue(schema.contains("reload"));
    assertTrue(schema.contains("清空全部缓存"));
    assertTrue(schema.contains("/api/platform-cache/summary"));
    assertTrue(schema.contains("/api/platform-cache/regions"));
    assertTrue(schema.contains("/api/platform-cache/entries"));
    assertTrue(schema.contains("查看条目"));
    assertTrue(schema.contains("当前平台缓存管理器"));
    assertTrue(schema.contains("\"initApi\""));
    assertTrue(schema.contains("\"childrenField\": \"children\""));
    assertTrue(schema.contains("\"name\": \"displayName\""));
    assertTrue(schema.contains("hasCache"));
    assertTrue(schema.contains("\"hiddenOn\": \"${!hasCache}\""));
    assertTrue(schema.contains("\"sendOn\": \"${cacheName}\""));
    assertTrue(schema.contains("\"componentName\": \"cacheEntryList\""));
    assertTrue(schema.contains("\"dataMergeMode\": \"merge\""));
    assertTrue(schema.contains("\"componentName\": \"cacheRegionList\""));
    assertTrue(schema.contains("\"cacheName\": \"${__rendererData.cacheName || __rendererData.name}\""));
    assertTrue(schema.contains("\"cacheTitle\": \"${__rendererData.cacheTitle || __rendererData.name}\""));
    assertTrue(schema.contains("\"cacheName\": \"\""));
    assertTrue(schema.contains("\"cacheTitle\": \"\""));
    assertTrue(schema.contains("\"label\": \"查看\""));
    assertTrue(schema.contains("\"actionType\": \"dialog\""));
    assertTrue(schema.contains("缓存值"));
    assertTrue(schema.contains("\"name\": \"valueJson\""));
    assertTrue(countOccurrences(schema, "\"componentName\": \"cacheEntryList\"") >= 3);
    assertFalse(schema.contains("\"target\": \"cacheEntryList?cacheName="));
    assertFalse(schema.contains("\"name\": \"providerType\""));
    assertFalse(schema.contains("\"label\": \"实现\""));
  }

  static class PlatformCacheServiceStub implements PlatformCacheService {
    @Override
    public com.ouroboros.web.ItemsWebApiResult<CacheRegionDescriptor> listRegions() {
      return com.ouroboros.web.ItemsWebApiResult.ok(Collections.singletonList(
          new CacheRegionDescriptor()
              .setName("users")
              .setInspectable(Boolean.TRUE)
              .setProviderType("ConcurrentMapCache")
              .setEntryCount(1L)
              .setEntryCountMode("EXACT")
      ));
    }

    @Override
    public com.ouroboros.web.WebApiResult<CacheEntryPage> listEntries(String cacheName, Integer page, Integer perPage, String keyword) {
      return com.ouroboros.web.WebApiResult.ok(CacheEntryPage.unsupported("当前缓存实现不支持条目级查看"));
    }

    @Override
    public com.ouroboros.web.WebApiResult<String> deleteEntry(String cacheName, String entryToken) {
      return com.ouroboros.web.WebApiResult.ok("缓存项删除成功");
    }

    @Override
    public com.ouroboros.web.WebApiResult<String> clearRegion(String cacheName) {
      return com.ouroboros.web.WebApiResult.ok("缓存空间清理成功");
    }

    @Override
    public com.ouroboros.web.WebApiResult<com.ouroboros.cache.ClearAllResult> clearAll() {
      return com.ouroboros.web.WebApiResult.ok(new com.ouroboros.cache.ClearAllResult());
    }

    public com.ouroboros.web.WebApiResult<Map<String, String>> getSummary() {
      return com.ouroboros.web.WebApiResult.ok(Collections.singletonMap("cacheManagerType", "ConcurrentMapCacheManager"));
    }
  }

  private String loadResource(String path) {
    try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        return null;
      }
      // Java 8 compatible: use ByteArrayOutputStream instead of readAllBytes()
      java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
      int nRead;
      byte[] data = new byte[1024];
      while ((nRead = stream.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, nRead);
      }
      return new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Failed to load resource: " + path, ex);
    }
  }

  private int countOccurrences(String text, String pattern) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(pattern, index)) >= 0) {
      count++;
      index += pattern.length();
    }
    return count;
  }
}
