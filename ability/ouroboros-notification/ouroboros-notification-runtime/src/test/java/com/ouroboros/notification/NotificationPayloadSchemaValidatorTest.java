package com.ouroboros.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class NotificationPayloadSchemaValidatorTest {
  private final NotificationPayloadSchemaValidator validator = new NotificationPayloadSchemaValidator();

  @Test
  public void normalizesLegacyIntAndFloatTypes() {
    Map<String, Object> payloadSchema = new LinkedHashMap<>();
    payloadSchema.put("count", fieldSchema("int"));
    payloadSchema.put("ratio", fieldSchema("float"));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("count", Integer.valueOf(1));
    payload.put("ratio", Double.valueOf(1.5));

    assertTrue(validator.validate(payloadSchema, payload).isValid());
  }

  @Test
  public void returnsDetailedErrorsForInvalidPayload() {
    Map<String, Object> payloadSchema = new LinkedHashMap<>();
    payloadSchema.put("count", fieldSchema("int"));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("count", "bad");

    var result = validator.validate(payloadSchema, payload);

    assertFalse(result.isValid());
    assertTrue(result.toMessageString(), result.toMessageString().contains("count"));
    assertTrue(result.toMessageString(), result.toMessageString().contains("type"));
  }

  @Test
  public void normalizesLegacyTypesRegardlessOfCaseAndWhitespace() {
    Map<String, Object> payloadSchema = new LinkedHashMap<>();
    payloadSchema.put("count", fieldSchema(" Int "));
    payloadSchema.put("ratio", fieldSchema(" FLOAT "));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("count", Integer.valueOf(1));
    payload.put("ratio", Double.valueOf(1.5));

    assertTrue(validator.validate(payloadSchema, payload).isValid());
  }

  @Test
  public void treatsNullOrEmptySchemaAsValid() {
    assertTrue(validator.validate(null, Collections.singletonMap("anything", "value")).isValid());
    assertTrue(validator.validate(new LinkedHashMap<>(), Collections.singletonMap("anything", "value")).isValid());
  }

  @Test
  public void normalizesLegacyTypesRecursivelyInsideNestedObjectsAndArrays() {
    Map<String, Object> payloadSchema = new LinkedHashMap<>();
    payloadSchema.put("meta", nestedObjectSchema());
    payloadSchema.put("items", nestedArraySchema());

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("count", Integer.valueOf(1));
    meta.put("ratio", Double.valueOf(1.5));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("meta", meta);
    payload.put("items", Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)));

    assertTrue(validator.validate(payloadSchema, payload).isValid());
  }

  private Map<String, Object> fieldSchema(String type) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", type);
    return schema;
  }

  private Map<String, Object> nestedObjectSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("count", fieldSchema("int"));
    properties.put("ratio", fieldSchema("float"));

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    return schema;
  }

  private Map<String, Object> nestedArraySchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "array");
    schema.put("items", fieldSchema("int"));
    return schema;
  }
}
