package com.ouroboros.data.initialization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record TargetDatasetRow(Map<String, Object> values) {
  public TargetDatasetRow {
    values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
