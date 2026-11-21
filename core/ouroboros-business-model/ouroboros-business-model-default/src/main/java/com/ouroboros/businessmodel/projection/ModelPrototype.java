package com.ouroboros.businessmodel.projection;

import java.util.Collections;
import java.util.Map;

public interface ModelPrototype<M, T extends ModelPrototype<M, T>> {
  T configure(Map<String, Object> config);

  default T configure(String key, Object value) {
    return configure(Collections.singletonMap(key, value));
  }

  default T removeConfig(String key) {
    return (T) this;
  }

  default T removeConfigs(String... keys) {
    for (var key : keys) {
      removeConfig(key);
    }
    return (T) this;
  }

  M getModel();
}
