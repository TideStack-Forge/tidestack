package com.ouroboros.businessmodel.resource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.schema.DefaultBusinessModelEditorSchemaGenerator;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class BusinessModelFieldTypeResourceContractTest {

  private static final String PRIMARY_FIELD_TYPES_URL = "/api/app/${appName}/field-types/${isPrimaryKey ? 'primary' : 'normal'}";
  private static final String FIELD_TYPE_UI_SCHEMA_URL = "/api/app/${appName}/field-types/${type}/ui-schema";

  @Test
  void editorTemplateUsesFieldTypeEndpoints() {
    Map<String, Object> editorSchema = new DefaultBusinessModelEditorSchemaGenerator().generate().getProps();

    Map fieldsPanel = findPanelWithHeaderText(fieldsTabBody(editorSchema), "业务字段");
    assertEquals("panel", fieldsPanel.get("type"));

    List fieldsPanelBody = asList(fieldsPanel.get("body"));
    Map fieldsCombo = findMap(fieldsPanelBody, "type", "combo");
    assertEquals("combo", fieldsCombo.get("type"));
    assertEquals("fields", fieldsCombo.get("name"));

    List items = asList(fieldsCombo.get("items"));
    Map typeField = findMap(items, "name", "type");
    Map source = asMap(typeField.get("source"));
    assertEquals(PRIMARY_FIELD_TYPES_URL, source.get("url"));

    Map buttonItem = findMap(items, "actionType", "drawer");
    Map drawer = asMap(buttonItem.get("drawer"));
    Map body = asMap(drawer.get("body"));
    List drawerBody = asList(body.get("body"));
    Map serviceItem = findMap(drawerBody, "type", "service");
    Map schemaApi = asMap(serviceItem.get("schemaApi"));
    assertEquals(FIELD_TYPE_UI_SCHEMA_URL, schemaApi.get("url"));

    assertNoLegacyFieldRepositoryUrl(editorSchema);
  }

  @Test
  void importFieldsSchemaUsesFieldTypeEndpoints() {
    Map importFieldsSchema = asMap(Json.toMap(readResource(
        "META-INF/ouroboros/metadata/business-model-editor/import-fields.ui-schema.json")).get("schema"));

    assertEquals("wizard", importFieldsSchema.get("type"));

    List steps = asList(importFieldsSchema.get("steps"));
    Map checkFieldsStep = findMap(steps, "title", "检查字段");
    Map fieldsCombo = findMap(asList(checkFieldsStep.get("body")), "name", "fields");
    assertEquals("combo", fieldsCombo.get("type"));

    Map typeField = findMap(asList(fieldsCombo.get("items")), "name", "type");
    Map source = asMap(typeField.get("source"));
    assertEquals(PRIMARY_FIELD_TYPES_URL, source.get("url"));

    assertNoLegacyFieldRepositoryUrl(importFieldsSchema);
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

  private static List fieldsTabBody(Map<String, Object> editorSchema) {
    List rootBody = asList(editorSchema.get("body"));
    Map tabs = findMap(rootBody, "type", "tabs");
    List tabsList = asList(tabs.get("tabs"));
    Map fieldsTab = findMap(tabsList, "hash", "fields-n-relations");
    return asList(fieldsTab.get("body"));
  }

  private static Map findPanelWithHeaderText(List panels, String headerText) {
    for (Object panelValue : panels) {
      Map panel = asMap(panelValue);
      Object header = panel.get("header");
      if (!Map.class.isInstance(header)) {
        continue;
      }
      Map headerMap = (Map) header;
      List headerBody = asList(headerMap.get("body"));
      for (Object headerItemValue : headerBody) {
        Map headerItem = asMap(headerItemValue);
        if (Objects.equals(headerText, headerItem.get("tpl"))) {
          return panel;
        }
      }
    }
    throw new AssertionError("missing panel with header text " + headerText);
  }

  private static void assertNoLegacyFieldRepositoryUrl(Object node) {
    if (Map.class.isInstance(node)) {
      Map map = (Map) node;
      Object url = map.get("url");
      if (url instanceof String) {
        assertFalse(((String) url).contains("field-repository"), "legacy field repository url should be removed");
      }
      map.values().forEach(BusinessModelFieldTypeResourceContractTest::assertNoLegacyFieldRepositoryUrl);
      return;
    }
    if (List.class.isInstance(node)) {
      List list = (List) node;
      list.forEach(BusinessModelFieldTypeResourceContractTest::assertNoLegacyFieldRepositoryUrl);
    }
  }

  private static Map asMap(Object value) {
    if (value == null) {
      fail("expected map but was null");
    }
    if (Map.class.isInstance(value)) {
      return (Map) value;
    }
    fail("expected map but was " + value.getClass().getName());
    return null;
  }

  private static List asList(Object value) {
    if (value == null) {
      fail("expected list but was null");
    }
    if (List.class.isInstance(value)) {
      return (List) value;
    }
    fail("expected list but was " + value.getClass().getName());
    return null;
  }

  private static Map findMap(List items, String key, Object expectedValue) {
    for (Object itemValue : items) {
      Map item = asMap(itemValue);
      if (Objects.equals(expectedValue, item.get(key))) {
        return item;
      }
    }
    throw new AssertionError("missing item where " + key + "=" + expectedValue);
  }
}
