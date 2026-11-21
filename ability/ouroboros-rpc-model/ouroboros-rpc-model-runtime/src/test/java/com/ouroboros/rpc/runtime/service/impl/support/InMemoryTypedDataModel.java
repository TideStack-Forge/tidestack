package com.ouroboros.rpc.runtime.service.impl.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.data.model.TypedDataModel;

public abstract class InMemoryTypedDataModel<M> implements TypedDataModel<String, M> {
  private final Map<String, M> store = new LinkedHashMap<>();

  protected abstract String getId(M model);

  public InMemoryTypedDataModel<M> put(M model) {
    store.put(getId(model), model);
    return this;
  }

  public M peek(String id) {
    return store.get(id);
  }

  public List<M> values() {
    return new ArrayList<>(store.values());
  }

  @Override
  public DataModel getDataModel() {
    return null;
  }

  @Override
  public Try<M> insert(M data) {
    store.put(getId(data), data);
    return Try.success(data);
  }

  @Override
  public Try<List<M>> batchInsert(Collection<M> dataList) {
    List<M> result = new ArrayList<>();
    dataList.forEach(data -> {
      store.put(getId(data), data);
      result.add(data);
    });
    return Try.success(result);
  }

  @Override
  public Try<Long> delete(String id) {
    return Try.success(store.remove(id) == null ? 0L : 1L);
  }

  @Override
  public Try<Long> delete(Collection<String> ids) {
    long deleted = 0L;
    for (String id : ids) {
      if (store.remove(id) != null) {
        deleted++;
      }
    }
    return Try.success(deleted);
  }

  @Override
  public Try<Long> delete(Map<String, Object> where) {
    return Try.failure(new UnsupportedOperationException("delete(where) is not supported by InMemoryTypedDataModel"));
  }

  @Override
  public Try<Long> update(String id, M data) {
    store.put(id, data);
    return Try.success(1L);
  }

  @Override
  public Try<Long> update(String id, Map<String, Object> data) {
    return Try.failure(new UnsupportedOperationException("update(id, map) is not supported by InMemoryTypedDataModel"));
  }

  @Override
  public Try<Long> update(Collection<String> ids, Map<String, Object> data) {
    return Try.failure(new UnsupportedOperationException("update(ids, map) is not supported by InMemoryTypedDataModel"));
  }

  @Override
  public Try<Long> update(Map<String, Object> where, Map<String, Object> data) {
    return Try.failure(new UnsupportedOperationException("update(where, map) is not supported by InMemoryTypedDataModel"));
  }

  @Override
  public Try<Long> count(Map<String, Object> where) {
    if (where == null || where.isEmpty()) {
      return Try.success((long) store.size());
    }
    return Try.failure(new UnsupportedOperationException("count(where) is not supported by InMemoryTypedDataModel"));
  }

  @Override
  public Try<M> get(String id) {
    return Try.success(store.get(id));
  }

  @Override
  public Try<M> get(String id, Map<String, Object> statement) {
    return get(id);
  }

  @Override
  public Try<List<M>> query(Collection<String> ids) {
    List<M> result = new ArrayList<>();
    for (String id : ids) {
      if (store.containsKey(id)) {
        result.add(store.get(id));
      }
    }
    return Try.success(result);
  }

  @Override
  public Try<List<M>> query(Map<String, Object> statement) {
    return Try.success(values());
  }

  @Override
  public Try<List<M>> query(Collection<String> select, Map<String, Object> where) {
    return query(where);
  }

  @Override
  public Try<List<M>> query(Collection<String> select, Map<String, Object> where, String orderBy) {
    return query(where);
  }

  @Override
  public Try<List<M>> query(Collection<String> select, Map<String, Object> where, String orderBy, Integer offset, Integer limit) {
    List<M> values = values();
    int safeOffset = offset == null ? 0 : Math.max(offset, 0);
    if (safeOffset >= values.size()) {
      return Try.success(Collections.emptyList());
    }
    int safeLimit = limit == null ? values.size() - safeOffset : Math.max(limit, 0);
    int end = Math.min(values.size(), safeOffset + safeLimit);
    return Try.success(new ArrayList<>(values.subList(safeOffset, end)));
  }

  @Override
  public TypedDataModel<String, M> withPlugins(List<PluginDescriptor> pluginDescriptors) {
    return this;
  }

  @Override
  public TypedDataModel<String, M> withoutPlugins(List<String> pluginNames) {
    return this;
  }

  @Override
  public TypedDataModel<String, M> withoutPlugins() {
    return this;
  }

  @Override
  public boolean hasPlugin(String name) {
    return false;
  }
}
