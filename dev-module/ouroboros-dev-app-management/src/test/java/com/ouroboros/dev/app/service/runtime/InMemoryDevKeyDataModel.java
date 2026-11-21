package com.ouroboros.dev.app.service.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.app.model.DevKeyEntity;

public class InMemoryDevKeyDataModel implements TypedDataModel<String, DevKeyEntity> {
  private final List<DevKeyEntity> records = new CopyOnWriteArrayList<>();

  @Override
  public DataModel getDataModel() {
    return null;
  }

  @Override
  public Try<DevKeyEntity> insert(DevKeyEntity data) {
    return Try.of(() -> {
      records.add(data);
      return data;
    });
  }

  @Override
  public Try<Long> delete(Map<String, Object> where) {
    return Try.of(() -> {
      List<DevKeyEntity> matches = records.stream()
          .filter(record -> matches(record, where))
          .collect(Collectors.toList());
      records.removeAll(matches);
      return (long) matches.size();
    });
  }

  @Override
  public Try<List<DevKeyEntity>> query(Map<String, Object> statement) {
    return Try.of(() -> {
      Object where = statement.get(Keyword.WHERE.toString());
      if (!(where instanceof Map)) {
        return new ArrayList<>(records);
      }
      return records.stream()
          .filter(record -> matches(record, castMap(where)))
          .collect(Collectors.toList());
    });
  }

  private boolean matches(DevKeyEntity record, Map<String, Object> where) {
    for (Map.Entry<String, Object> entry : where.entrySet()) {
      if (!Objects.equals(value(record, entry.getKey()), entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private Object value(DevKeyEntity record, String field) {
    if ("appName".equals(field)) {
      return record.getAppName();
    }
    if ("userIdentity".equals(field)) {
      return record.getUserIdentity();
    }
    if ("devKey".equals(field)) {
      return record.getDevKey();
    }
    return null;
  }

  private Map<String, Object> castMap(Object value) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
      Object key = entry.getKey();
      if (key instanceof String fieldName) {
        result.put(fieldName, entry.getValue());
      }
    }
    return result;
  }

  @Override
  public Try<List<DevKeyEntity>> batchInsert(Collection<DevKeyEntity> dataList) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<Long> delete(String id) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<Long> delete(Collection<String> ids) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<Long> update(String id, DevKeyEntity data) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<Long> update(String id, Map<String, Object> data) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<Long> update(Collection<String> ids, Map<String, Object> data) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<Long> update(Map<String, Object> where, Map<String, Object> data) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<Long> count(Map<String, Object> where) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<DevKeyEntity> get(String id) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<DevKeyEntity> get(String id, Map<String, Object> statement) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<List<DevKeyEntity>> query(Collection<String> ids) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<List<DevKeyEntity>> query(Collection<String> select, Map<String, Object> where) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<List<DevKeyEntity>> query(Collection<String> select, Map<String, Object> where, String orderBy) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public Try<List<DevKeyEntity>> query(Collection<String> select, Map<String, Object> where, String orderBy, Integer offset, Integer limit) {
    return Try.failure(new UnsupportedOperationException());
  }

  @Override
  public TypedDataModel<String, DevKeyEntity> withPlugins(List<PluginDescriptor> pluginDescriptors) {
    return this;
  }

  @Override
  public TypedDataModel<String, DevKeyEntity> withoutPlugins(List<String> pluginNames) {
    return this;
  }

  @Override
  public TypedDataModel<String, DevKeyEntity> withoutPlugins() {
    return this;
  }

  @Override
  public boolean hasPlugin(String name) {
    return false;
  }
}
