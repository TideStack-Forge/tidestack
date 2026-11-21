package com.ouroboros.statistics.reporting.definition.view;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartDefinitionJsonSerdeTest {

  @Test
  void shouldDeserializeChartDefinition() {
    var json = "{"
        + "\"name\":\"sales.overview\","
        + "\"type\":\"chart\","
        + "\"datasetName\":\"demo.sales\","
        + "\"isEnabled\":true,"
        + "\"chartType\":\"metric-card\","
        + "\"params\":{\"valueFields\":[\"total\"]}"
        + "}";

    var result = Json.toBean(ChartDefinition.class, json);

    assertNotNull(result);
    assertEquals(Boolean.TRUE, result.getIsEnabled());
    var chart = assertInstanceOf(ChartDefinition.class, result);
    assertEquals("metric-card", chart.getChartType());
    assertEquals(java.util.List.of("total"), chart.getParams().get("valueFields"));
    assertTrue(Json.toJsonString(chart).contains("\"type\":\"chart\""));
  }

  @Test
  void shouldRejectUnknownChartProperty() {
    String json = "{"
        + "\"type\":\"chart\","
        + "\"name\":\"sales.overview\","
        + "\"datasetName\":\"demo.sales\","
        + "\"displayType\":\"bar\""
        + "}";

    assertTrue(Json.tryToStrictBean(ChartDefinition.class, json).isFailure());
  }

}
