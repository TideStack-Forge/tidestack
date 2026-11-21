package com.ouroboros.statistics.reporting.definition.query;

import java.util.ArrayList;
import java.util.List;

public class SqlQueryDefinition extends QueryDefinition {
  private String datasource;
  private String sql;
  private List<SqlDialectDefinition> dialects = new ArrayList<>();

  public SqlQueryDefinition() {
    setType("sql");
  }

  public String getDatasource() {
    return datasource;
  }

  public void setDatasource(String datasource) {
    this.datasource = datasource;
  }

  public String getSql() {
    return sql;
  }

  public void setSql(String sql) {
    this.sql = sql;
  }

  public List<SqlDialectDefinition> getDialects() {
    return dialects;
  }

  public void setDialects(List<SqlDialectDefinition> dialects) {
    this.dialects = dialects;
  }
}
