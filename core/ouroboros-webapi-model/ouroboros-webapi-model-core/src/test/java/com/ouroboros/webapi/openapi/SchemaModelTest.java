package com.ouroboros.webapi.openapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class SchemaModelTest {

  @Test
  void shouldStoreUnionTypesForOpenApi31() {
    SchemaModel schema = SchemaModel.builder()
        .types(Arrays.asList("string", "null"))
        .build();

    assertEquals(Arrays.asList("string", "null"), schema.getTypes());
  }

  @Test
  void shouldStoreCompositeAndObjectConstraints() {
    SchemaModel stringSchema = SchemaModel.builder().stringType().build();
    SchemaModel integerSchema = SchemaModel.builder().integerType().build();
    SchemaModel compositeSchema = objectSchema(stringSchema);
    SchemaModel objectSchema = SchemaModel.builder()
        .objectType()
        .additionalProperties(stringSchema)
        .minProperties(1)
        .maxProperties(5)
        .constValue("fixed")
        .multipleOf(2)
        .exclusiveMinimum(0)
        .exclusiveMaximum(10)
        .oneOf(Collections.singletonList(stringSchema))
        .anyOf(Collections.singletonList(integerSchema))
        .allOf(Collections.singletonList(compositeSchema))
        .not(integerSchema)
        .build();

    assertEquals(stringSchema, objectSchema.getAdditionalProperties());
    assertEquals(Integer.valueOf(1), objectSchema.getMinProperties());
    assertEquals(Integer.valueOf(5), objectSchema.getMaxProperties());
    assertEquals("fixed", objectSchema.getConstValue());
    assertEquals(Integer.valueOf(2), objectSchema.getMultipleOf());
    assertEquals(Integer.valueOf(0), objectSchema.getExclusiveMinimum());
    assertEquals(Integer.valueOf(10), objectSchema.getExclusiveMaximum());
    assertEquals(Collections.singletonList(stringSchema), objectSchema.getOneOf());
    assertEquals(Collections.singletonList(integerSchema), objectSchema.getAnyOf());
    assertEquals(Collections.singletonList(compositeSchema), objectSchema.getAllOf());
    assertEquals(integerSchema, objectSchema.getNot());
  }

  @Test
  void shouldKeepNullableCompatibility() {
    SchemaModel schema = SchemaModel.builder()
        .stringType()
        .nullable(true)
        .build();

    assertEquals(Boolean.TRUE, schema.getNullable());
    assertNull(schema.getTypes());
  }

  @Test
  void shouldSerializeOpenApi31SchemaKeywordsAndRoundTripUnionType() {
    SchemaModel schema = SchemaModel.builder()
        .types(Arrays.asList("string", "null"))
        .defaultValue("demo")
        .enumValues(Arrays.asList("demo", "prod"))
        .constValue("demo")
        .build();

    Map<String, Object> json = Json.toMap(Json.toJsonString(schema));
    SchemaModel roundTrip = Json.toBean(SchemaModel.class, Json.toJsonString(schema));

    assertEquals(Arrays.asList("string", "null"), json.get("type"));
    assertEquals("demo", json.get("default"));
    assertEquals(Arrays.asList("demo", "prod"), json.get("enum"));
    assertEquals("demo", json.get("const"));
    assertFalse(json.containsKey("types"));
    assertFalse(json.containsKey("defaultValue"));
    assertFalse(json.containsKey("enumValues"));
    assertFalse(json.containsKey("constValue"));
    assertEquals(Arrays.asList("string", "null"), roundTrip.getTypes());
    assertNull(roundTrip.getType());
  }

  @Test
  void shouldSerializeSchemaRefAsDollarRefAndOmitNullFields() {
    SchemaModel schema = SchemaModel.builder()
        .ref("#/components/schemas/User")
        .build();

    Map<String, Object> json = Json.toMap(Json.toJsonString(schema));

    assertEquals("#/components/schemas/User", json.get("$ref"));
    assertFalse(json.containsKey("ref"));
    assertFalse(json.containsKey("defaultValue"));
    assertFalse(json.containsKey("enumValues"));
    assertFalse(json.containsKey("constValue"));
  }

  @Test
  void shouldSerializeServerVariableKeywords() {
    ServerVariableModel serverVariable = ServerVariableModel.builder()
        .enumValues(Arrays.asList("dev", "prod"))
        .defaultValue("dev")
        .description("target env")
        .build();

    Map<String, Object> json = Json.toMap(Json.toJsonString(serverVariable));

    assertEquals(Arrays.asList("dev", "prod"), json.get("enum"));
    assertEquals("dev", json.get("default"));
    assertEquals("target env", json.get("description"));
    assertFalse(json.containsKey("enumValues"));
    assertFalse(json.containsKey("defaultValue"));
  }

  private SchemaModel objectSchema(SchemaModel propertySchema) {
    return SchemaModel.builder()
        .objectType()
        .properties(Collections.singletonMap("value", propertySchema))
        .build();
  }
}
