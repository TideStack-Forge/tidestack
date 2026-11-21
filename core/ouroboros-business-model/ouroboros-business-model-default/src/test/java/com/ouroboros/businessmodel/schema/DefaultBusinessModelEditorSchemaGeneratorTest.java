package com.ouroboros.businessmodel.schema;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DefaultBusinessModelEditorSchemaGeneratorTest {

  @Test
  void shouldPreserveAppNamePlaceholderInGeneratedEditorSchema() {
    DefaultBusinessModelEditorSchemaGenerator generator = new DefaultBusinessModelEditorSchemaGenerator();

    String schemaText = String.valueOf(generator.generate().getProps());

    assertTrue(schemaText.contains("${appName}"));
  }

  @Test
  void shouldRenderDefaultWorkflowBindingAsCompactDesignTimeContract() {
    DefaultBusinessModelEditorSchemaGenerator generator = new DefaultBusinessModelEditorSchemaGenerator();

    String schemaJson = Json.toJsonString(generator.generate());
    Map<String, Object> schema = Json.toMap(schemaJson);

    assertTrue(schemaJson.contains("\"type\":\"input-group\""));
    assertTrue(schemaJson.contains("\"label\":\"绑定流程标识\""));
    assertTrue(schemaJson.contains("\"name\":\"workflow.bindings[0].processId\""));
    assertTrue(schemaJson.contains("\"label\":\"创建流程\""));
    assertTrue(schemaJson.contains("\"label\":\"选择现有流程\""));
    assertTrue(schemaJson.contains("\"label\":\"打开流程编辑器\""));
    assertTrue(schemaJson.contains("\"title\":\"流程绑定\""));
    assertTrue(schemaJson.contains("\"name\":\"create-default-approval-workflow-form\""));
    assertTrue(schemaJson.contains("\"name\":\"workflowDefinitionName\""));
    assertTrue(schemaJson.contains("\"name\":\"workflowDefinitionTitle\""));
    assertTrue(schemaJson.contains("\"name\":\"workflowDefinitionDescription\""));
    assertTrue(schemaJson.contains("\"className\":\"mt-4 text-right\""));
    assertTrue(schemaJson.contains("\"formatVersion\":1"));
    assertTrue(schemaJson.contains("\"formType\":\"business-model\""));
    assertTrue(schemaJson.contains("\"formId\":\"${formId}\""));
    assertTrue(schemaJson.contains("\"type\":\"StartNode\""));
    assertTrue(schemaJson.contains("\"type\":\"ApprovalNode\""));
    assertTrue(schemaJson.contains("\"type\":\"EndNode\""));
    assertTrue(schemaJson.contains("\"id\":\"${workflowDefinitionName}-end\""));
    assertTrue(schemaJson.contains("\"responseData\":{\"items\":{\"$items\":{\"&\":\"$model\""));
    assertTrue(schemaJson.contains("\"type\":\"operation\""));
    assertTrue(schemaJson.contains("\"label\":\"选择\""));
    assertTrue(schemaJson.contains("\"workflow.isEnabled\":true"));
    assertTrue(schemaJson.contains("\"workflow.bindings[0].processId\":\"${workflowDefinitionName}\""));
    assertTrue(schemaJson.contains("\"workflow.bindings[0].processId\":\"${name || id}\""));
    assertTrue(schemaJson.contains("\"definitionId\":\"${name || id}\""));
    assertTrue(schemaJson.contains("\"definitionName\":\"${title || name || id}\""));
    assertTrue(schemaJson.contains("\"actionType\":\"closeDialog\""));
    assertTrue(schemaJson.contains("\"label\":\"状态字段\""));
    assertTrue(schemaJson.contains("\"title\":\"流程状态映射\""));
    assertTrue(schemaJson.contains("/dev/${appName}/approval-workflow-definition/${workflowDefinitionName}"));
    assertTrue(schemaJson.contains("/dev/${appName}/approval-workflow-definition/${workflow.bindings[0].processId}"));
    assertBusinessModelFormWritebacksDoNotCopyCurrentContext(schema);
    assertFalse(schemaJson.contains("\"type\":\"picker\""));
    assertFalse(schemaJson.contains("\"embed\":true"));
    assertFalse(schemaJson.contains("\"name\":\"selectedWorkflowDefinition\""));
    assertFalse(schemaJson.contains("SelectResult.value[0]"));
    assertFalse(schemaJson.contains("\"itemAction\""));
    assertFalse(schemaJson.contains("\"name\":\"definitionId\""));
    assertFalse(schemaJson.contains("\"title\":\"默认流程绑定\""));
    assertFalse(schemaJson.contains("通过显式 processId"));
    assertFalse(schemaJson.contains("默认流程定义资产"));
    assertFalse(schemaJson.contains("最终何时生效"));
    assertFalse(schemaJson.contains("创建前先明确指定 name"));
    assertFalse(schemaJson.contains("\"type\":\"通知节点\""));
    assertFalse(schemaJson.contains("绑定所选流程"));
    assertFalse(schemaJson.contains("替换绑定流程"));
    assertFalse(schemaJson.contains("绑定并打开编辑器"));
    assertFalse(schemaJson.contains("替换并打开编辑器"));
    assertFalse(schemaJson.contains("当前状态："));
    assertFalse(schemaJson.contains("\"title\":\"状态投影摘要\""));
    assertFalse(schemaJson.contains("\"title\":\"运行时投影与入口\""));
  }

  @Test
  void shouldNotExposeCustomHostSystemTabOpenMode() {
    DefaultBusinessModelEditorSchemaGenerator generator = new DefaultBusinessModelEditorSchemaGenerator();

    String schemaJson = Json.toJsonString(generator.generate());

    assertFalse(schemaJson.contains("宿主系统标签"));
    assertFalse(schemaJson.contains("\"openPage\""));
  }

  @Test
  void shouldExposeDynamicActionEventAndSubscriptionEditors() {
    DefaultBusinessModelEditorSchemaGenerator generator = new DefaultBusinessModelEditorSchemaGenerator();

    String schemaJson = Json.toJsonString(generator.generate());

    assertTrue(schemaJson.contains("\"title\":\"行为契约\""));
    Map<?, ?> catalog = findBehaviorCatalog(Json.toMap(schemaJson));
    assertNotNull(catalog);
    assertEquals("behaviorCatalog", catalog.get("name"));
    Map<?, ?> definitions = requireMap(catalog.get("definitionSchemas"));
    for (String kind : List.of("action", "event", "subscription")) {
      Map<?, ?> definition = requireMap(definitions.get(kind));
      assertEquals("array", definition.get("type"));
      Map<?, ?> item = requireMap(definition.get("items"));
      assertTrue(requireList(item.get("required")).contains("name"));
      assertTrue(requireMap(item.get("properties")).containsKey("name"));
    }
    assertTrue(requireMap(requireMap(requireMap(definitions.get("action")).get("items")).get("properties")).containsKey("binding"));
    assertTrue(requireMap(requireMap(requireMap(definitions.get("event")).get("items")).get("properties")).containsKey("payloadSchema"));
    assertTrue(requireMap(requireMap(requireMap(definitions.get("subscription")).get("items")).get("properties")).containsKey("target"));
  }

  @Test
  void shouldKeepWorkflowSubViewsInsideSidebarTabs() {
    DefaultBusinessModelEditorSchemaGenerator generator = new DefaultBusinessModelEditorSchemaGenerator();

    Map<String, Object> schema = Json.toMap(Json.toJsonString(generator.generate()));
    Map<?, ?> mainTabs = requireMap(requireList(schema.get("body")).get(1));
    Map<?, ?> workflowTab = findTab(requireList(mainTabs.get("tabs")), "工作流配置");
    Map<?, ?> workflowPanel = requireMap(requireList(workflowTab.get("body")).get(0));
    List<?> workflowPanelBody = requireList(workflowPanel.get("body"));
    Map<?, ?> workflowSidebarTabs = requireMap(workflowPanelBody.get(1));
    List<?> workflowSubTabs = requireList(workflowSidebarTabs.get("tabs"));

    assertEquals(2, workflowPanelBody.size());
    assertEquals("tabs", workflowSidebarTabs.get("type"));
    assertEquals(3, workflowSubTabs.size());
    Map<?, ?> bindingTab = findTab(workflowSubTabs, "流程绑定");
    Map<?, ?> startTab = findTab(workflowSubTabs, "流程发起");
    Map<?, ?> flowTab = findTab(workflowSubTabs, "流程处理");

    assertSidebarTab(bindingTab);
    assertSidebarTab(startTab);
    assertSidebarTab(flowTab);
    assertEquals("fas fa-link", bindingTab.get("icon"));
    assertEquals("fas fa-play", startTab.get("icon"));
    assertEquals("fas fa-tasks", flowTab.get("icon"));
  }

  @Test
  void shouldRenderBaseInfoCollapseInOneHeaderRow() {
    DefaultBusinessModelEditorSchemaGenerator generator = new DefaultBusinessModelEditorSchemaGenerator();

    Map<String, Object> schema = Json.toMap(Json.toJsonString(generator.generate()));
    Map<?, ?> baseInfoPanel = requireMap(requireList(schema.get("body")).get(0));
    List<?> baseInfoBody = requireList(baseInfoPanel.get("body"));
    Map<?, ?> baseInfoCollapse = requireMap(baseInfoBody.get(0));

    assertEquals("collapse", baseInfoCollapse.get("type"));
    assertEquals(false, baseInfoCollapse.get("showArrow"));
    assertEquals(true, baseInfoCollapse.get("collapsed"));
    assertTrue(baseInfoCollapse.containsKey("header"));
    assertTrue(baseInfoCollapse.containsKey("collapseHeader"));
    assertFalse(baseInfoCollapse.containsKey("titlePosition"));
    assertEquals("namespace", requireMap(baseInfoBody.get(1)).get("name"));
  }

  private static Map<?, ?> findBehaviorCatalog(Object value) {
    if (value instanceof Map<?, ?> map) {
      if ("business-behavior-catalog".equals(map.get("type"))) { return map; }
      for (Object child : map.values()) {
        Map<?, ?> found = findBehaviorCatalog(child);
        if (found != null) { return found; }
      }
    } else if (value instanceof List<?> list) {
      for (Object child : list) {
        Map<?, ?> found = findBehaviorCatalog(child);
        if (found != null) { return found; }
      }
    }
    return null;
  }

  private static void assertSidebarTab(Map<?, ?> tab) {
    assertTrue(tab.containsKey("tab"));
    assertFalse(tab.containsKey("body"));
    assertTrue(requireList(tab.get("tab")).size() > 0);
  }

  private static Map<?, ?> requireMap(Object value) {
    assertTrue(value instanceof Map<?, ?>);
    return (Map<?, ?>) value;
  }

  private static void assertBusinessModelFormWritebacksDoNotCopyCurrentContext(Object node) {
    if (node instanceof Map<?, ?> map) {
      if ("edit-business-model-form".equals(map.get("componentName"))) {
        Map<?, ?> args = requireMap(map.get("args"));
        Map<?, ?> value = requireMap(args.get("value"));
        assertFalse(value.containsKey("&"));
      }
      for (Object child : map.values()) {
        assertBusinessModelFormWritebacksDoNotCopyCurrentContext(child);
      }
      return;
    }
    if (node instanceof List<?> list) {
      for (Object child : list) {
        assertBusinessModelFormWritebacksDoNotCopyCurrentContext(child);
      }
    }
  }

  private static List<?> requireList(Object value) {
    assertTrue(value instanceof List<?>);
    return (List<?>) value;
  }

  private static Map<?, ?> findTab(List<?> tabs, String title) {
    for (Object tabObject : tabs) {
      Map<?, ?> tab = requireMap(tabObject);
      if (title.equals(tab.get("title"))) {
        return tab;
      }
    }
    return fail("missing tab: " + title);
  }
}
