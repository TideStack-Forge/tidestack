package com.ouroboros.data.initialization;

import java.util.Map;
import java.util.Optional;

public interface TargetDatasetGateway {
  Optional<TargetDatasetRow> findOne(String targetModel, Map<String, Object> matchValues);

  void insert(String targetModel, Map<String, Object> values);
}
