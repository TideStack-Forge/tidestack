package com.ouroboros.statistics.reporting.definition.query;

public class SqlDialectDefinition {
  private String dialectName;
  private String sql;

  public String getDialectName() {
    return dialectName;
  }

  public void setDialectName(String dialectName) {
    this.dialectName = dialectName;
  }

  public String getSql() {
    return sql;
  }

  public void setSql(String sql) {
    this.sql = sql;
  }
}
