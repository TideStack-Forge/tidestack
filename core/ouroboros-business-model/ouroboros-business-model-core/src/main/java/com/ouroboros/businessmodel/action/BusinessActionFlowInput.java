package com.ouroboros.businessmodel.action;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Request-shaped Flow work view. Business mappings take precedence over the original request. */
final class BusinessActionFlowInput extends AbstractMap<String, Object> {
  private final Map<String, Object> values;
  private final Map<?, ?> request;
  private Map<String, Object> params;

  BusinessActionFlowInput(Map<String, Object> values, Object request) {
    this.values = values;
    this.request = request instanceof Map<?, ?> map ? map : Map.of();
  }

  @Override public Object get(Object key) {
    if (values.containsKey(key)) {
      Object value = values.get(key);
      if ("params".equals(key) && value instanceof Map<?, ?> map && request.containsKey("params")) {
        if (params == null) { params = new Parameters(map, request); }
        return params;
      }
      return value;
    }
    if ("body".equals(key) && values.containsKey("data")) { return values.get("data"); }
    if ("data".equals(key) && values.containsKey("body")) { return values.get("body"); }
    return request.get(key);
  }

  @Override public boolean containsKey(Object key) {
    return values.containsKey(key)
        || "body".equals(key) && values.containsKey("data")
        || "data".equals(key) && values.containsKey("body")
        || request.containsKey(key);
  }

  @Override public Set<String> keySet() {
    var keys = new LinkedHashSet<>(values.keySet());
    request.keySet().forEach(key -> { if (key instanceof String name) { keys.add(name); } });
    if (values.containsKey("data")) { keys.add("body"); }
    if (values.containsKey("body")) { keys.add("data"); }
    return Collections.unmodifiableSet(keys);
  }

  @Override public Set<Entry<String, Object>> entrySet() { return PreparedBusinessActionInput.entries(this); }

  @Override public Object put(String key, Object value) {
    Object previous = values.put(key, value);
    if ("params".equals(key)) { params = null; }
    return previous;
  }

  @Override public Object remove(Object key) {
    Object previous = values.remove(key);
    if ("params".equals(key)) { params = null; }
    return previous;
  }

  @Override public void clear() { values.clear(); params = null; }

  /** Keep non-enumerable header fallback and explicit nulls without modifying the HTTP request. */
  private static final class Parameters extends AbstractMap<String, Object> {
    private final Map<?, ?> business;
    private final Map<?, ?> request;
    private final Map<String, Object> changes = new LinkedHashMap<>();
    private final Set<Object> removed = new LinkedHashSet<>();
    private Map<?, ?> fallback;
    private boolean cleared;

    private Parameters(Map<?, ?> business, Map<?, ?> request) {
      this.business = business;
      this.request = request;
    }

    private Map<?, ?> fallback() {
      if (fallback == null) {
        fallback = request.get("params") instanceof Map<?, ?> map ? map : Map.of();
      }
      return fallback;
    }

    @Override public Object get(Object key) {
      if (changes.containsKey(key)) { return changes.get(key); }
      if (cleared || removed.contains(key)) { return null; }
      return business.containsKey(key) ? business.get(key) : fallback().get(key);
    }

    @Override public boolean containsKey(Object key) {
      return changes.containsKey(key) || !cleared && !removed.contains(key)
          && (business.containsKey(key) || fallback().containsKey(key));
    }

    @Override public Set<String> keySet() {
      var keys = new LinkedHashSet<>(changes.keySet());
      if (!cleared) {
        business.keySet().forEach(key -> { if (key instanceof String name) { keys.add(name); } });
        fallback().keySet().forEach(key -> { if (key instanceof String name) { keys.add(name); } });
        keys.removeAll(removed);
      }
      return Collections.unmodifiableSet(keys);
    }

    @Override public Set<Entry<String, Object>> entrySet() { return PreparedBusinessActionInput.entries(this); }

    @Override public Object put(String key, Object value) {
      Object previous = get(key);
      removed.remove(key);
      changes.put(key, value);
      return previous;
    }

    @Override public Object remove(Object key) {
      Object previous = get(key);
      changes.remove(key);
      removed.add(key);
      return previous;
    }

    @Override public void clear() { changes.clear(); removed.clear(); cleared = true; }
  }
}
