package com.ouroboros.dynamicform.resource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class DynamicFormFieldTypeResourceContractTest {

  @Test
  void authorityMetadataUsesFieldTypeEndpoints() {
    Map<String, Object> authority = Json.toMap(readResource("META-INF/ouroboros/metadata/systemSetting/dynamicForm.authority.json"));

    List<?> apis = asList(authority.get("apis"));
    assertTrue(apis.contains("GET /api/dynamic-form/field-types"));
    assertTrue(apis.contains("GET /api/dynamic-form/field-types/{fieldName}/params-schema"));
  }

  @Test
  void addSchemaUsesFieldTypeApis() {
    Map<?, ?> addSchema = readSchemaResource("META-INF/ouroboros/metadata/system-setting/dynamic-form/add.ui-schema.json");

    Map<?, ?> fieldsCombo = findMap(asList(addSchema.get("body")), "name", "fields");
    assertEquals("combo", fieldsCombo.get("type"));

    List<?> items = asList(fieldsCombo.get("items"));
    Map<?, ?> groupItem = findMap(items, "type", "group");
    List<?> groupBody = asList(groupItem.get("body"));
    Map<?, ?> typeField = findMap(groupBody, "name", "type");
    Map<?, ?> source = asMap(typeField.get("source"));
    assertEquals("/api/dynamic-form/field-types", source.get("url"));

    Map<?, ?> serviceItem = findMap(items, "type", "service");
    Map<?, ?> schemaApi = asMap(serviceItem.get("schemaApi"));
    assertEquals("/api/dynamic-form/field-types/${type}/params-schema", schemaApi.get("url"));
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

  private Map<?, ?> readSchemaResource(String resourcePath) {
    Map<String, Object> resource = Json.toMap(readResource(resourcePath));
    return asMap(resource.get("schema"));
  }

  private static Map<?, ?> asMap(Object value) {
    if (value == null) {
      fail("expected map but was null");
    }
    if (Map.class.isInstance(value)) {
      return (Map<?, ?>) value;
    }
    fail("expected map but was " + value.getClass().getName());
    return null;
  }

  private static List<?> asList(Object value) {
    if (value == null) {
      fail("expected list but was null");
    }
    if (List.class.isInstance(value)) {
      return (List<?>) value;
    }
    fail("expected list but was " + value.getClass().getName());
    return null;
  }

  private static Map<?, ?> findMap(List<?> items, String key, Object expectedValue) {
    return items.stream()
        .map(DynamicFormFieldTypeResourceContractTest::asMap)
        .filter(item -> Objects.equals(expectedValue, item.get(key)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing item where " + key + "=" + expectedValue));
  }
}
