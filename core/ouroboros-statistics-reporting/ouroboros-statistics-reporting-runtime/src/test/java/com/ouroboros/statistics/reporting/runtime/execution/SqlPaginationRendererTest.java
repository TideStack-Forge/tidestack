package com.ouroboros.statistics.reporting.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SqlPaginationRendererTest {

  private final SqlPaginationRenderer renderer = new SqlPaginationRenderer();

  @Test
  void shouldRenderLimitOffsetForCommonDialects() {
    PageSpec page = page(2, 25);

    assertEquals(" LIMIT 25 OFFSET 25", renderer.render(context("mysql"), page, false));
    assertEquals(" LIMIT 25 OFFSET 25", renderer.render(context("postgresql"), page, false));
    assertEquals(" LIMIT 25 OFFSET 25", renderer.render(context("h2"), page, false));
  }

  @Test
  void shouldRenderFetchSyntaxForOracle() {
    assertEquals(" OFFSET 25 ROWS FETCH NEXT 25 ROWS ONLY",
        renderer.render(context("oracle"), page(2, 25), false));
  }

  @Test
  void shouldRequireStableOrderForSqlServer() {
    assertThrows(IllegalArgumentException.class,
        () -> renderer.render(context("sqlserver"), page(1, 25), false));
    assertEquals(" OFFSET 25 ROWS FETCH NEXT 25 ROWS ONLY",
        renderer.render(context("sqlserver"), page(2, 25), true));
  }

  @Test
  void shouldRejectUnknownDialectInsteadOfSendingInvalidSql() {
    assertThrows(IllegalArgumentException.class,
        () -> renderer.render(context("db2"), page(1, 25), true));
  }

  private SqlDataSourceContext context(String dialect) {
    return new SqlDataSourceContext().setDialect(dialect);
  }

  private PageSpec page(int pageNo, int pageSize) {
    PageSpec page = new PageSpec();
    page.setPageNo(pageNo);
    page.setPageSize(pageSize);
    return page;
  }
}
