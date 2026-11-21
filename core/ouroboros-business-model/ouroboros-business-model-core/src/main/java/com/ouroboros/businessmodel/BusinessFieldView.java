package com.ouroboros.businessmodel;

import java.util.Map;
import java.util.Optional;

public interface BusinessFieldView {
  String getName();
  String getTitle();
  String getType();
  Map<String, Object> getExtraProps();
  Optional<Object> getExtraProp(String key);
  <T> Optional<T> getExtraProp(Class<T> type, String key);
}
