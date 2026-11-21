package com.ouroboros.statistics.reporting.dev.model;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodeChartDefinitionJsonSerdeTest {

  @Test
  void shouldDeserializeLowcodeChartDefinitionFromControllerPayload() {
    String json = "{"
        + "\"type\":\"chart\","
        + "\"name\":\"sales-kpi-runtime-report\","
        + "\"title\":\"Sales Orders KPI Runtime Report\","
        + "\"datasetName\":\"srt.sales.orders\","
        + "\"chartType\":\"metric-card\","
        + "\"params\":{\"valueFields\":[\"salesAmount\"]}"
        + "}";

    LowcodeChartDefinition result = Json.toBean(LowcodeChartDefinition.class, json);

    assertNotNull(result);
    assertEquals("chart", result.getType());
    assertEquals("sales-kpi-runtime-report", result.getName());
    assertEquals("metric-card", result.getChartType());
    assertEquals(java.util.List.of("salesAmount"), result.getParams().get("valueFields"));
    assertTrue(Json.toJsonString(result).contains("\"type\":\"chart\""));
  }
}
