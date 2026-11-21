package com.ouroboros.statistics.reporting.runtime.result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;

public class DatasetResult {
  private List<Map<String, Object>> rows = new ArrayList<>();
  private DatasetSchemaDefinition resolvedSchema;
  private Map<String, Object> summary = new LinkedHashMap<>();
  private Map<String, Object> pageInfo = new LinkedHashMap<>();
  private Map<String, Object> executionMeta = new LinkedHashMap<>();
  private Map<String, Object> appliedParameters = new LinkedHashMap<>();
  private DatasetDiagnostics diagnostics = new DatasetDiagnostics();

  public List<Map<String, Object>> getRows() {
    return rows;
  }

  public void setRows(List<Map<String, Object>> rows) {
    this.rows = rows;
  }

  public DatasetSchemaDefinition getResolvedSchema() {
    return resolvedSchema;
  }

  public void setResolvedSchema(DatasetSchemaDefinition resolvedSchema) {
    this.resolvedSchema = resolvedSchema;
  }

  public Map<String, Object> getSummary() {
    return summary;
  }

  public void setSummary(Map<String, Object> summary) {
    this.summary = summary;
  }

  public Map<String, Object> getPageInfo() {
    return pageInfo;
  }

  public void setPageInfo(Map<String, Object> pageInfo) {
    this.pageInfo = pageInfo;
  }

  public Map<String, Object> getExecutionMeta() {
    return executionMeta;
  }

  public void setExecutionMeta(Map<String, Object> executionMeta) {
    this.executionMeta = executionMeta;
  }

  public Map<String, Object> getAppliedParameters() {
    return appliedParameters;
  }

  public void setAppliedParameters(Map<String, Object> appliedParameters) {
    this.appliedParameters = appliedParameters;
  }

  public DatasetDiagnostics getDiagnostics() {
    return diagnostics;
  }

  public void setDiagnostics(DatasetDiagnostics diagnostics) {
    this.diagnostics = diagnostics;
  }

}
