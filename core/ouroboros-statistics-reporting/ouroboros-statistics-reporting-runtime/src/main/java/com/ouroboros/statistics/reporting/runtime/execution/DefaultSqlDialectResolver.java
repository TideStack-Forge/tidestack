package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlDialectDefinition;

public class DefaultSqlDialectResolver {

  public ResolvedSql resolve(SqlQueryDefinition queryDefinition, SqlDataSourceContext context) {
    if (queryDefinition == null) {
      throw new IllegalArgumentException("sql query definition is required");
    }
    if (context == null) {
      throw new IllegalArgumentException("sql data source context is required");
    }

    List<SqlDialectDefinition> dialects = queryDefinition.getDialects() == null
        ? Collections.<SqlDialectDefinition>emptyList()
        : queryDefinition.getDialects();

    SqlDialectDefinition selected = selectDialect(dialects, context);
    if (selected != null) {
      return new ResolvedSql()
          .setSql(selected.getSql())
          .setMatchedDialect(selected)
          .setFallbackToSql(false);
    }
    if (isBlank(queryDefinition.getSql())) {
      throw new IllegalArgumentException("no SQL dialect matched and sql is empty");
    }
    return new ResolvedSql()
        .setSql(queryDefinition.getSql())
        .setFallbackToSql(true);
  }

  private SqlDialectDefinition selectDialect(List<SqlDialectDefinition> dialects,
                                             SqlDataSourceContext context) {
    List<SqlDialectDefinition> matched = new ArrayList<SqlDialectDefinition>();
    for (SqlDialectDefinition dialect : dialects) {
      if (dialect == null) {
        continue;
      }
      if (matchesDialect(dialect, context)) {
        matched.add(dialect);
      }
    }
    return matched
        .stream()
        .findFirst()
        .orElse(null);
  }

  private boolean matchesDialect(SqlDialectDefinition dialect, SqlDataSourceContext context) {
    return !isBlank(dialect.getDialectName())
        && !isBlank(context.getDialect())
        && dialect.getDialectName().equalsIgnoreCase(context.getDialect());
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public static class ResolvedSql {
    private String sql;
    private SqlDialectDefinition matchedDialect;
    private boolean fallbackToSql;

    public String getSql() {
      return sql;
    }

    public ResolvedSql setSql(String sql) {
      this.sql = sql;
      return this;
    }

    public SqlDialectDefinition getMatchedDialect() {
      return matchedDialect;
    }

    public ResolvedSql setMatchedDialect(SqlDialectDefinition matchedDialect) {
      this.matchedDialect = matchedDialect;
      return this;
    }

    public boolean isFallbackToSql() {
      return fallbackToSql;
    }

    public ResolvedSql setFallbackToSql(boolean fallbackToSql) {
      this.fallbackToSql = fallbackToSql;
      return this;
    }
  }
}
