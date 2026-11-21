package com.ouroboros.fieldtype.dev.resource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class FieldTypeManagementUiSchemaTest {

  @Test
  void listPageShowsRicherFieldTypeMetadata() {
    Map<?, ?> listSchema = readResourceJson("META-INF/ouroboros/metadata/dev/{appName}/field-types.ui-schema.json");

    assertContainsValueForKey(listSchema, "name", "label");
    assertContainsValueForKey(listSchema, "name", "order");
    assertContainsValueForKey(listSchema, "name", "isPrimaryKeyType");
    assertContainsValueForKey(listSchema, "name", "ui.input.type");
    assertContainsValueForKey(listSchema, "name", "ui.display.type");
    assertContainsValueForKey(listSchema, "tpl", "${paramsUiSchema ? paramsUiSchema.length : 0}");
  }

  @Test
  void addPageUsesStructuredEditorsBoundToSchemaObject() {
    Map<?, ?> addSchema = readResourceJson("META-INF/ouroboros/metadata/dev/{appName}/field-types/add.ui-schema.json");

    assertContainsValueForKey(addSchema, "name", "schema.name");
    assertContainsValueForKey(addSchema, "name", "schema.label");
    assertContainsValueForKey(addSchema, "name", "schema.dataType");
    assertContainsValueForKey(addSchema, "name", "schema.validations");
    assertContainsValueForKey(addSchema, "name", "schema.validationErrors");
    assertContainsValueForKey(addSchema, "name", "schema.paramsUiSchema");
    assertContainsValueForKey(addSchema, "name", "schema.ui.input");
    assertContainsValueForKey(addSchema, "name", "schema.ui.display");
    assertContainsValueForKey(addSchema, "name", "schema");
    assertContainsValueForKey(addSchema, "type", "json-data-editor");
    assertContainsValueForKey(addSchema, "type", "ui-schema-editor");
    assertContainsValueForKey(addSchema, "multiple", true);
    assertContainsValueForKey(addSchema, "&", "${schema|toJson}");
    assertContainsValueForKey(addSchema, "title", "完整定义");
    assertContainsValueForKey(addSchema, "description", "直接编辑完整 JSON 定义，未在结构化表单中展示的字段也会被保留");
  }

  @Test
  void editPageLoadsFullSchemaAndUsesStructuredEditors() {
    Map<?, ?> editSchema = readResourceJson("META-INF/ouroboros/metadata/dev/{appName}/field-types/edit.ui-schema.json");

    assertContainsNestedMapEntry(editSchema, "responseData", "schema", "$$");
    assertContainsValueForKey(editSchema, "name", "schema.name");
    assertContainsValueForKey(editSchema, "name", "schema.paramsUiSchema");
    assertContainsValueForKey(editSchema, "name", "schema.ui.input");
    assertContainsValueForKey(editSchema, "name", "schema.ui.display");
    assertContainsValueForKey(editSchema, "type", "ui-schema-editor");
    assertContainsValueForKey(editSchema, "type", "json-data-editor");
    assertContainsValueForKey(editSchema, "&", "${schema|toJson}");
    assertContainsValueForKey(editSchema, "title", "完整定义");
    assertContainsValueForKey(editSchema, "description", "直接编辑完整 JSON 定义，未在结构化表单中展示的字段也会被保留");
  }

  @Test
  void detailPageUsesStaticStructuredPreview() {
    Map<?, ?> detailSchema = readResourceJson("META-INF/ouroboros/metadata/dev/{appName}/field-types/detail.ui-schema.json");

    assertContainsNestedMapEntry(detailSchema, "responseData", "schema", "$$");
    assertContainsValueForKey(detailSchema, "name", "schema.paramsUiSchema");
    assertContainsValueForKey(detailSchema, "name", "schema.ui.input");
    assertContainsValueForKey(detailSchema, "name", "schema.ui.display");
    assertContainsValueForKey(detailSchema, "type", "ui-schema-editor");
    assertContainsValueForKey(detailSchema, "type", "json-data-editor");
    assertContainsValueForKey(detailSchema, "static", true);
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

  private Map<?, ?> readResourceJson(String resourcePath) {
    return asMap(Json.toMap(readResource(resourcePath)).get("schema"));
  }

  private Map<?, ?> asMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return map;
    }
    fail("expected map but was " + value);
    return null;
  }

  private void assertContainsValueForKey(Object node, String key, Object expectedValue) {
    assertTrue(
        containsValueForKey(node, key, expectedValue),
        () -> "expected to find key `" + key + "` with value `" + expectedValue + "`"
    );
  }

  private void assertContainsNestedMapEntry(Object node, String parentKey, String childKey, Object expectedValue) {
    assertTrue(
        containsNestedMapEntry(node, parentKey, childKey, expectedValue),
        () -> "expected to find `" + parentKey + "." + childKey + "` with value `" + expectedValue + "`"
    );
  }

  private boolean containsValueForKey(Object node, String key, Object expectedValue) {
    if (node instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) node;
      if (map.containsKey(key) && Objects.equals(map.get(key), expectedValue)) {
        return true;
      }
      return map.values().stream().anyMatch(value -> containsValueForKey(value, key, expectedValue));
    }
    if (node instanceof List) {
      return ((List<?>) node).stream().anyMatch(value -> containsValueForKey(value, key, expectedValue));
    }
    return false;
  }

  private boolean containsNestedMapEntry(Object node, String parentKey, String childKey, Object expectedValue) {
    if (node instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) node;
      Object child = map.get(parentKey);
      if (child instanceof Map && Objects.equals(((Map<?, ?>) child).get(childKey), expectedValue)) {
        return true;
      }
      return map.values().stream().anyMatch(value -> containsNestedMapEntry(value, parentKey, childKey, expectedValue));
    }
    if (node instanceof List) {
      return ((List<?>) node).stream().anyMatch(value -> containsNestedMapEntry(value, parentKey, childKey, expectedValue));
    }
    return false;
  }
}
