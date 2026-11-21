package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChartOptionBuilderRegistry {
  private final Map<String, ChartOptionBuilder> builders;

  public ChartOptionBuilderRegistry(List<ChartOptionBuilder> builders) {
    Map<String, ChartOptionBuilder> registered = new LinkedHashMap<String, ChartOptionBuilder>();
    for (ChartOptionBuilder builder : Objects.requireNonNull(builders, "chart option builders are required")) {
      if (builder == null) {
        continue;
      }
      for (String chartType : builder.chartTypes()) {
        ChartOptionBuilder previous = registered.putIfAbsent(chartType, builder);
        if (previous != null) {
          throw new IllegalArgumentException("duplicate chart option builder for chartType: " + chartType);
        }
      }
    }
    this.builders = Map.copyOf(registered);
  }

  public Map<String, Object> build(ChartParameters parameters, ChartProjection projection) {
    ChartOptionBuilder builder = builders.get(parameters.chartType());
    if (builder == null) {
      throw new IllegalArgumentException("no chart option builder registered for chartType: "
          + parameters.chartType());
    }
    return builder.build(parameters, projection);
  }
}
