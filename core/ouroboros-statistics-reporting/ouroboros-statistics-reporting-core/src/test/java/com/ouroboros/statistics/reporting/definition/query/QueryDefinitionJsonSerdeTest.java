package com.ouroboros.statistics.reporting.definition.query;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryDefinitionJsonSerdeTest {

  @Test
  void shouldRejectUnknownSqlQueryProperty() {
    String json = "{"
        + "\"type\":\"sql\","
        + "\"datasource\":\"default\","
        + "\"sql\":\"select 1\","
        + "\"genericSql\":\"select 2\""
        + "}";

    assertTrue(Json.tryToStrictBean(SqlQueryDefinition.class, json).isFailure());
  }

  @Test
  void shouldRejectUnknownSqlDialectProperty() {
    String json = "{"
        + "\"dialectName\":\"mysql\","
        + "\"sql\":\"select 1\","
        + "\"datasourceTag\":\"legacy\""
        + "}";

    assertTrue(Json.tryToStrictBean(SqlDialectDefinition.class, json).isFailure());
  }
}
