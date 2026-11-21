package com.ouroboros.cache.management.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ouroboros.cache.CacheAdministration;
import com.ouroboros.cache.CacheEntryPage;
import com.ouroboros.cache.CacheEntryQuery;
import com.ouroboros.cache.CacheRegionDescriptor;
import com.ouroboros.cache.ClearAllResult;
import com.ouroboros.cache.management.service.PlatformCacheService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@Service
public class PlatformCacheServiceImpl implements PlatformCacheService {
  private final CacheAdministration cacheAdministration;

  public PlatformCacheServiceImpl(CacheAdministration cacheAdministration) {
    this.cacheAdministration = cacheAdministration;
  }

  @Override
  public WebApiResult<Map<String, String>> getSummary() {
    try {
      return WebApiResult.ok(Collections.singletonMap("cacheManagerType", cacheAdministration.getCacheManagerType()));
    } catch (Exception ex) {
      return WebApiResult.error(ex.getMessage());
    }
  }

  @Override
  public ItemsWebApiResult<CacheRegionDescriptor> listRegions() {
    try {
      return ItemsWebApiResult.ok(buildRegionTree(cacheAdministration.listRegions()));
    } catch (Exception ex) {
      return ItemsWebApiResult.error(ex.getMessage());
    }
  }

  @Override
  public WebApiResult<CacheEntryPage> listEntries(String cacheName, Integer page, Integer perPage, String keyword) {
    try {
      CacheEntryPage entryPage = cacheAdministration.listEntries(cacheName, new CacheEntryQuery(keyword, page, perPage));
      return WebApiResult.ok(entryPage);
    } catch (Exception ex) {
      return WebApiResult.error(ex.getMessage());
    }
  }

  @Override
  public WebApiResult<String> deleteEntry(String cacheName, String entryToken) {
    try {
      cacheAdministration.evictEntry(cacheName, entryToken);
      return WebApiResult.ok("缓存项删除成功");
    } catch (Exception ex) {
      return WebApiResult.error(ex.getMessage());
    }
  }

  @Override
  public WebApiResult<String> clearRegion(String cacheName) {
    try {
      cacheAdministration.clearRegion(cacheName);
      return WebApiResult.ok("缓存空间清理成功");
    } catch (Exception ex) {
      return WebApiResult.error(ex.getMessage());
    }
  }

  @Override
  public WebApiResult<ClearAllResult> clearAll() {
    try {
      ClearAllResult result = cacheAdministration.clearAll();
      if (result.getFailedRegions() != null && !result.getFailedRegions().isEmpty()) {
        return WebApiResult.build(-1, "部分缓存空间清理失败", result);
      }
      return WebApiResult.ok(result);
    } catch (Exception ex) {
      return WebApiResult.error(ex.getMessage());
    }
  }

  private List<CacheRegionDescriptor> buildRegionTree(List<CacheRegionDescriptor> regions) {
    if (regions == null || regions.isEmpty()) {
      return Collections.emptyList();
    }

    List<CacheRegionDescriptor> roots = new ArrayList<>();
    Map<String, CacheRegionDescriptor> nodes = new LinkedHashMap<>();
    regions.forEach(region -> appendRegion(roots, nodes, region));
    return roots;
  }

  private void appendRegion(List<CacheRegionDescriptor> roots,
                            Map<String, CacheRegionDescriptor> nodes,
                            CacheRegionDescriptor region) {
    if (region == null || !StringUtils.hasText(region.getName())) {
      return;
    }

    String[] segments = region.getName().split(":");
    StringBuilder currentPath = new StringBuilder();
    List<CacheRegionDescriptor> currentLevel = roots;

    for (int index = 0; index < segments.length; index++) {
      String segment = segments[index];
      if (currentPath.length() > 0) {
        currentPath.append(':');
      }
      currentPath.append(segment);

      String fullPath = currentPath.toString();
      CacheRegionDescriptor node = nodes.get(fullPath);
      if (node == null) {
        node = createVirtualNode(fullPath, segment);
        nodes.put(fullPath, node);
        currentLevel.add(node);
      }

      if (index == segments.length - 1) {
        mergeRegion(node, region, segment);
      }

      currentLevel = node.getChildren();
    }
  }

  private CacheRegionDescriptor createVirtualNode(String name, String displayName) {
    return new CacheRegionDescriptor()
        .setName(name)
        .setDisplayName(displayName)
        .setHasCache(Boolean.FALSE)
        .setInspectable(Boolean.FALSE)
        .setChildren(new ArrayList<>());
  }

  private void mergeRegion(CacheRegionDescriptor target, CacheRegionDescriptor source, String displayName) {
    target
        .setName(source.getName())
        .setDisplayName(displayName)
        .setProviderType(source.getProviderType())
        .setInspectable(source.getInspectable())
        .setHasCache(Boolean.TRUE)
        .setEntryCount(source.getEntryCount())
        .setEntryCountMode(source.getEntryCountMode());
  }
}
