package com.ouroboros.statistics.reporting.dev.model.editor;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;

public class DatasetReferenceSummary {
  private String name;
  private String title;
  private String datasetName;
  private String chartType;
  private Boolean isEnabled;
  private List<String> referencedFields = new ArrayList<String>();
  @JsonIgnore
  private ChartDefinition chartDefinition;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public void setDatasetName(String datasetName) {
    this.datasetName = datasetName;
  }

  public String getChartType() {
    return chartType;
  }

  public void setChartType(String chartType) {
    this.chartType = chartType;
  }

  public Boolean getIsEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(Boolean isEnabled) {
    this.isEnabled = isEnabled;
  }

  public List<String> getReferencedFields() {
    return referencedFields;
  }

  public void setReferencedFields(List<String> referencedFields) {
    this.referencedFields = referencedFields == null ? new ArrayList<String>() : referencedFields;
  }

  public ChartDefinition getChartDefinition() {
    return chartDefinition;
  }

  public void setChartDefinition(ChartDefinition chartDefinition) {
    this.chartDefinition = chartDefinition;
  }
}
