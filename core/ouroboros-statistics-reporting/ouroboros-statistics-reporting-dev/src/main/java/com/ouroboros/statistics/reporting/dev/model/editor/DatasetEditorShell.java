package com.ouroboros.statistics.reporting.dev.model.editor;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.web.SelectOption;

public class DatasetEditorShell {
  private LowcodeDatasetDefinition dataset;
  private List<SelectOption> datasourceOptions = new ArrayList<SelectOption>();

  public LowcodeDatasetDefinition getDataset() {
    return dataset;
  }

  public void setDataset(LowcodeDatasetDefinition dataset) {
    this.dataset = dataset;
  }

  public List<SelectOption> getDatasourceOptions() {
    return datasourceOptions;
  }

  public void setDatasourceOptions(List<SelectOption> datasourceOptions) {
    this.datasourceOptions = datasourceOptions == null ? new ArrayList<SelectOption>() : datasourceOptions;
  }
}
