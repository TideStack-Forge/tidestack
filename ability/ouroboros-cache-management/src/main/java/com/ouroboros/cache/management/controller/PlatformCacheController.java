package com.ouroboros.cache.management.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import com.ouroboros.cache.CacheEntryPage;
import com.ouroboros.cache.CacheRegionDescriptor;
import com.ouroboros.cache.ClearAllResult;
import com.ouroboros.cache.management.service.PlatformCacheService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/platform-cache")
public class PlatformCacheController {
  private final PlatformCacheService platformCacheService;

  public PlatformCacheController(PlatformCacheService platformCacheService) {
    this.platformCacheService = platformCacheService;
  }

  @GetMapping("/summary")
  public WebApiResult<Map<String, String>> summary() {
    return platformCacheService.getSummary();
  }

  @GetMapping("/regions")
  public ItemsWebApiResult<CacheRegionDescriptor> listRegions() {
    return platformCacheService.listRegions();
  }

  @GetMapping("/entries")
  public WebApiResult<CacheEntryPage> listEntries(@RequestParam String cacheName,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer perPage,
                                                  @RequestParam(required = false) String keyword) {
    if (!StringUtils.hasText(cacheName)) {
      return WebApiResult.fail("缓存空间名称不能为空");
    }
    if (page == null || page < 1) {
      return WebApiResult.fail("页码必须大于等于1");
    }
    if (perPage == null || perPage < 1) {
      return WebApiResult.fail("每页条数必须大于等于1");
    }
    return platformCacheService.listEntries(cacheName, page, perPage, keyword);
  }

  @DeleteMapping("/entries")
  public WebApiResult<String> deleteEntry(@RequestParam String cacheName,
                                          @RequestParam(required = false) String entryToken) {
    if (!StringUtils.hasText(cacheName)) {
      return WebApiResult.fail("缓存空间名称不能为空");
    }
    if (!StringUtils.hasText(entryToken)) {
      return WebApiResult.fail("缓存项标识不能为空");
    }
    return platformCacheService.deleteEntry(cacheName, entryToken);
  }

  @DeleteMapping("/regions")
  public WebApiResult<String> clearRegion(@RequestParam(required = false) String cacheName) {
    if (!StringUtils.hasText(cacheName)) {
      return WebApiResult.fail("缓存空间名称不能为空");
    }
    return platformCacheService.clearRegion(cacheName);
  }

  @DeleteMapping
  public WebApiResult<ClearAllResult> clearAll() {
    return platformCacheService.clearAll();
  }
}
