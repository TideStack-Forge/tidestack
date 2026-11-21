package com.ouroboros.configuration.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigurationWorkbenchSchemaResourceTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void configurationManagementSchema_usesWorkbenchTreeAndGroupPanelOnly() {
    JsonNode schema = readJson("META-INF/ouroboros/metadata/system-setting/configuration-management.ui-schema.json");

    assertText(schema, "type", "page");

    JsonNode form = findFirstDescendantByFieldValue(schema, "id", "configurationWorkbenchPage");
    assertNotNull(form);
    assertText(form, "type", "form");
    assertText(requiredNode(form, "data"), "selectedWorkbenchNode", "");
    assertText(requiredNode(form, "data"), "selectedPath", "");

    JsonNode tree = findFirstDescendantByFieldValue(schema, "id", "configurationWorkbenchTree");
    assertNotNull(tree);
    assertText(tree, "type", "input-tree");
    assertText(tree, "name", "selectedWorkbenchNode");
    assertText(requiredNode(tree, "source"), "method", "get");
    assertText(requiredNode(tree, "source"), "url", "/api/sys/configuration/workbench/tree");
    assertText(requiredNode(requiredNode(tree, "source"), "responseData"), "items", "${items}");

    JsonNode treeChange = requiredNode(requiredNode(tree, "onEvent"), "change");
    assertArraySize(treeChange, "actions", 1);
    assertBroadcastAction(child(treeChange, "actions", 0));

    JsonNode changeEvent = requiredNode(requiredNode(form, "onEvent"), "configurationNodeChange");
    assertArraySize(changeEvent, "actions", 2);
    assertSetValueAction(child(changeEvent, "actions", 0));
    assertReloadAction(child(changeEvent, "actions", 1), null, "configurationWorkbenchGroupPanelService");

    JsonNode groupPanelService = findFirstDescendantByFieldValue(schema, "id", "configurationWorkbenchGroupPanelService");
    assertNotNull(groupPanelService);
    assertText(groupPanelService, "type", "service");
    assertBoolean(groupPanelService, "initFetch", true);
    assertText(requiredNode(groupPanelService, "api"), "method", "get");
    assertText(requiredNode(groupPanelService, "api"), "url", "/api/sys/configuration/workbench/group?path=${selectedPath}");
    assertText(requiredNode(groupPanelService, "body"), "type", "ui-schema");
    assertText(requiredNode(groupPanelService, "body"), "schemaId", "/system-setting/configuration-management/group-panel");
  }

  @Test
  public void groupAndItemPanelSchemas_useInlineFormsAndDialogDetail() {
    JsonNode groupPanel = readJson("META-INF/ouroboros/metadata/system-setting/configuration-management/group-panel.ui-schema.json");
    JsonNode groupSection = readJson("META-INF/ouroboros/metadata/system-setting/configuration-management/group-section.ui-schema.json");
    JsonNode itemPanel = readJson("META-INF/ouroboros/metadata/system-setting/configuration-management/item-panel.ui-schema.json");

    assertText(groupPanel, "type", "form");
    assertText(requiredNode(groupPanel, "api"), "method", "put");
    assertText(requiredNode(groupPanel, "api"), "url", "/api/sys/configuration");
    assertTrue(requiredNode(groupPanel, "api").path("requestAdaptor").isTextual());
    assertTrue(requiredNode(groupPanel, "api").path("requestAdaptor").asText().trim().length() > 0);

    JsonNode submitReload = requiredNode(requiredNode(groupPanel, "onEvent"), "submitSucc");
    assertArraySize(submitReload, "actions", 2);
    assertReloadAction(child(submitReload, "actions", 0), "configurationWorkbenchTree", null);
    assertReloadAction(child(submitReload, "actions", 1), null, "configurationWorkbenchGroupPanelService");

    JsonNode groupSectionSchema = findFirstDescendantByFieldValue(groupPanel, "schemaId", "/system-setting/configuration-management/group-section");
    assertNotNull(groupSectionSchema);
    assertText(groupSectionSchema, "type", "ui-schema");

    JsonNode buttonToolbar = findFirstDescendantByFieldValue(groupPanel, "type", "button-toolbar");
    assertNotNull(buttonToolbar);
    JsonNode submitButton = findFirstDescendantByFieldValue(buttonToolbar, "type", "submit");
    assertNotNull(submitButton);

    JsonNode itemsCombo = findFirstDescendantByFieldValue(groupSection, "name", "items");
    assertNotNull(itemsCombo);
    assertText(itemsCombo, "type", "combo");
    JsonNode editorSchema = findFirstDescendantByFieldValue(itemsCombo, "type", "amis-schema");
    assertNotNull(editorSchema);
    assertText(editorSchema, "source", "$editorSchema");

    JsonNode detailButton = findFirstDescendantByFieldValue(itemsCombo, "actionType", "dialog");
    assertNotNull(detailButton);
    JsonNode detailDialog = requiredNode(detailButton, "dialog");
    assertText(detailDialog, "type", "dialog");
    JsonNode detailService = requiredNode(detailDialog, "body");
    assertText(requiredNode(detailService, "api"), "method", "get");
    assertText(requiredNode(detailService, "api"), "url", "/api/sys/configuration/workbench/item/${key}");
    assertText(requiredNode(detailService, "body"), "type", "ui-schema");
    assertText(requiredNode(detailService, "body"), "schemaId", "/system-setting/configuration-management/item-panel");

    JsonNode childGroupsCombo = findFirstDescendantByFieldValue(groupSection, "name", "childGroups");
    assertNotNull(childGroupsCombo);
    assertText(childGroupsCombo, "type", "combo");
    JsonNode childGroupSection = findFirstDescendantByFieldValue(childGroupsCombo, "schemaId", "/system-setting/configuration-management/group-section");
    assertNotNull(childGroupSection);
    assertText(childGroupSection, "type", "ui-schema");

    List<JsonNode> propertyBlocks = findDescendantsByFieldValue(itemPanel, "type", "property");
    assertEquals(2, propertyBlocks.size());

    JsonNode basicInfo = propertyBlocks.get(0);
    assertArraySize(basicInfo, "items", 9);
    assertFieldReference(child(basicInfo, "items", 0), "content", "groupPath");
    assertFieldReference(child(basicInfo, "items", 1), "content", "metadata.valueType");
    assertFieldReference(child(basicInfo, "items", 2), "content", "metadata.defaultValue");
    assertFieldReference(child(basicInfo, "items", 3), "content", "metadata.scope");
    assertFieldReference(child(basicInfo, "items", 4), "content", "metadata.modifiable");
    assertFieldReference(child(basicInfo, "items", 5), "content", "metadata.nullable");
    assertFieldReference(child(basicInfo, "items", 6), "content", "metadata.obsolete");
    assertFieldReference(child(basicInfo, "items", 7), "content", "metadata.obsoleteCause");
    assertFieldReference(child(basicInfo, "items", 8), "content", "metadata.description");

    JsonNode recentInfo = propertyBlocks.get(1);
    assertArraySize(recentInfo, "items", 2);
    JsonNode updateTimeItem = child(recentInfo, "items", 0);
    assertText(requiredNode(updateTimeItem, "content"), "type", "static-datetime");
    assertText(requiredNode(updateTimeItem, "content"), "name", "configValue.updateAt");
    assertFieldReference(child(recentInfo, "items", 1), "content", "configValue.updateBy");
  }

  @Test
  public void configurationManagementAuthority_includesWorkbenchApis() {
    JsonNode authority = readJson("META-INF/ouroboros/metadata/configurationManagement.authority.json");

    assertText(authority, "fullName", "systemSetting.configurationManagement");

    JsonNode apis = requiredNode(authority, "apis");
    assertTrue(apis.isArray());
    List<String> apiValues = textValues(apis);
    assertEquals(6, apiValues.size());
    assertTrue(apiValues.contains("GET /api/sys/configuration"));
    assertTrue(apiValues.contains("PUT /api/sys/configuration"));
    assertTrue(apiValues.contains("GET /api/sys/configuration/value-type"));
    assertTrue(apiValues.contains("GET /api/sys/configuration/workbench/tree"));
    assertTrue(apiValues.contains("GET /api/sys/configuration/workbench/group"));
    assertTrue(apiValues.contains("GET /api/sys/configuration/workbench/item/{key}"));
    assertFalse(apiValues.contains("GET /api/approval-workflow/engine-adapters"));
    assertFalse(apiValues.contains(
        "GET /api/approval-workflow/engine-adapters/{adapterId}/connection-schema"));

    JsonNode uiViews = requiredNode(authority, "uiViews");
    assertArraySize(authority, "uiViews", 1);
    assertNodeText(uiViews.get(0), "/system-setting/configuration-management");

    JsonNode menuItems = requiredNode(authority, "menuItems");
    assertArraySize(authority, "menuItems", 1);
    assertNodeText(menuItems.get(0), "systemSetting.configurationManagement");
  }

  private JsonNode readJson(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull("classLoader should exist", classLoader);

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull("resource should exist: " + resourcePath, inputStream);
      JsonNode root = OBJECT_MAPPER.readTree(inputStream);
      if (resourcePath.endsWith(".ui-schema.json")) {
        return requiredNode(root, "schema");
      }
      return root;
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }

  private JsonNode requiredNode(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    assertNotNull("missing field: " + fieldName, field);
    return field;
  }

  private JsonNode child(JsonNode arrayNode, String fieldName, int index) {
    JsonNode field = requiredNode(arrayNode, fieldName);
    assertTrue("expected array field: " + fieldName, field.isArray());
    assertTrue("array field " + fieldName + " should contain index " + index, field.size() > index);
    JsonNode child = field.get(index);
    assertNotNull("missing array item " + index + " for field: " + fieldName, child);
    return child;
  }

  private void assertArraySize(JsonNode node, String fieldName, int expectedSize) {
    JsonNode arrayNode = requiredNode(node, fieldName);
    assertTrue("expected array field: " + fieldName, arrayNode.isArray());
    assertEquals(expectedSize, arrayNode.size());
  }

  private void assertText(JsonNode node, String fieldName, String expected) {
    JsonNode field = requiredNode(node, fieldName);
    assertEquals(expected, field.asText());
  }

  private void assertNodeText(JsonNode node, String expected) {
    assertNotNull(node);
    assertEquals(expected, node.asText());
  }

  private void assertBoolean(JsonNode node, String fieldName, boolean expected) {
    JsonNode field = requiredNode(node, fieldName);
    assertEquals(expected, field.asBoolean());
  }

  private void assertReloadAction(JsonNode action, String expectedComponentName, String expectedComponentId) {
    assertText(action, "actionType", "reload");
    if (expectedComponentName != null) {
      assertText(action, "componentName", expectedComponentName);
    }
    if (expectedComponentId != null) {
      assertText(action, "componentId", expectedComponentId);
    }
  }

  private void assertSetValueAction(JsonNode action) {
    assertText(action, "actionType", "setValue");
    JsonNode value = requiredNode(requiredNode(action, "args"), "value");
    assertTemplateReferences(value, "selectedWorkbenchNode", "nodeId");
    assertTemplateReferences(value, "selectedPath", "path");
  }

  private void assertBroadcastAction(JsonNode action) {
    assertText(action, "actionType", "broadcast");
    JsonNode args = requiredNode(action, "args");
    assertText(args, "eventName", "configurationNodeChange");
    JsonNode data = requiredNode(action, "data");
    assertTemplateReferences(data, "nodeId", "event.data.value");
    assertTemplateReferences(data, "path", "item.path");
  }

  private void assertTemplateReferences(JsonNode node, String fieldName, String expectedReference) {
    String expression = requiredNode(node, fieldName).asText().trim();
    assertTrue("expected template expression but found: " + expression, expression.startsWith("${") && expression.endsWith("}"));

    String normalized = expression.substring(2, expression.length() - 1).trim();
    int fallbackIndex = normalized.indexOf("||");
    if (fallbackIndex >= 0) {
      normalized = normalized.substring(0, fallbackIndex).trim();
    }
    int ternaryIndex = normalized.indexOf('?');
    if (ternaryIndex >= 0) {
      normalized = normalized.substring(0, ternaryIndex).trim();
    }
    if (normalized.startsWith("(")) {
      normalized = normalized.substring(1).trim();
    }
    if (normalized.endsWith(")")) {
      normalized = normalized.substring(0, normalized.length() - 1).trim();
    }

    assertTrue("expected template to reference " + expectedReference + " but was " + expression,
        normalized.startsWith(expectedReference));
  }

  private void assertFieldReference(JsonNode node, String fieldName, String expectedReference) {
    String expression = requiredNode(node, fieldName).asText();
    assertTrue("expected field reference " + expectedReference + " but was " + expression, expression.contains(expectedReference));
    assertTrue("expected semantic expression wrapper for " + expectedReference + " but was " + expression,
        expression.contains("${"));
  }

  private List<JsonNode> findDescendantsByFieldValue(JsonNode node, String fieldName, String expectedValue) {
    List<JsonNode> result = new ArrayList<JsonNode>();
    collectDescendantsByFieldValue(node, fieldName, expectedValue, result);
    return result;
  }

  private void collectDescendantsByFieldValue(JsonNode node, String fieldName, String expectedValue, List<JsonNode> result) {
    if (node == null) {
      return;
    }
    if (expectedValue.equals(node.path(fieldName).asText())) {
      result.add(node);
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        collectDescendantsByFieldValue(fields.next().getValue(), fieldName, expectedValue, result);
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        collectDescendantsByFieldValue(child, fieldName, expectedValue, result);
      }
    }
  }

  private JsonNode findFirstDescendantByFieldValue(JsonNode node, String fieldName, String expectedValue) {
    if (node == null) {
      return null;
    }
    if (expectedValue.equals(node.path(fieldName).asText())) {
      return node;
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        JsonNode match = findFirstDescendantByFieldValue(fields.next().getValue(), fieldName, expectedValue);
        if (match != null) {
          return match;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        JsonNode match = findFirstDescendantByFieldValue(child, fieldName, expectedValue);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }

  private List<String> textValues(JsonNode arrayNode) {
    List<String> values = new ArrayList<String>();
    for (JsonNode node : arrayNode) {
      values.add(node.asText());
    }
    return values;
  }
}
