package com.ouroboros.statistics.reporting.dev.service;

import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;

public interface DatasetDevConfigService {
  default void applyToExecutionRequest(String appName,
      com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest request, String datasource) {
    if (request == null || request.getDatasetName() == null || request.getDatasetName().isBlank()) return;
    var config = findByDataset(appName, request.getDatasetName()).orElse(null);
    if (config == null) return;
    var context = new java.util.LinkedHashMap<String, Object>(request.getExecutionContext() == null ? java.util.Map.of() : request.getExecutionContext());
    String requested = context.get("datasource") instanceof String value && !value.isBlank() ? value : datasource;
    String requestedSource = requested == null || requested.isBlank() ? "default" : requested.trim();
    String savedSource = config.getDatasource() == null || config.getDatasource().isBlank() ? "default" : config.getDatasource().trim();
    if (!requestedSource.equalsIgnoreCase(savedSource)) return;
    context.put("datasource", requestedSource);
    if (config.getTestConnection() != null) context.put("testConnection", config.getTestConnection());
    request.setExecutionContext(context);
    var parameters = new java.util.LinkedHashMap<String, Object>();
    if (config.getParameters() != null) parameters.putAll(config.getParameters());
    if (request.getRuntimeParameters() != null) parameters.putAll(request.getRuntimeParameters());
    request.setRuntimeParameters(parameters);
  }

  Try<DatasetDevConfigModel> save(DatasetDevConfigModel config);

  Optional<DatasetDevConfigModel> findByDataset(String appName, String datasetName);

  List<DatasetDevConfigModel> findByAppName(String appName);
}
