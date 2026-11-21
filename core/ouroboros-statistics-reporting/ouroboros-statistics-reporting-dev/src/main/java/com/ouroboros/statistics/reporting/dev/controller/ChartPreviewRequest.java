package com.ouroboros.statistics.reporting.dev.controller;

import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;

public class ChartPreviewRequest extends DatasetExecutionRequest {
  private com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection testConnection;

  public com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection getTestConnection() {
    return testConnection;
  }

  public void setTestConnection(com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection connection) {
    this.testConnection = connection;
  }

  private ChartDefinition chart;

  public ChartDefinition getChart() {
    return chart;
  }

  public void setChart(ChartDefinition chart) {
    this.chart = chart;
  }

  @Override
  public ChartPreviewRequest copy() {
    DatasetExecutionRequest copiedBase = super.copy();
    ChartPreviewRequest copied = new ChartPreviewRequest();
    copied.setDatasetName(copiedBase.getDatasetName());
    copied.setRuntimeParameters(copiedBase.getRuntimeParameters());
    copied.setExecutionContext(copiedBase.getExecutionContext());
    copied.setAppliedParameters(copiedBase.getAppliedParameters());
    copied.setDynamicFilter(copiedBase.getDynamicFilter());
    copied.setPage(copiedBase.getPage());
    copied.setChart(chart);
    copied.setTestConnection(testConnection);
    return copied;
  }
}
