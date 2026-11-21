package com.ouroboros.statistics.reporting.runtime.service;

import com.ouroboros.statistics.reporting.runtime.chart.*;

import java.util.Map;
import java.util.Objects;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.projector.ChartProjector;
import com.ouroboros.statistics.reporting.runtime.result.ChartExecutionResponse;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;

public class ChartCompiler {
  private final ChartDefinitionValidator validator;
  private final ChartParameterParser parameterParser;
  private final ChartProjector projector;
  private final ChartOptionBuilderRegistry optionBuilderRegistry;

  public ChartCompiler(ChartDefinitionValidator validator,
                       ChartParameterParser parameterParser,
                       ChartProjector projector,
                       ChartOptionBuilderRegistry optionBuilderRegistry) {
    this.validator = Objects.requireNonNull(validator, "validator is required");
    this.parameterParser = Objects.requireNonNull(parameterParser, "parameterParser is required");
    this.projector = Objects.requireNonNull(projector, "projector is required");
    this.optionBuilderRegistry = Objects.requireNonNull(optionBuilderRegistry, "optionBuilderRegistry is required");
  }

  public ChartExecutionResponse compile(DatasetDefinition dataset,
                                        ChartDefinition chart,
                                        DatasetResult result) {
    validate(dataset, chart);
    return compileValidated(chart, result);
  }

  public void validate(DatasetDefinition dataset, ChartDefinition chart) {
    validator.validate(dataset, chart);
  }

  ChartExecutionResponse compileValidated(ChartDefinition chart, DatasetResult result) {
    ChartParameters parameters = parameterParser.parse(chart);
    Map<String, Object> option = optionBuilderRegistry.build(parameters, projector.project(result));
    return new ChartExecutionResponse(chart.getChartType(), option);
  }
}
