package com.ouroboros.cache;

import java.io.Serializable;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.util.SerializationUtils;

import com.ouroboros.util.Json;

public class ConcurrentMapCacheEntryInspector implements CacheEntryInspector {
  private static final String SERIALIZED_PREFIX = "serialized:";
  private static final String REGISTRY_PREFIX = "registry:";
  private final ConcurrentMap<String, Object> keyPayloadRegistry = new ConcurrentHashMap<>();

  @Override
  public boolean supports(Cache cache) {
    return cache instanceof ConcurrentMapCache;
  }

  @Override
  public CacheRegionDescriptor describeRegion(String cacheName, Cache cache) {
    var nativeCache = getNativeCache(cache);
    return new CacheRegionDescriptor()
        .setName(cacheName)
        .setProviderType(cache.getClass().getSimpleName())
        .setInspectable(Boolean.TRUE)
        .setEntryCount(Long.valueOf(nativeCache.size()))
        .setEntryCountMode("EXACT");
  }

  @Override
  public CacheEntryPage inspect(String cacheName, Cache cache, CacheEntryQuery query) {
    var nativeCache = getNativeCache(cache);
    var keyword = query == null || query.getKeyword() == null ? "" : query.getKeyword().toLowerCase(Locale.ROOT);
    int page = query == null || query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    int perPage = query == null || query.getPerPage() == null || query.getPerPage() < 1 ? 20 : Math.min(query.getPerPage(), 100);

    clearRegistry(cacheName);

    List<Map.Entry<Object, Object>> filteredEntries = nativeCache.entrySet().stream()
        .filter(entry -> keyword.isEmpty() || buildKeyDisplay(entry.getKey()).toLowerCase(Locale.ROOT).contains(keyword))
        .collect(Collectors.toList());

    int fromIndex = Math.min((page - 1) * perPage, filteredEntries.size());
    int toIndex = Math.min(fromIndex + perPage, filteredEntries.size());
    List<CacheEntryDescriptor> pageItems = fromIndex >= toIndex ? Collections.emptyList() : filteredEntries.subList(fromIndex, toIndex)
        .stream()
        .map(entry -> buildDescriptor(cacheName, entry))
        .collect(Collectors.toList());

    return new CacheEntryPage(Boolean.TRUE, Long.valueOf(filteredEntries.size()), pageItems, null);
  }

  @Override
  public String getInspectorId() {
    return "concurrent-map";
  }

  @Override
  public String serializeKeyPayload(String cacheName, Cache cache, Object rawKey) {
    if (rawKey == null) {
      return null;
    }
    if (rawKey instanceof Serializable) {
      String serializedPayload = trySerializeKeyPayload(rawKey);
      if (serializedPayload != null) {
        return serializedPayload;
      }
    }
    String payload = UUID.randomUUID().toString();
    String registryPayload = REGISTRY_PREFIX + payload;
    keyPayloadRegistry.put(buildRegistryKey(cacheName, registryPayload), rawKey);
    return registryPayload;
  }

  @Override
  public Object deserializeKeyPayload(String cacheName, Cache cache, String keyPayload) {
    if (keyPayload != null && keyPayload.startsWith(SERIALIZED_PREFIX)) {
      return SerializationUtils.deserialize(
          Base64.getUrlDecoder().decode(keyPayload.substring(SERIALIZED_PREFIX.length()))
      );
    }
    Object key = keyPayloadRegistry.remove(buildRegistryKey(cacheName, keyPayload));
    if (key == null) {
      throw new IllegalArgumentException("缓存项标识已失效，请刷新后重试");
    }
    return key;
  }

  @SuppressWarnings("unchecked")
  private ConcurrentMap<Object, Object> getNativeCache(Cache cache) {
    return (ConcurrentMap<Object, Object>) ((ConcurrentMapCache) cache).getNativeCache();
  }

  private CacheEntryDescriptor buildDescriptor(String cacheName, Map.Entry<Object, Object> entry) {
    Object key = entry.getKey();
    Object value = entry.getValue();
    return new CacheEntryDescriptor()
        .setCacheName(cacheName)
        .setEntryToken(serializeKeyPayload(cacheName, null, key))
        .setKeyDisplay(buildKeyDisplay(key))
        .setKeyType(key == null ? "Object" : key.getClass().getSimpleName())
        .setValueType(value == null ? "Object" : value.getClass().getSimpleName())
        .setValuePreview(buildValuePreview(value))
        .setValueJson(buildValueJson(value));
  }

  private String buildKeyDisplay(Object key) {
    return key == null ? "null" : key.toString();
  }

  private void clearRegistry(String cacheName) {
    String cachePrefix = cacheName + "|";
    keyPayloadRegistry.keySet().removeIf(registryKey -> registryKey.startsWith(cachePrefix));
  }

  private String buildRegistryKey(String cacheName, String keyPayload) {
    return cacheName + "|" + keyPayload;
  }

  private String trySerializeKeyPayload(Object rawKey) {
    try {
      byte[] serialized = SerializationUtils.serialize(rawKey);
      if (serialized == null) {
        return null;
      }
      return SERIALIZED_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(serialized);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private String buildValuePreview(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof CharSequence) {
      return value.getClass().getSimpleName() + "(length=" + ((CharSequence) value).length() + ")";
    }
    if (value instanceof byte[]) {
      return "byte[](length=" + ((byte[]) value).length + ")";
    }
    if (value instanceof Iterable) {
      int size = 0;
      for (Object ignored : (Iterable<?>) value) {
        size++;
      }
      return value.getClass().getSimpleName() + "(size=" + size + ")";
    }
    if (value instanceof Map) {
      return value.getClass().getSimpleName() + "(size=" + ((Map<?, ?>) value).size() + ")";
    }
    if (value.getClass().isArray()) {
      return value.getClass().getComponentType().getSimpleName() + "[](length=" + java.lang.reflect.Array.getLength(value) + ")";
    }
    if (value instanceof Number || value instanceof Boolean || value.getClass().isEnum()) {
      return value.toString();
    }
    return value.getClass().getSimpleName();
  }

  private String buildValueJson(Object value) {
    if (value == null) {
      return "null";
    }

    String json = Json.tryToPrettyJsonString(value).getOrElse("");
    if (json != null && !json.isEmpty()) {
      return json;
    }
    return Json.toJsonString(String.valueOf(value));
  }
}
