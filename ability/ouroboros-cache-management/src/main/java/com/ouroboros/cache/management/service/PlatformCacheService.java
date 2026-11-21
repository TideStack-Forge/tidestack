package com.ouroboros.cache.management.service;

import java.util.Map;

import com.ouroboros.cache.CacheEntryPage;
import com.ouroboros.cache.CacheRegionDescriptor;
import com.ouroboros.cache.ClearAllResult;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

public interface PlatformCacheService {
  WebApiResult<Map<String, String>> getSummary();

  ItemsWebApiResult<CacheRegionDescriptor> listRegions();

  WebApiResult<CacheEntryPage> listEntries(String cacheName, Integer page, Integer perPage, String keyword);

  WebApiResult<String> deleteEntry(String cacheName, String entryToken);

  WebApiResult<String> clearRegion(String cacheName);

  WebApiResult<ClearAllResult> clearAll();
}
