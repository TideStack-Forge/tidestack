package com.ouroboros.dev.datasource.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

class PlatformDataSourceSchemaTest {
  private static final List<String> SCHEMAS = Arrays.asList(
      "META-INF/ouroboros/metadata/platform-datasource/add.ui-schema.json",
      "META-INF/ouroboros/metadata/platform-datasource/edit.ui-schema.json",
      "META-INF/ouroboros/metadata/platform-datasource/detail.ui-schema.json"
  );

  @Test
  void shouldExposeConsistentKingbaseDriverOptionWithoutChangingDatasourceType() throws Exception {
    List<String> expectedDrivers = null;
    for (String schemaPath : SCHEMAS) {
      Map<?, ?> schema = schema(schemaPath);
      List<String> drivers = driverOptions(schema);

      assertTrue(drivers.contains("com.kingbase8.Driver"), schemaPath);
      assertEquals(1, countDriverLabel(schema, "KingbaseES / 人大金仓"), schemaPath);
      assertFalse(containsType(schema, "kingbase"), schemaPath);
      assertFalse(urlPlaceholder(schema).contains("kingbase"), schemaPath);

      if (expectedDrivers == null) {
        expectedDrivers = drivers;
      } else {
        assertEquals(expectedDrivers, drivers, schemaPath);
      }
    }
  }

  private static Map<?, ?> schema(String path) throws IOException {
    try (var input = PlatformDataSourceSchemaTest.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Resource not found: " + path);
      }
      Map<?, ?> resource = Json.toBean(Map.class, input);
      assertNotNull(resource, path);
      return asMap(resource.get("schema"));
    }
  }

  private static List<String> driverOptions(Map<?, ?> schema) {
    return options(field(schema, "driverClassName")).stream()
        .map(PlatformDataSourceSchemaTest::asMap)
        .map(option -> String.valueOf(option.get("value")))
        .collect(Collectors.toList());
  }

  private static long countDriverLabel(Map<?, ?> schema, String label) {
    return options(field(schema, "driverClassName")).stream()
        .map(PlatformDataSourceSchemaTest::asMap)
        .filter(option -> label.equals(option.get("label")))
        .count();
  }

  private static String urlPlaceholder(Map<?, ?> schema) {
    return String.valueOf(field(schema, "url").get("placeholder"));
  }

  private static Map<?, ?> field(Map<?, ?> schema, String name) {
    Map<?, ?> definition = asList(schema.get("body")).stream()
        .map(PlatformDataSourceSchemaTest::asMap)
        .filter(item -> "definition".equals(item.get("name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("definition field missing"));
    return asList(definition.get("valueItems")).stream()
        .map(PlatformDataSourceSchemaTest::asMap)
        .filter(item -> name.equals(item.get("name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " field missing"));
  }

  private static List<?> options(Map<?, ?> field) {
    return asList(field.get("options"));
  }

  private static boolean containsType(Object value, String type) {
    if (value instanceof Map<?, ?> map) {
      return type.equals(map.get("type")) || map.values().stream().anyMatch(item -> containsType(item, type));
    }
    if (value instanceof List<?> list) {
      return list.stream().anyMatch(item -> containsType(item, type));
    }
    return false;
  }

  private static Map<?, ?> asMap(Object value) {
    if (!(value instanceof Map<?, ?>)) {
      throw new AssertionError("Expected map but got: " + value);
    }
    return (Map<?, ?>) value;
  }

  private static List<?> asList(Object value) {
    if (!(value instanceof List<?>)) {
      throw new AssertionError("Expected list but got: " + value);
    }
    return (List<?>) value;
  }
}
