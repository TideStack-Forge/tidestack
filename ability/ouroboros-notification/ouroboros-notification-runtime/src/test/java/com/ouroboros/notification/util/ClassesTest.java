package com.ouroboros.notification.util;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassesTest {

  @Test
  void toMapDefineShouldMapBasicTypesToLowercaseTypeNames() {
    Map<String, Object> definition = Classes.toMapDefine(BasicFields.class);

    assertEquals("string", definition.get("name"));
    assertEquals("integer", definition.get("count"));
    assertEquals("boolean", definition.get("enabled"));
  }

  @Test
  void toMapDefineShouldRecurseIntoNestedObjects() {
    Map<String, Object> definition = Classes.toMapDefine(WrapperFields.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> nested = (Map<String, Object>) definition.get("nested");
    assertTrue(nested.containsKey("title"));
    assertEquals("string", nested.get("title"));
    assertEquals("long", nested.get("size"));
  }

  @Test
  void toMapDefineShouldIncludeFieldsDeclaredOnSuperclasses() {
    Map<String, Object> definition = Classes.toMapDefine(InheritedOptionFields.class);

    assertEquals("string", definition.get("endpoint"));
    assertEquals("string", definition.get("sender"));
  }

  private static class BasicFields {
    private String name;
    private Integer count;
    private boolean enabled;
  }

  private static class WrapperFields {
    private NestedFields nested;
  }

  private static class NestedFields {
    private String title;
    private Long size;
  }

  private static class InheritedOptionFields extends InheritedOptionFieldsSupport {
  }

  private static class InheritedOptionFieldsSupport {
    private String endpoint;
    private String sender;
  }
}
