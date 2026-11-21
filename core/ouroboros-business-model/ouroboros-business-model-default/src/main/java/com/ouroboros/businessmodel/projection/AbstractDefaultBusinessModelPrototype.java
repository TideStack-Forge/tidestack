package com.ouroboros.businessmodel.projection;

import java.util.HashMap;
import java.util.Map;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.util.Maps;

public abstract class AbstractDefaultBusinessModelPrototype<M, T extends ModelPrototype<M, T>> implements ModelPrototype<M, T> {
  protected DefaultBusinessModel businessModel;
  protected Map<String, Object> config;

  protected AbstractDefaultBusinessModelPrototype(DefaultBusinessModel model) {
    this.businessModel = model;
    this.config = new HashMap<>();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T configure(Map<String, Object> config) {
    this.config.putAll(Maps.deepClone(config));
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T configure(String key, Object value) {
    if (value instanceof Map<?, ?> mapValue) {
      this.config.put(key, Maps.deepClone(mapValue));
    } else {
      this.config.put(key, value);
    }
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T removeConfig(String key) {
    config.remove(key);
    return (T) this;
  }
}
