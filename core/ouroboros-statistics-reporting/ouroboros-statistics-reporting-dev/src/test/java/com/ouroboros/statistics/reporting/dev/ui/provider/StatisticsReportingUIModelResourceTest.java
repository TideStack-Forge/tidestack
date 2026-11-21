package com.ouroboros.statistics.reporting.dev.ui.provider;

import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsReportingUIModelResourceTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String RESOURCE_BASE = "META-INF/ouroboros/ui-model/dev/{appName}/";
  private static final Map<String, String> ROUTES = Map.of(
      "dataset.ui-model.json", "/dev/{appName}/dataset",
      "dataset/add.ui-model.json", "/dev/{appName}/dataset/add",
      "dataset/{datasetName}.ui-model.json", "/dev/{appName}/dataset/{datasetName}",
      "chart.ui-model.json", "/dev/{appName}/chart",
      "chart/add.ui-model.json", "/dev/{appName}/chart/add",
      "chart/{fullName}.ui-model.json", "/dev/{appName}/chart/{fullName}");

  @Test
  void shouldRegisterEveryAuthoringRouteAsAnAmisUiModel() throws Exception {
    StatisticsReportingUISchemaProvider schemaProvider = new StatisticsReportingUISchemaProvider();

    for (var route : ROUTES.entrySet()) {
      JsonNode model = readModel(route.getKey());

      assertEquals(route.getValue(), model.path("path").asText(), route.getKey());
      assertEquals("amis", model.path("type").asText(), route.getKey());
      assertTrue(schemaProvider.getUISchema(route.getValue()).isPresent(), route.getKey());
    }
  }

  private JsonNode readModel(String name) throws Exception {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(RESOURCE_BASE + name)) {
      assertTrue(input != null, "UI model resource should exist: " + name);
      return OBJECT_MAPPER.readTree(input);
    }
  }
}
