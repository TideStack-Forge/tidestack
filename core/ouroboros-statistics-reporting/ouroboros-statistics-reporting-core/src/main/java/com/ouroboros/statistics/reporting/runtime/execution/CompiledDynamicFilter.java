package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public class CompiledDynamicFilter {
  private String whereClause;
  private String orderByClause;
  private Map<String, Object> parameters = new LinkedHashMap<String, Object>();

  public String getWhereClause() {
    return whereClause;
  }

  public void setWhereClause(String whereClause) {
    this.whereClause = whereClause;
  }

  public String getOrderByClause() {
    return orderByClause;
  }

  public void setOrderByClause(String orderByClause) {
    this.orderByClause = orderByClause;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public void setParameters(Map<String, Object> parameters) {
    this.parameters = parameters;
  }
}
