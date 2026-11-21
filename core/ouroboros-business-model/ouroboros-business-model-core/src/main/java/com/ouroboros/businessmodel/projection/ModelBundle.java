package com.ouroboros.businessmodel.projection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 模型集合
 */
public class ModelBundle {
  private final Map<Class<?>, Map<String, Object>> modelBundleMap = new HashMap<>();

  @SuppressWarnings("unchecked")
  public <T> Map<String, T> getModelMap(Class<T> modelClass) {
    return (Map<String, T>) modelBundleMap.computeIfAbsent(modelClass, k -> new HashMap<>());
  }

  public <T> Optional<T> getModel(Class<T> modelClass, String modelName) {
    return Optional.ofNullable(getModelMap(modelClass).get(modelName));
  }

  public <T> void putModel(Class<T> modelClass, String modelName, T model) {
    getModelMap(modelClass).put(modelName, model);
  }

  public <T> ModelAccessor<T> getModelAccessor(Class<T> modelClass) {
    var modelMap = getModelMap(modelClass);
    return new ModelAccessor<T>() {
      @Override
      public Optional<T> getModel(String modelName) {
        return Optional.ofNullable(modelMap.get(modelName));
      }

      @Override
      public void putModel(String modelName, T model) {
        modelMap.put(modelName, model);
      }
    };
  }

  public Map<Class<?>, Map<String, Object>> getAll() {
    return Collections.unmodifiableMap(modelBundleMap);
  }

  public interface ModelAccessor<T> {
    Optional<T> getModel(String modelName);

    void putModel(String modelName, T model);
  }
}
