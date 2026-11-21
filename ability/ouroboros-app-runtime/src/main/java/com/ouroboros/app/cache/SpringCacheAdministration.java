package com.ouroboros.app.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.ouroboros.cache.CacheAdministration;
import com.ouroboros.cache.CacheEntryDescriptor;
import com.ouroboros.cache.CacheEntryInspector;
import com.ouroboros.cache.CacheEntryPage;
import com.ouroboros.cache.CacheEntryQuery;
import com.ouroboros.cache.CacheEntryTokenCodec;
import com.ouroboros.cache.CacheRegionDescriptor;
import com.ouroboros.cache.CacheRegionFailure;
import com.ouroboros.cache.ClearAllResult;

public class SpringCacheAdministration implements CacheAdministration {
  private final CacheManager cacheManager;
  private final List<CacheEntryInspector> inspectors;
  private final CacheEntryTokenCodec tokenCodec;

  public SpringCacheAdministration(CacheManager cacheManager,
                                   List<CacheEntryInspector> inspectors,
                                   CacheEntryTokenCodec tokenCodec) {
    this.cacheManager = cacheManager;
    this.inspectors = inspectors == null ? Collections.emptyList() : inspectors;
    this.tokenCodec = tokenCodec;
  }

  @Override
  public String getCacheManagerType() {
    return cacheManager.getClass().getSimpleName();
  }

  @Override
  public List<CacheRegionDescriptor> listRegions() {
    return cacheManager.getCacheNames().stream()
        .map(this::toRegionDescriptor)
        .collect(Collectors.toList());
  }

  @Override
  public CacheEntryPage listEntries(String cacheName, CacheEntryQuery query) {
    Cache cache = requireCache(cacheName);
    return findInspector(cache)
        .map(inspector -> signEntryTokens(cacheName, inspector, inspector.inspect(cacheName, cache, query)))
        .orElseGet(() -> CacheEntryPage.unsupported("当前缓存实现不支持条目级查看"));
  }

  @Override
  public void evictEntry(String cacheName, String entryToken) {
    Cache cache = requireCache(cacheName);
    CacheEntryInspector inspector = findInspector(cache)
        .orElseThrow(() -> new IllegalArgumentException("当前缓存空间不支持条目级删除，请刷新后重试"));
    var decoded = tokenCodec.decode(cacheName, inspector.getInspectorId(), entryToken);
    Object key = inspector.deserializeKeyPayload(cacheName, cache, decoded.getKeyPayload());
    cache.evict(key);
  }

  @Override
  public void clearRegion(String cacheName) {
    requireCache(cacheName).clear();
  }

  @Override
  public ClearAllResult clearAll() {
    List<String> names = new ArrayList<>(cacheManager.getCacheNames());
    List<String> cleared = new ArrayList<>();
    List<CacheRegionFailure> failed = new ArrayList<>();

    names.forEach(name -> {
      try {
        Cache cache = requireCache(name);
        cache.clear();
        cleared.add(name);
      } catch (Exception ex) {
        failed.add(new CacheRegionFailure()
            .setName(name)
            .setError(ex.getMessage()));
      }
    });

    return new ClearAllResult()
        .setTotalRegions(names.size())
        .setClearedRegions(cleared)
        .setFailedRegions(failed);
  }

  private CacheRegionDescriptor toRegionDescriptor(String cacheName) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache == null) {
      return new CacheRegionDescriptor()
          .setName(cacheName)
          .setProviderType("Unknown")
          .setInspectable(Boolean.FALSE)
          .setEntryCount(null)
          .setEntryCountMode("UNSUPPORTED");
    }
    return findInspector(cache)
        .map(inspector -> inspector.describeRegion(cacheName, cache))
        .orElseGet(() -> new CacheRegionDescriptor()
            .setName(cacheName)
            .setProviderType(cache.getClass().getSimpleName())
            .setInspectable(Boolean.FALSE)
            .setEntryCount(null)
            .setEntryCountMode("UNSUPPORTED"));
  }

  private Cache requireCache(String cacheName) {
    return Optional.ofNullable(cacheManager.getCache(cacheName))
        .orElseThrow(() -> new IllegalArgumentException("缓存空间不存在"));
  }

  private Optional<CacheEntryInspector> findInspector(Cache cache) {
    return inspectors.stream().filter(inspector -> inspector.supports(cache)).findFirst();
  }

  private CacheEntryPage signEntryTokens(String cacheName,
                                         CacheEntryInspector inspector,
                                         CacheEntryPage page) {
    if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
      return page;
    }

    List<CacheEntryDescriptor> signedItems = page.getItems().stream()
        .map(item -> signEntryToken(cacheName, inspector, item))
        .collect(Collectors.toList());

    return new CacheEntryPage()
        .setInspectable(page.getInspectable())
        .setTotal(page.getTotal())
        .setItems(signedItems)
        .setMessage(page.getMessage());
  }

  private CacheEntryDescriptor signEntryToken(String cacheName,
                                              CacheEntryInspector inspector,
                                              CacheEntryDescriptor item) {
    String rawKeyPayload = item.getEntryToken();
    String signedToken = rawKeyPayload == null
        ? null
        : tokenCodec.encode(cacheName, inspector.getInspectorId(), rawKeyPayload);

    return new CacheEntryDescriptor()
        .setCacheName(item.getCacheName())
        .setEntryToken(signedToken)
        .setKeyDisplay(item.getKeyDisplay())
        .setKeyType(item.getKeyType())
        .setValueType(item.getValueType())
        .setValuePreview(item.getValuePreview())
        .setValueJson(item.getValueJson())
        .setTtlMillis(item.getTtlMillis())
        .setExpireAt(item.getExpireAt());
  }
}
