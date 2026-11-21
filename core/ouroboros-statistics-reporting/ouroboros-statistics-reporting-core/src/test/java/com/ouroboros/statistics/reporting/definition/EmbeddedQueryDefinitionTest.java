package com.ouroboros.statistics.reporting.definition;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddedQueryDefinitionTest {
  static Map<String, Object> definition() {
    return Map.of("queryDefinition", Map.of("type", "sql", "sql", "select ${departmentId} as id"),
        "parameters", List.of(Map.of("name", "departmentId", "type", "string", "valueSource", "systemContext",
            "systemContextBinding", "departmentId", "required", true)),
        "schema", Map.of("fields", List.of(Map.of("name", "id", "dataType", "string", "semanticRole", "dimension"))),
        "requiredContext", List.of("currentUser", "departmentId"));
  }

  @Test
  void createsAnOwnerPrivateSourceAndRequiresTrustedContext() {
    var query = EmbeddedQueryDefinition.resolve(definition(), "demo.orders");
    assertEquals("demo.orders.private-query", query.getName());
    assertTrue(query.getIsEnabled());
    query.requireContext(Map.of("currentUser", "user1", "departmentId", "d1"));
    assertThrows(BusinessException.class, () -> query.requireContext(Map.of("currentUser", "user1")));
    assertThrows(BusinessException.class, () -> query.requireContext(Map.of("currentUser", "", "departmentId", "d1")));
  }

  @Test
  void clampsPreviouslyPublishedLimitsWhenPlatformIsTightened() {
    var source = new java.util.LinkedHashMap<>(definition());
    source.put("executionConstraints", Map.of("maxPageSize", 2000));
    assertThrows(BusinessException.class, () -> EmbeddedQueryDefinition.resolve(source, "demo.orders", true));
    var published = EmbeddedQueryDefinition.resolve(source, "demo.orders", false);
    assertEquals(1000, com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionPolicy.defaults()
        .tighten(published.getExecutionConstraints(), false).maxPageSize());
  }

  @Test
  void rejectsMissingContextAndInvalidParameterDeclarations() {
    var source = new java.util.LinkedHashMap<>(definition());
    source.remove("requiredContext");
    assertThrows(BusinessException.class, () -> EmbeddedQueryDefinition.resolve(source, "demo.orders"));
    source.put("requiredContext", List.of("currentUser"));
    source.put("parameters", List.of());
    assertThrows(BusinessException.class, () -> EmbeddedQueryDefinition.resolve(source, "demo.orders"));
  }
}
