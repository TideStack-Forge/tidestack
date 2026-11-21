package com.ouroboros.businessmodel.validation;

import java.util.*;

import com.ouroboros.businessmodel.BusinessModel;

/**
 * 验证上下文
 *
 * @param <B> 模型类型
 */
@SuppressWarnings("unused")
public class ValidationContext<B extends BusinessModel> {
  B model;
  Map<String, Object> attributes = new HashMap<>();
  List<Throwable> errors = new ArrayList<>();

  public ValidationContext(B model) {
    this.model = model;
  }

  public B getModel() {
    return model;
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

  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  public void addError(Throwable error) {
    errors.add(error);
  }

  public void addErrors(Throwable error) {

  }

  public List<Throwable> getErrors() {
    return Collections.unmodifiableList(errors);
  }
}
