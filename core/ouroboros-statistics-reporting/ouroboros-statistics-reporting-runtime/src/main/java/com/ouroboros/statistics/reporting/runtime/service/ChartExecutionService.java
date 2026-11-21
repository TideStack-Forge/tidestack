package com.ouroboros.statistics.reporting.runtime.service;

import java.util.LinkedHashMap;
import java.util.Objects;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.result.ChartExecutionResponse;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

public class ChartExecutionService {
  private final DefinitionCenter<DatasetDefinition> datasetDefinitionCenter;
  private final DefinitionCenter<ChartDefinition> chartDefinitionCenter;
  private final DatasetExecutionService datasetExecutionService;
  private final ChartCompiler chartCompiler;

  public ChartExecutionService(DefinitionCenter<DatasetDefinition> datasetDefinitionCenter,
                               DefinitionCenter<ChartDefinition> chartDefinitionCenter,
                               DatasetExecutionService datasetExecutionService,
                               ChartCompiler chartCompiler) {
    this.datasetDefinitionCenter = Objects.requireNonNull(datasetDefinitionCenter, "datasetDefinitionCenter is required");
    this.chartDefinitionCenter = Objects.requireNonNull(chartDefinitionCenter, "chartDefinitionCenter is required");
    this.datasetExecutionService = Objects.requireNonNull(datasetExecutionService, "datasetExecutionService is required");
    this.chartCompiler = Objects.requireNonNull(chartCompiler, "chartCompiler is required");
  }

  public ChartExecutionResponse execute(String chartName, DatasetExecutionRequest request) {
    ChartDefinition chart = chartDefinitionCenter.getDefinition(chartName)
        .orElseThrow(() -> new IllegalArgumentException("chart definition not found: " + chartName));
    return execute(chart, request);
  }

  public ChartExecutionResponse execute(ChartDefinition chart, DatasetExecutionRequest request) {
    DatasetDefinition dataset = datasetDefinitionCenter.getDefinition(chart.getDatasetName())
        .orElseThrow(() -> new IllegalArgumentException(
            "dataset definition not found: " + chart.getDatasetName()));
    chartCompiler.validate(dataset, chart);
    DatasetExecutionRequest resolvedRequest = request == null ? new DatasetExecutionRequest() : request.copy();
    resolvedRequest.setDatasetName(dataset.getName());
    if (resolvedRequest.getRuntimeParameters() == null) {
      resolvedRequest.setRuntimeParameters(new LinkedHashMap<String, Object>());
    }
    DatasetResult result = datasetExecutionService.execute(resolvedRequest);
    return chartCompiler.compileValidated(chart, result);
  }
}
