package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;

public class ChartParameterParser {

  public ChartParameters parse(ChartDefinition chart) {
    if (chart == null) {
      throw new IllegalArgumentException("chart definition is required");
    }
    if (chart.getChartType() == null || chart.getChartType().isBlank()) {
      throw new IllegalArgumentException("chartType is required");
    }
    Map<String, Object> values = chart.getParams() == null
        ? new LinkedHashMap<String, Object>()
        : chart.getParams();
    return new ChartParameters(chart.getChartType(), values);
  }
}
