package com.ouroboros.statistics.reporting.definition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.QueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;

public class DatasetDefinition {
  private String name;
  private String title;
  private String description;
  private List<String> tags = new ArrayList<>();
  private Boolean isEnabled = Boolean.TRUE;
  private QueryDefinition queryDefinition;
  private List<ParameterDefinition> parameters = new ArrayList<>();
  private DatasetSchemaDefinition schema;
  private Map<String, Object> permissionDefinition = new LinkedHashMap<>();
  private Map<String, Object> executionConstraints = new LinkedHashMap<>();

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags == null ? new ArrayList<String>() : tags;
  }

  public Boolean getIsEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(Boolean isEnabled) {
    this.isEnabled = isEnabled;
  }

  public QueryDefinition getQueryDefinition() {
    return queryDefinition;
  }

  public void setQueryDefinition(QueryDefinition queryDefinition) {
    this.queryDefinition = queryDefinition;
  }

  public List<ParameterDefinition> getParameters() {
    return parameters;
  }

  public void setParameters(List<ParameterDefinition> parameters) {
    this.parameters = parameters == null ? new ArrayList<ParameterDefinition>() : parameters;
  }

  public DatasetSchemaDefinition getSchema() {
    return schema;
  }

  public void setSchema(DatasetSchemaDefinition schema) {
    this.schema = schema;
  }

  public Map<String, Object> getPermissionDefinition() {
    return permissionDefinition;
  }

  public void setPermissionDefinition(Map<String, Object> permissionDefinition) {
    this.permissionDefinition = permissionDefinition == null
        ? new LinkedHashMap<String, Object>()
        : permissionDefinition;
  }

  public Map<String, Object> getExecutionConstraints() {
    return executionConstraints;
  }

  public void setExecutionConstraints(Map<String, Object> executionConstraints) {
    this.executionConstraints = executionConstraints == null
        ? new LinkedHashMap<String, Object>()
        : executionConstraints;
  }

}
