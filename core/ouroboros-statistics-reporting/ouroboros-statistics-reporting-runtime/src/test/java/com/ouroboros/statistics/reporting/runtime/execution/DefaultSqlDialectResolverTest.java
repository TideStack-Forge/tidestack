package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlDialectDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSqlDialectResolverTest {

  @Test
  void shouldPreferMatchingDialectBeforeBaseSql() {
    var queryDefinition = new SqlQueryDefinition();
    queryDefinition.setSql("select 1 as total");
    queryDefinition.setDialects(Collections.singletonList(dialect("mysql", "select 3 as total")));

    var selected = new DefaultSqlDialectResolver().resolve(
        queryDefinition,
        new SqlDataSourceContext()
            .setDatasource("reporting-main")
            .setDialect("mysql")
            .setDialectVersion("8.0.36")
    );

    assertEquals("mysql", selected.getMatchedDialect().getDialectName());
    assertEquals("select 3 as total", selected.getSql());
    assertFalse(selected.isFallbackToSql());
  }

  @Test
  void shouldFallbackToBaseSqlWhenNoDialectMatches() {
    var queryDefinition = new SqlQueryDefinition();
    queryDefinition.setSql("select 1 as total");
    queryDefinition.setDialects(Collections.singletonList(dialect("postgresql", "select 2 as total")));

    var selected = new DefaultSqlDialectResolver().resolve(
        queryDefinition,
        new SqlDataSourceContext()
            .setDatasource("analytics-h2")
            .setDialect("h2")
            .setDialectVersion("2.2.224")
    );

    assertTrue(selected.isFallbackToSql());
    assertEquals("select 1 as total", selected.getSql());
  }

  private static SqlDialectDefinition dialect(String dialectName, String sql) {
    var dialect = new SqlDialectDefinition();
    dialect.setDialectName(dialectName);
    dialect.setSql(sql);
    return dialect;
  }
}
