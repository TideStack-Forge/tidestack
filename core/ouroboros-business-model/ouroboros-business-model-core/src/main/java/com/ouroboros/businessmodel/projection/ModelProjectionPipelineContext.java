package com.ouroboros.businessmodel.projection;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.BusinessModel;

/**
 * 模型投射流水线上下文
 *
 * @param <M> 模型类型
 */
@SuppressWarnings("unused")
public class ModelProjectionPipelineContext<M extends BusinessModel> {
  private final ModelBundle bundle = new ModelBundle();
  private final M sourceModel;
  private final List<Throwable> errors = new ArrayList<>();
  Map<String, Object> attributes = new HashMap<>();

  public ModelProjectionPipelineContext(M sourceModel) {
    this.sourceModel = sourceModel;
  }

  public ModelBundle getBundle() {
    return bundle;
  }

  public M getSourceModel() {
    return sourceModel;
  }

  public <T> Map<String, T> getModelMap(Class<T> modelClass) {
    return bundle.getModelMap(modelClass);
  }

  public <T> Optional<T> getModel(Class<T> modelClass, String modelName) {
    return bundle.getModel(modelClass, modelName);
  }

  public <T> void putModel(Class<T> modelClass, String modelName, T model) {
    bundle.putModel(modelClass, modelName, model);
  }

  public <T> ModelBundle.ModelAccessor<T> getModelAccessor(Class<T> modelClass) {
    return bundle.getModelAccessor(modelClass);
  }

  public Map<Class<?>, Map<String, Object>> getAll() {
    return bundle.getAll();
  }

  public List<Throwable> getErrors() {
    return errors;
  }

  public void addError(Throwable error) {
    errors.add(error);
  }

  public void addErrors(List<Throwable> errors) {
    this.errors.addAll(errors);
  }

  public Optional<Object> getAttribute(String key) {
    return Optional.ofNullable(attributes.get(key));
  }

  public <T> Optional<T> getAttribute(Class<T> clazz, String key) {
    var value = getAttribute(key);
    return value.map((v) -> {
      if (clazz.isInstance(v)) {
        return clazz.cast(v);
      }
      return null;
    });
  }

  public <T> T getAttribute(Class<T> clazz, String key, T defaultValue) {
    var value = getAttribute(key);
    return value.map((v) -> {
      if (clazz.isInstance(v)) {
        return clazz.cast(v);
      }
      return null;
    }).orElse(defaultValue);
  }

  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  public <T> void updateAttribute(String key, Consumer<T> updater, Supplier<T> defaultSupplier) {
    var value = getAttribute(key);
    if (!value.isPresent()) {
      var defaultValue = defaultSupplier.get();
      value = Optional.of(defaultValue);
      attributes.put(key, defaultValue);
    }
    updater.accept((T) value.get());
  }

}
