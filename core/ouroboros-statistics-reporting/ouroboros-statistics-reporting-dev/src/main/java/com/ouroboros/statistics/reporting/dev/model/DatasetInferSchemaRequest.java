package com.ouroboros.statistics.reporting.dev.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlDialectDefinition;

public class DatasetInferSchemaRequest extends DatasetDataSourceRequest {
  private String datasetName;
  private String sql;
  private String dialectName;
  private List<SqlDialectDefinition> dialects = new ArrayList<SqlDialectDefinition>();
  private List<ParameterDefinition> parameterDefinitions = new ArrayList<ParameterDefinition>();
  private Map<String, Object> parameters = new LinkedHashMap<String, Object>();

  public String getDatasetName() {
    return datasetName;
  }

  public void setDatasetName(String datasetName) {
    this.datasetName = datasetName;
  }

  public String getSql() {
    return sql;
  }

  public void setSql(String sql) {
    this.sql = sql;
  }

  public String getDialectName() {
    return dialectName;
  }

  public void setDialectName(String dialectName) {
    this.dialectName = dialectName;
  }

  public List<SqlDialectDefinition> getDialects() {
    return dialects;
  }

  public void setDialects(List<SqlDialectDefinition> dialects) {
    this.dialects = dialects == null ? new ArrayList<SqlDialectDefinition>() : dialects;
  }

  public List<ParameterDefinition> getParameterDefinitions() {
    return parameterDefinitions;
  }

  public void setParameterDefinitions(List<ParameterDefinition> parameterDefinitions) {
    this.parameterDefinitions =
        parameterDefinitions == null ? new ArrayList<ParameterDefinition>() : parameterDefinitions;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public void setParameters(Map<String, Object> parameters) {
    this.parameters = parameters;
  }

}
