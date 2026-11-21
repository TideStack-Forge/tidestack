package com.ouroboros.businesslog.util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LazyMap implements Map<String, Object> {
  Map<String, Supplier<Object>> lazyMap;
  Map<String, Object> cachedKVs;
  Map<String, Object> wrappedMap;

  // 一组Function, 用于查找不确定的KV
  List<Function<String, Object>> dynamicKVResolvers;

  public LazyMap() {
    this(Collections.emptyMap());
  }

  /**
   * 构造函数
   *
   * @param wrappedMap 获取值时先从这里查找，找不到才会从lazyKV中查找，最后从dynamicKVResolvers中查找
   */
  public LazyMap(Map<String, Object> wrappedMap) {
    this(wrappedMap, Collections.emptyMap());
  }

  /**
   * 构造函数
   *
   * @param wrappedMap 获取值时先从这里查找
   * @param lazyMap    wrapperMap找不到的KV从这里查找
   */
  public LazyMap(Map<String, Object> wrappedMap, Map<String, Supplier<Object>> lazyMap) {
    this.wrappedMap = wrappedMap;
    this.cachedKVs = new HashMap<>();
    this.lazyMap = new HashMap<>(lazyMap);
    this.dynamicKVResolvers = new ArrayList<>();
  }

  /**
   * 构造函数
   *
   * @param wrappedMap         获取值时先从这里查找
   * @param lazyMap            wrapperMap找不到的KV从这里查找
   * @param dynamicKVResolvers 最后从动态KVResolvers中查找
   */
  public LazyMap(Map<String, Object> wrappedMap, Map<String, Supplier<Object>> lazyMap, List<Function<String, Object>> dynamicKVResolvers) {
    this.wrappedMap = wrappedMap;
    this.cachedKVs = new HashMap<>();
    this.lazyMap = new HashMap<>(lazyMap);
    this.dynamicKVResolvers = new ArrayList<>(dynamicKVResolvers);
  }

  public LazyMap addLazyKV(String key, Supplier<Object> valueSupplier) {
    lazyMap.put(key, valueSupplier);
    return this;
  }

  public LazyMap addDynamicKVResolver(Function<String, Object> resolver) {
    dynamicKVResolvers.add(resolver);
    return this;
  }

  @Override
  public int size() {
    // 这里其实没什么意义，因为还有 dynamicKVResolvers 动态KV
    return wrappedMap.size() + lazyMap.size();
  }

  @Override
  public boolean isEmpty() {
    // 武断的返回 false，因为如果真的是空的，也没必要用 LazyMap
    return false;
  }

  @Override
  public boolean containsKey(Object key) {
    return wrappedMap.containsKey(key) || lazyMap.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return false;
  }

  @Override
  public Object get(Object key) {
    // 先从缓存中获取
    Object v1 = cachedKVs.get(key);
    if (v1 != null) {
      return v1;
    }

    // 再从被包裹的Map中获取
    Object v2 = wrappedMap.get(key);
    if (v2 != null) {
      cachedKVs.put((String) key, v2);
      return v2;
    }

    // 再从lazyKV中获取
    Supplier<Object> f = lazyMap.get(key);
    if (f != null) {
      Object v3 = f.get();
      cachedKVs.put((String) key, v3);
      return v3;
    }

    // 再从动态KV中获取
    for (Function<String, Object> resolver : dynamicKVResolvers) {
      Object v4 = resolver.apply((String) key);
      if (v4 != null) {
        cachedKVs.put((String) key, v4);
        return v4;
      }
    }

    // 没有找到KV
    return null;
  }

  @Override
  public Object put(String key, Object value) {
    return null;
  }

  @Override
  public Object remove(Object key) {
    return null;
  }

  @Override
  public void putAll(Map<? extends String, ?> m) {

  }

  @Override
  public void clear() {

  }

  @Override
  public Set<String> keySet() {
    return Stream.of(wrappedMap.keySet().stream(), lazyMap.keySet().stream())
        .flatMap(s -> s)
        .collect(Collectors.toSet());
  }

  @Override
  public Collection<Object> values() {
    return Stream.of(wrappedMap.values().stream(), lazyMap.values().stream())
        .flatMap(s -> s)
        .collect(Collectors.toList());
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    // 当调用这个的时候，所有的lazyKV都会被执行
    return Stream.of(wrappedMap.entrySet().stream(),
            lazyMap.entrySet().stream().map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().get())))
        .flatMap(s -> s)
        .collect(Collectors.toSet());
  }
}
