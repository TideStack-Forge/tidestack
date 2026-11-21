package com.ouroboros.statistics.reporting.dev.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;

@Service
@DevelopModule("chart")
public class ChartDevService extends AbstractChartDevService<LowcodeChartDefinition> {
  static final String MODEL_FILE_DIRECTORY = "statistics-reporting/charts";
  static final String MODEL_FILE_EXTENSION = ".chart.json";

  public ChartDevService(DatasetDevService datasetDevService) {
    super(datasetDevService, MODEL_FILE_DIRECTORY, MODEL_FILE_EXTENSION, "图表");
  }

  @Override
  protected TypeReference<LowcodeChartDefinition> getModelTypeReference() {
    return new TypeReference<LowcodeChartDefinition>() {
    };
  }
}
