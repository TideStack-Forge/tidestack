package com.ouroboros.businessmodel.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.BusinessObjectReferenceSchemas;

class BusinessObjectReferenceSchemasTest {
  private final Map<String, Object> reference = Map.of("type", "object", "x-business-object-ref", "sales.Customer");

  @Test
  void materializedPropertiesAndHomogeneousArrayItemsHaveResolvableIdentityLocations() {
    assertTrue(BusinessObjectReferenceSchemas.unsupportedReferencePaths(Map.of("properties", Map.of("customers",
        Map.of("type", "array", "items", reference)))).isEmpty());
    assertTrue(BusinessObjectReferenceSchemas.unsupportedReferencePaths(reference).isEmpty());
  }

  @Test
  void compositionAndDynamicPropertiesCannotHideAReferenceDeclaration() {
    assertEquals(List.of(".allOf[0].properties.customer.x-business-object-ref"),
        BusinessObjectReferenceSchemas.unsupportedReferencePaths(Map.of("allOf", List.of(Map.of("properties",
            Map.of("customer", reference))))));
    assertEquals(List.of(".patternProperties.*.x-business-object-ref"),
        BusinessObjectReferenceSchemas.unsupportedReferencePaths(Map.of("patternProperties", Map.of("*", reference))));
    assertEquals(List.of(".items[0].x-business-object-ref"),
        BusinessObjectReferenceSchemas.unsupportedReferencePaths(Map.of("items", List.of(reference))));
  }

  @Test
  void schemaReferencesCannotChangeTheLocationOfBusinessObjectReferences() {
    assertEquals(List.of(".$ref"), BusinessObjectReferenceSchemas.unsupportedReferencePaths(Map.of(
        "$ref", "#/properties/customer", "properties", Map.of("customer", reference))));
    assertTrue(BusinessObjectReferenceSchemas.unsupportedReferencePaths(Map.of(
        "$ref", "#/$defs/name", "$defs", Map.of("name", Map.of("type", "string")))).isEmpty());
    assertTrue(BusinessObjectReferenceSchemas.unsupportedReferencePaths(Map.of(
        "allOf", List.of(Map.of("type", "object"), Map.of("required", List.of("name"))))).isEmpty());
  }
}
