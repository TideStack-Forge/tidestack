package com.ouroboros.businessmodel.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

class DashboardEditorSchemaProviderTest {

  @Test
  void shouldSupportDashboardModelType() {
    DashboardEditorSchemaProvider provider = new DashboardEditorSchemaProvider();

    assertTrue(provider.support("dashboard"));
  }

  @Test
  void shouldGenerateDashboardRendererWithChartSelectOnly() {
    DashboardEditorSchemaProvider provider = new DashboardEditorSchemaProvider();

    String schemaText = String.valueOf(provider.generateBaseSchema(
        Collections.singletonMap("appName", "demo-app")).getProps());

    assertTrue(schemaText.contains("type=dashboard"));
    assertTrue(schemaText.contains("name=chart"));
    assertTrue(schemaText.contains("type=select"));
    assertTrue(schemaText.contains("name=chartName"));
    assertTrue(schemaText.contains("/api/dev/${appName}/chart/options"));
    assertTrue(schemaText.contains("name=layout.cols"));
    assertTrue(schemaText.contains("name=layout.rowHeight"));
    assertTrue(schemaText.contains("拖拽图表组件完成布局，保存后写入仪表板业务模型。"));
    assertFalse(schemaText.contains("global-filter"));
    assertFalse(schemaText.contains("json-editor"));
  }

  @Test
  void shouldRenderDashboardBaseInfoAsCollapsedSingleHeaderRow() {
    DashboardEditorSchemaProvider provider = new DashboardEditorSchemaProvider();

    Map<String, Object> schema = Json.toMap(Json.toJsonString(provider.generateBaseSchema(
        Collections.singletonMap("appName", "demo-app"))));
    Map<?, ?> panel = requireMap(requireList(schema.get("body")).get(0));
    Map<?, ?> collapse = requireMap(requireList(panel.get("body")).get(0));

    assertEquals("collapse", collapse.get("type"));
    assertEquals(false, collapse.get("showArrow"));
    assertEquals(true, collapse.get("collapsed"));
    assertEquals(true, collapse.get("mountOnEnter"));
    assertEquals(true, collapse.get("unmountOnExit"));
    assertTrue(collapse.containsKey("header"));
    assertTrue(collapse.containsKey("collapseHeader"));
    assertEquals("namespace", requireMap(requireList(schema.get("body")).get(1)).get("name"));
  }

  private static List<?> requireList(Object value) {
    assertTrue(value instanceof List<?>);
    return (List<?>) value;
  }

  private static Map<?, ?> requireMap(Object value) {
    assertTrue(value instanceof Map<?, ?>);
    return (Map<?, ?>) value;
  }
}
