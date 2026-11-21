package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.Map;
import java.util.Set;

public interface ChartOptionBuilder {
  Set<String> chartTypes();

  Map<String, Object> build(ChartParameters parameters, ChartProjection projection);
}
