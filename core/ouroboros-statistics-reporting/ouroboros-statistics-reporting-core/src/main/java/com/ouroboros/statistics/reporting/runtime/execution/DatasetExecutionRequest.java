package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public class DatasetExecutionRequest {
  @com.fasterxml.jackson.annotation.JsonIgnore
  private DatasetExecutionBudget executionBudget;
  @com.fasterxml.jackson.annotation.JsonIgnore
  private boolean export;
  private String datasetName;
  private String compositeViewName;
  private Map<String, Object> runtimeParameters = new LinkedHashMap<>();
  private Map<String, Object> executionContext = new LinkedHashMap<>();
  private Map<String, Object> appliedParameters = new LinkedHashMap<>();
  private DynamicFilterSpec dynamicFilter;
  private PageSpec page;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public DatasetExecutionBudget getExecutionBudget() { return executionBudget; }
  @com.fasterxml.jackson.annotation.JsonIgnore
  public void setExecutionBudget(DatasetExecutionBudget budget) { this.executionBudget = budget; }
  @com.fasterxml.jackson.annotation.JsonIgnore
  public boolean isExport() { return export; }
  @com.fasterxml.jackson.annotation.JsonIgnore
  public void setExport(boolean export) { this.export = export; }

  public String getDatasetName() {
    return datasetName;
  }

  public void setDatasetName(String datasetName) {
    this.datasetName = datasetName;
  }

  public String getCompositeViewName() {
    return compositeViewName;
  }

  public void setCompositeViewName(String compositeViewName) {
    this.compositeViewName = compositeViewName;
  }

  public Map<String, Object> getRuntimeParameters() {
    return runtimeParameters;
  }

  public void setRuntimeParameters(Map<String, Object> runtimeParameters) {
    this.runtimeParameters = runtimeParameters;
  }

  public Map<String, Object> getExecutionContext() {
    return executionContext;
  }

  public void setExecutionContext(Map<String, Object> executionContext) {
    this.executionContext = executionContext;
  }

  public Map<String, Object> getAppliedParameters() {
    return appliedParameters;
  }

  public void setAppliedParameters(Map<String, Object> appliedParameters) {
    this.appliedParameters = appliedParameters;
  }

  public DynamicFilterSpec getDynamicFilter() {
    return dynamicFilter;
  }

  public void setDynamicFilter(DynamicFilterSpec dynamicFilter) {
    this.dynamicFilter = dynamicFilter;
  }

  public PageSpec getPage() {
    return page;
  }

  public void setPage(PageSpec page) {
    this.page = page;
  }

  public DatasetExecutionRequest copy() {
    DatasetExecutionRequest copied = new DatasetExecutionRequest();
    copied.setDatasetName(datasetName);
    copied.setExecutionBudget(executionBudget);
    copied.setExport(export);
    copied.setCompositeViewName(compositeViewName);
    copied.setRuntimeParameters(copyMap(runtimeParameters));
    copied.setExecutionContext(copyMap(executionContext));
    copied.setAppliedParameters(copyMap(appliedParameters));
    copied.setDynamicFilter(dynamicFilter == null ? null : dynamicFilter.copy());
    copied.setPage(page == null ? null : page.copy());
    return copied;
  }

  private Map<String, Object> copyMap(Map<String, Object> source) {
    Map<String, Object> copied = new LinkedHashMap<>();
    if (source != null) {
      copied.putAll(source);
    }
    return copied;
  }
}
