package com.ouroboros.businessmodel.meta;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ouroboros.util.Maps;

public class MetaWithExtraProps {
  protected Map<String, Object> extraProps = new LinkedHashMap<>();

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public void setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps;
  }

  public void putExtraProps(Map<String, Object> extraProps) {
    this.extraProps.putAll(extraProps);
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    if ("fullName".equals(key)) {
      return;
    }
    extraProps.put(key, value);
  }

  public Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key));
  }

  public <T> Optional<T> getExtraProp(Class<T> type, String key) {
    return getExtraProp(key).map(v -> type.isInstance(v) ? type.cast(v) : null);
  }

  public <T> T getExtraProps(Class<T> type, String key, T defaultValue) {
    return getExtraProp(type, key).orElse(defaultValue);
  }

  public Optional<Object> getExtraPropDeep(List<String> path) {
    return Maps.Hierarchical.getValue(getExtraProps(), path.stream().collect(Collectors.joining(".")));
  }

  public Optional<Object> getExtraPropDeep(String... pathKey) {
    return getExtraPropDeep(Arrays.asList(pathKey));
  }

  public <T> Optional<T> getExtraPropDeep(Class<T> clazz, String... pathKey) {
    return getExtraPropDeep(clazz, Arrays.asList(pathKey));
  }

  public <T> Optional<T> getExtraPropDeep(Class<T> clazz, List<String> path) {
    var value = getExtraPropDeep(path);
    return value
        .map(v -> {
          if (clazz.isInstance(v)) {
            return clazz.cast(v);
          }
          return null;
        });
  }
}
