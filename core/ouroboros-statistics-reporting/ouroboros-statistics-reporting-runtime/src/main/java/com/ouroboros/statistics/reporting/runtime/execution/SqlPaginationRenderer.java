package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.Locale;

public class SqlPaginationRenderer {

  public String render(SqlDataSourceContext context, PageSpec page, boolean hasStableOrder) {
    if (context == null) {
      throw new IllegalArgumentException("sql data source context is required for pagination");
    }
    if (page == null) {
      return "";
    }
    String dialect = normalize(context.getDialect());
    long offset = ((long) page.getPageNo() - 1L) * page.getPageSize();
    if (isLimitOffsetDialect(dialect)) {
      return " LIMIT " + page.getPageSize() + " OFFSET " + offset;
    }
    if ("oracle".equals(dialect)) {
      return " OFFSET " + offset + " ROWS FETCH NEXT " + page.getPageSize() + " ROWS ONLY";
    }
    if ("sqlserver".equals(dialect)) {
      if (!hasStableOrder) {
        throw new IllegalArgumentException("sqlserver pagination requires a stable order");
      }
      return " OFFSET " + offset + " ROWS FETCH NEXT " + page.getPageSize() + " ROWS ONLY";
    }
    throw new IllegalArgumentException("unsupported SQL pagination dialect: " + context.getDialect());
  }

  private boolean isLimitOffsetDialect(String dialect) {
    return "mysql".equals(dialect)
        || "postgresql".equals(dialect)
        || "mariadb".equals(dialect)
        || "h2".equals(dialect);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
