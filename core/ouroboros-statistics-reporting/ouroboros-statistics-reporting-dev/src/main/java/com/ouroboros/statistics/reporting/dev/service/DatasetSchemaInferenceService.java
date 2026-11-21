package com.ouroboros.statistics.reporting.dev.service;

import com.ouroboros.statistics.reporting.dev.model.DatasetInferSchemaRequest;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetInferSchemaResult;

public interface DatasetSchemaInferenceService {
  DatasetInferSchemaResult inferSchema(String appName, DatasetInferSchemaRequest request);

  default void testConnection(String appName, DatasetInferSchemaRequest request) {
    throw new UnsupportedOperationException("dataset connection test is not configured");
  }
}
