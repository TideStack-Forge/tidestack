package com.ouroboros.statistics.reporting.runtime.result;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChartExecutionResponse {
  private String chartType;
  private Map<String, Object> option = new LinkedHashMap<String, Object>();

  public ChartExecutionResponse() {
  }

  public ChartExecutionResponse(String chartType, Map<String, Object> option) {
    this.chartType = chartType;
    this.option = option;
  }

  public String getChartType() {
    return chartType;
  }

  public void setChartType(String chartType) {
    this.chartType = chartType;
  }

  public Map<String, Object> getOption() {
    return option;
  }

  public void setOption(Map<String, Object> option) {
    this.option = option;
  }
}
