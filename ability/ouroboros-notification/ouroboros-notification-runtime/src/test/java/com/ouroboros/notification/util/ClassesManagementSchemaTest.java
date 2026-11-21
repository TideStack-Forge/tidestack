package com.ouroboros.notification.util;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.notification.adapter.NotificationChannelManagement;
import com.ouroboros.util.JsonSchemas;

import static org.junit.jupiter.api.Assertions.*;

class ClassesManagementSchemaTest {
  @Test
  void theOriginalJavaPropertiesDetermineTypesIncludingNestedMapsAndArrays() {
    var schema = Classes.toManagementSchema(Options.class, Map.of("subject", NotificationChannelManagement.Field.required("标题")));
    assertTrue(JsonSchemas.validateResult(schema, Map.of("subject", "hello", "flags", Map.of("web", true), "items", List.of(3))).isValid());
    assertFalse(JsonSchemas.validateResult(schema, Map.of("subject", "hello", "flags", Map.of("web", "true"))).isValid());
    assertFalse(JsonSchemas.validateResult(schema, Map.of("subject", "hello", "items", List.of("wrong type"))).isValid());
    assertFalse(JsonSchemas.validateResult(schema, Map.of("subject", "hello", "unknown", true)).isValid());
  }

  @Test
  void displayAndValidationUseTheSameSupplementWithoutRedefiningFieldTypes() {
    var schema = Classes.toManagementSchema(Options.class, Map.of("subject", NotificationChannelManagement.Field.required("标题")));
    assertFalse(JsonSchemas.validateResult(schema, Map.of()).isValid());
    assertFalse(JsonSchemas.validateResult(schema, Map.of("subject", "  ")).isValid());
    assertFalse(JsonSchemas.validateResult(schema, Map.of("subject", 42)).isValid());
    assertTrue(schema.toString().contains("标题"));
  }

  @Test
  void optionalJavaObjectAndEnumFieldsAcceptNullWhilePresentValuesKeepTheirTypes() {
    var schema = Classes.toManagementSchema(NestedOptions.class, Map.of());
    var empty = new java.util.LinkedHashMap<String, Object>();
    empty.put("child", null); empty.put("mode", null);
    assertTrue(JsonSchemas.validateResult(schema, empty).isValid());
    assertTrue(JsonSchemas.validateResult(schema, Map.of("child", Map.of("count", 2), "mode", "FIRST")).isValid());
    assertFalse(JsonSchemas.validateResult(schema, Map.of("child", "wrong")).isValid());
    assertFalse(JsonSchemas.validateResult(schema, Map.of("mode", "UNKNOWN")).isValid());
    assertFalse(JsonSchemas.validateResult(schema, Map.of("child", Map.of("count", "wrong"))).isValid());
  }

  static class NestedOptions {
    public Child child;
    public Mode mode;
  }
  static class Child { public Integer count; }
  enum Mode { FIRST, SECOND }

  static class Options {
    @JsonProperty("subject") public String template;
    public Map<String, Boolean> flags;
    public List<Integer> items;
    public static String ignored;
  }
}
