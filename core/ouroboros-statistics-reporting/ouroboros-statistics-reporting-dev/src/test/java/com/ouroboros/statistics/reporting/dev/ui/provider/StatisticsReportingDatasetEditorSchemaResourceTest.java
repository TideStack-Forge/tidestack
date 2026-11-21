package com.ouroboros.statistics.reporting.dev.ui.provider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsReportingDatasetEditorSchemaResourceTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void datasetEditorSchemaShouldBindMetadataOnlyEditorAndPreviewAction() {
    String schema = readResource(
        "META-INF/ouroboros/ui-schema/dev/{appName}/dataset/{datasetName}.json");

    assertTrue(schema.contains("编辑数据集"));
    assertTrue(schema.contains("dataset-sql-editor"));
    assertFalse(schema.contains("statistics-query-definition-editor"));
    assertTrue(schema.contains("datasetEditorForm"));
    assertTrue(schema.contains("/api/dev/${appName}/dataset/${datasetName}/editor"));
    assertTrue(schema.contains("/api/dev/${appName}/dataset/${datasetName}"));
    assertTrue(schema.contains("/api/dev/${appName}/dataset/infer-schema"));
    assertTrue(schema.contains("/api/dev/${appName}/dataset/preview"));
    JsonNode preview = findNodeByType(parseJson(schema), "dataset-preview");
    assertNotNull(preview, "dataset preview should exist");
    JsonNode previewDataset = preview.path("api").path("data").path("dataset");
    assertTrue(previewDataset.isObject(), "preview should send a metadata-only dataset object");
    assertEquals("$dataset.name", previewDataset.path("name").asText());
    assertEquals("$dataset.queryDefinition.sql",
        previewDataset.path("queryDefinition").path("sql").asText());
    assertEquals("$dataset.tags", previewDataset.path("tags").asText());
    assertFalse(previewDataset.has("jdbcConnection"));
    assertFalse(previewDataset.has("fixture"));
    assertTrue(schema.contains("执行并推导字段"));
    assertTrue(schema.contains("参数定义"));
    assertTrue(schema.contains("输出字段 Schema"));
    assertTrue(schema.contains("\"type\": \"input-table\""));
    assertTrue(schema.contains("\"name\": \"dataset.parameters\""));
    assertTrue(schema.contains("\"name\": \"dataset.schema.fields\""));
    assertFieldAdvancedColumns(schema);
    assertFalse(schema.contains("fixture"));
    assertFalse(schema.contains("testDatasourceName"));
    assertFalse(schema.contains("testParameters"));
    assertFalse(schema.contains("发布检查"));
    assertFalse(schema.contains("Parameter Hints"));
    assertFalse(schema.contains("publish-check"));
    assertFalse(schema.contains("工作流"));
    assertFalse(schema.contains("Dashboard"));
    assertFalse(schema.contains("/analytics"));
    assertFalse(schema.contains("/statistics-reporting"));
  }

  @Test
  void datasetListAndAddSchemasShouldUseFlatDatasetRoutes() {
    String listSchema = readResource("META-INF/ouroboros/ui-schema/dev/{appName}/dataset.json");
    String addSchema = readResource("META-INF/ouroboros/ui-schema/dev/{appName}/dataset/add.json");

    assertEquals("/api/dev/${appName}/dataset", parseJson(listSchema).path("body").path("api").path("url").asText());
    assertTrue(listSchema.contains("/dev/${appName}/dataset/add"));
    assertTrue(listSchema.contains("/dev/${appName}/dataset/${name}"));
    assertTrue(listSchema.contains("/dev/${appName}/chart"));
    assertTrue(listSchema.contains("\"label\": \"启用\""));
    assertTrue(listSchema.contains("\"label\": \"禁用\""));
    assertTrue(listSchema.contains("\"label\": \"删除\""));
    assertTrue(listSchema.contains("\"method\": \"delete\""));
    assertTrue(listSchema.contains("?commit=true"));
    assertTrue(addSchema.contains("/api/dev/${appName}/dataset"));
    assertTrue(addSchema.contains("/dev/${appName}/dataset/${result.data.name}"));
    assertTrue(addSchema.contains("queryDefinition.datasource"));
    assertTrue(addSchema.contains("queryDefinition.sql"));
    assertTrue(addSchema.contains("\"name\": \"isEnabled\""));
    assertFalse(listSchema.contains("/analytics"));
    assertFalse(addSchema.contains("/analytics"));
    assertFalse(listSchema.contains("Dashboard"));
    assertFalse(addSchema.contains("Dashboard"));
  }

  @Test
  void chartSchemasShouldUseFlatChartRoutesAndDatasetBackedFieldSelectors() {
    String listSchema = readResource("META-INF/ouroboros/ui-schema/dev/{appName}/chart.json");
    String addSchema = readResource("META-INF/ouroboros/ui-schema/dev/{appName}/chart/add.json");
    String editSchema = readResource("META-INF/ouroboros/ui-schema/dev/{appName}/chart/{fullName}.json");

    assertEquals("/api/dev/${appName}/chart", parseJson(listSchema).path("body").path("api").path("url").asText());
    assertTrue(listSchema.contains("/dev/${appName}/chart/add"));
    assertTrue(listSchema.contains("/dev/${appName}/chart/${name}"));
    assertTrue(listSchema.contains("\"label\": \"启用\""));
    assertTrue(listSchema.contains("\"label\": \"禁用\""));
    assertTrue(listSchema.contains("\"label\": \"删除\""));
    assertTrue(listSchema.contains("\"method\": \"delete\""));
    assertTrue(listSchema.contains("?commit=true"));
    assertTrue(listSchema.contains("\"combo\": \"组合图\""));
    assertTrue(addSchema.contains("/api/dev/${appName}/chart"));
    assertTrue(addSchema.contains("/dev/${appName}/chart/${result.data.name}"));
    assertTrue(addSchema.contains("\"name\": \"description\""));
    assertTrue(addSchema.contains("\"name\": \"tags\""));
    assertTrue(addSchema.contains("\"name\": \"isEnabled\""));
    assertTrue(addSchema.contains("field-options/${datasetName}?chartType=${chartType}&param=xField"));
    assertTrue(addSchema.contains("field-options/${datasetName}?chartType=${chartType}&param=categoryField"));
    assertTrue(addSchema.contains("field-options/${datasetName}?chartType=${chartType}&param=sourceField"));
    assertTrue(addSchema.contains("field-options/${datasetName}?chartType=${chartType}&param=valueFields"));
    assertTrue(addSchema.contains("\"name\": \"params.series\""));
    assertComboSeriesColumns(addSchema);
    assertFalse(addSchema.contains("\"name\": \"params\""));
    assertTrue(editSchema.contains("/api/dev/${appName}/chart/${fullName}"));
    assertTrue(editSchema.contains("\"name\": \"datasetName\""));
    assertTrue(editSchema.contains("\"name\": \"description\""));
    assertTrue(editSchema.contains("\"name\": \"tags\""));
    assertTrue(editSchema.contains("chart-editor-form-status"));
    assertTrue(editSchema.contains("statisticsReporting.chartEditor.changed"));
    assertTrue(editSchema.contains("statisticsReporting.chartEditor.saved"));
    assertTrue(editSchema.contains("ctrl+s,command+s"));
    assertTrue(editSchema.contains("未保存的图表内容将会丢失"));
    assertTrue(editSchema.contains("visibleOn\": \"${chartType == 'pie' || chartType == 'donut'}\""));
    assertTrue(editSchema.contains("\"label\": \"桑基图\""));
    assertTrue(editSchema.contains("\"label\": \"组合图\""));
    assertTrue(editSchema.contains(
        "/api/dev/${appName}/chart/field-options/${datasetName}?chartType=${chartType}&param=xField"));
    assertTrue(editSchema.contains("\"type\": \"input-table\""));
    assertComboSeriesColumns(editSchema);
    assertTrue(editSchema.contains("/api/dev/${appName}/chart/preview"));
    assertTrue(editSchema.contains("\"type\": \"chart-runtime\""));
    JsonNode editRoot = parseJson(editSchema);
    assertNotNull(findNodeByClassName(editRoot, "statistics-chart-editor-controls"));
    assertNotNull(findNodeByClassName(editRoot, "statistics-chart-editor-canvas"));
    assertNotNull(findNodeByClassName(editRoot, "statistics-chart-editor-preview-shell"));
    assertNotNull(findNodeByClassName(editRoot, "statistics-chart-editor-preview-body"));
    assertFalse(editSchema.contains("channel-options"));
    assertFalse(editSchema.contains("fieldBindings"));
    assertFalse(listSchema.contains("/analytics"));
    assertFalse(addSchema.contains("/analytics"));
    assertFalse(editSchema.contains("/analytics"));
    assertFalse(editSchema.contains("Dashboard"));
    assertFalse(editSchema.contains("jdbc"));
    assertFalse(editSchema.contains("fixture"));
  }

  @Test
  void canonicalAuthoringResourcesShouldLiveUnderFlatPaths() {
    assertResourceExists("META-INF/ouroboros/ui-schema/dev/{appName}/dataset.json");
    assertResourceExists("META-INF/ouroboros/ui-schema/dev/{appName}/dataset/add.json");
    assertResourceExists("META-INF/ouroboros/ui-schema/dev/{appName}/dataset/{datasetName}.json");
    assertResourceExists("META-INF/ouroboros/ui-schema/dev/{appName}/chart.json");
    assertResourceExists("META-INF/ouroboros/ui-schema/dev/{appName}/chart/add.json");
    assertResourceExists("META-INF/ouroboros/ui-schema/dev/{appName}/chart/{fullName}.json");
  }

  private void assertResourceExists(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");
    assertNotNull(classLoader.getResourceAsStream(resourcePath), "resource should exist: " + resourcePath);
  }

  private String readResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream, "resource should exist: " + resourcePath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }

  private static void assertFieldAdvancedColumns(String schema) {
    JsonNode root = parseJson(schema);
    JsonNode fieldsTable = findNodeByName(root, "dataset.schema.fields");
    assertNotNull(fieldsTable, "dataset.schema.fields input-table should exist");
    assertEquals("input-table", fieldsTable.path("type").asText());

    JsonNode columns = fieldsTable.path("columns");
    assertSwitch(columns, "sortable", "可排序", false);
    assertSwitch(columns, "filterable", "可过滤", false);
    assertSwitch(columns, "exportable", "可导出", true);
    assertSwitch(columns, "visibleByDefault", "默认显示", true);
  }

  private static void assertComboSeriesColumns(String schema) {
    JsonNode root = parseJson(schema);
    JsonNode seriesTable = findNodeByName(root, "params.series");
    assertNotNull(seriesTable, "combo series input-table should exist");
    assertSwitch(seriesTable.path("columns"), "smooth", "平滑 (仅折线/面积)", false);
    assertSwitch(seriesTable.path("columns"), "stacked", "堆叠", false);
    JsonNode smooth = findNodeByName(seriesTable.path("columns"), "smooth");
    JsonNode stacked = findNodeByName(seriesTable.path("columns"), "stacked");
    assertEquals("${type == 'bar'}", smooth.path("disabledOn").asText());
    assertFalse(smooth.has("visibleOn"));
    assertFalse(smooth.has("clearValueOnHidden"));
    assertFalse(stacked.has("visibleOn"));
    assertFalse(stacked.has("clearValueOnHidden"));
  }

  private static JsonNode parseJson(String schema) {
    try {
      return OBJECT_MAPPER.readTree(schema);
    } catch (Exception error) {
      throw new AssertionError("schema should be valid JSON", error);
    }
  }

  private static JsonNode findNodeByName(JsonNode node, String name) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    if (name.equals(node.path("name").asText(null))) {
      return node;
    }
    if (node.isObject()) {
      Iterator<JsonNode> children = node.elements();
      while (children.hasNext()) {
        JsonNode found = findNodeByName(children.next(), name);
        if (found != null) {
          return found;
        }
      }
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        JsonNode found = findNodeByName(item, name);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static JsonNode findNodeByClassName(JsonNode node, String className) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    if (className.equals(node.path("className").asText(null))) {
      return node;
    }
    if (node.isObject()) {
      Iterator<JsonNode> children = node.elements();
      while (children.hasNext()) {
        JsonNode found = findNodeByClassName(children.next(), className);
        if (found != null) {
          return found;
        }
      }
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        JsonNode found = findNodeByClassName(item, className);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static JsonNode findNodeByType(JsonNode node, String type) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    if (type.equals(node.path("type").asText(null))) {
      return node;
    }
    if (node.isObject()) {
      Iterator<JsonNode> children = node.elements();
      while (children.hasNext()) {
        JsonNode found = findNodeByType(children.next(), type);
        if (found != null) {
          return found;
        }
      }
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        JsonNode found = findNodeByType(item, type);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static void assertSwitch(JsonNode container, String name, String label, boolean defaultValue) {
    JsonNode switchNode = findNodeByName(container, name);
    assertNotNull(switchNode, name + " switch should exist");
    assertEquals("switch", switchNode.path("type").asText());
    assertEquals(label, switchNode.path("label").asText());
    assertEquals(defaultValue, switchNode.path("value").asBoolean(false));
  }
}
