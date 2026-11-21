package com.ouroboros.statistics.reporting.definition;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetDefinitionJsonSerdeTest {

  @Test
  void shouldDeserializeSqlDatasetDefinition() {
    var json = "{"
        + "\"name\":\"demo.sales\","
        + "\"title\":\"销售汇总\","
        + "\"tags\":[\"analytics\",\"sales\"],"
        + "\"isEnabled\":true,"
        + "\"queryDefinition\":{"
        + "\"type\":\"sql\","
        + "\"datasource\":\"reporting-main\","
        + "\"sql\":\"select sum(amount) as total from sales where biz_date >= ${startDate}\","
        + "\"dialects\":[{"
        + "\"dialectName\":\"mysql\","
        + "\"sql\":\"select sum(amount) as total from sales where biz_date >= ${startDate}\""
        + "}]"
        + "},"
        + "\"parameters\":[{"
        + "\"name\":\"startDate\","
        + "\"title\":\"开始日期\","
        + "\"type\":\"date\","
        + "\"required\":true,"
        + "\"visibleInView\":true"
        + "}],"
        + "\"schema\":{"
        + "\"fields\":[{"
        + "\"name\":\"total\","
        + "\"title\":\"销售额\","
        + "\"dataType\":\"number\","
        + "\"semanticRole\":\"metric\","
        + "\"nullable\":false,"
        + "\"exportable\":true,"
        + "\"visibleByDefault\":true"
        + "}]"
        + "}"
        + "}";

    var result = Json.toBean(DatasetDefinition.class, json);

    assertNotNull(result);
    assertEquals("demo.sales", result.getName());
    assertEquals(Boolean.TRUE, result.getIsEnabled());
    assertEquals(2, result.getTags().size());
    var queryDefinition = assertInstanceOf(SqlQueryDefinition.class, result.getQueryDefinition());
    assertEquals("reporting-main", queryDefinition.getDatasource());
    assertEquals("select sum(amount) as total from sales where biz_date >= ${startDate}", queryDefinition.getSql());
    assertEquals(1, queryDefinition.getDialects().size());
    assertEquals("mysql", queryDefinition.getDialects().get(0).getDialectName());
    assertEquals("date", result.getParameters().get(0).getType());
    assertEquals("metric", result.getSchema().getFields().get(0).getSemanticRole());
    assertTrue(Json.toJsonString(result).contains("\"isEnabled\":true"));
  }

  @Test
  void shouldRejectUnknownDatasetProperty() {
    String json = "{"
        + "\"name\":\"demo.sales\","
        + "\"appName\":\"demo-app\""
        + "}";

    assertTrue(Json.tryToStrictBean(DatasetDefinition.class, json).isFailure());
  }

  @Test
  void shouldNormalizeNullableCollectionMetadataToEmptyCollections() {
    DatasetDefinition result = Json.toBean(DatasetDefinition.class,
        "{\"name\":\"demo.sales\",\"tags\":null,\"parameters\":null,"
            + "\"permissionDefinition\":null,\"executionConstraints\":null}");

    assertTrue(result.getTags().isEmpty());
    assertTrue(result.getParameters().isEmpty());
    assertTrue(result.getPermissionDefinition().isEmpty());
    assertTrue(result.getExecutionConstraints().isEmpty());
  }

}
