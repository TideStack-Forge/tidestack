package com.ouroboros.cache;

import java.util.List;

public interface CacheAdministration {
  String getCacheManagerType();

  List<CacheRegionDescriptor> listRegions();

  CacheEntryPage listEntries(String cacheName, CacheEntryQuery query);

  void evictEntry(String cacheName, String entryToken);

  void clearRegion(String cacheName);

  ClearAllResult clearAll();
}
