package com.ouroboros.statistics.reporting.runtime.execution;

import javax.sql.DataSource;

public class SqlDataSourceContext {
  private String datasource;
  private String dialect;
  private String dialectVersion;
  private DataSource dataSource;

  public String getDatasource() {
    return datasource;
  }

  public SqlDataSourceContext setDatasource(String datasource) {
    this.datasource = datasource;
    return this;
  }

  public String getDialect() {
    return dialect;
  }

  public SqlDataSourceContext setDialect(String dialect) {
    this.dialect = dialect;
    return this;
  }

  public String getDialectVersion() {
    return dialectVersion;
  }

  public SqlDataSourceContext setDialectVersion(String dialectVersion) {
    this.dialectVersion = dialectVersion;
    return this;
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public SqlDataSourceContext setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
    return this;
  }
}
