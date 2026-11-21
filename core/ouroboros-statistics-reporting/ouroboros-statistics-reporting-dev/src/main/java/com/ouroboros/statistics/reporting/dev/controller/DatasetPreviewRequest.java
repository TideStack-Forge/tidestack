package com.ouroboros.statistics.reporting.dev.controller;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;

public class DatasetPreviewRequest extends DatasetExecutionRequest {
  private com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection testConnection;

  public com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection getTestConnection() {
    return testConnection;
  }

  public void setTestConnection(com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection connection) {
    this.testConnection = connection;
  }

  private DatasetDefinition dataset;
  private String dialectName;

  public DatasetDefinition getDataset() {
    return dataset;
  }

  public void setDataset(DatasetDefinition dataset) {
    this.dataset = dataset;
  }

  public String getDialectName() {
    return dialectName;
  }

  public void setDialectName(String dialectName) {
    this.dialectName = dialectName;
  }

  @Override
  public DatasetPreviewRequest copy() {
    DatasetExecutionRequest copiedBase = super.copy();
    DatasetPreviewRequest copied = new DatasetPreviewRequest();
    copied.setDatasetName(copiedBase.getDatasetName());
    copied.setCompositeViewName(copiedBase.getCompositeViewName());
    copied.setPage(copiedBase.getPage());
    copied.setDynamicFilter(copiedBase.getDynamicFilter());
    copied.setExecutionBudget(copiedBase.getExecutionBudget());
    copied.setExport(copiedBase.isExport());
    copied.setRuntimeParameters(copiedBase.getRuntimeParameters());
    copied.setExecutionContext(copiedBase.getExecutionContext());
    copied.setAppliedParameters(copiedBase.getAppliedParameters());
    copied.setDataset(dataset);
    copied.setDialectName(dialectName);
    copied.setTestConnection(testConnection);
    return copied;
  }
}
