package com.ouroboros.businessmodel.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BusinessEventOccurrenceTest {
  @Test
  void occurrenceKeepsAnImmutableDeepSnapshotOfItsPayloadAndSchema() {
    List<String> required = new ArrayList<>(List.of("orderId"));
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("required", required);
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("orderId", "order-1");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("order", nested);
    BusinessEventOccurrence<Map<String, Object>> occurrence = occurrence(schema, payload);

    required.add("secret");
    nested.put("orderId", "changed");

    assertEquals(List.of("orderId"), occurrence.payloadSchema().get("required"));
    assertEquals("order-1", ((Map<?, ?>) occurrence.payload().get("order")).get("orderId"));
    assertThrows(UnsupportedOperationException.class, () -> occurrence.payloadFields().put("new", true));
  }

  @Test
  void mutableObjectsCannotEnterTheCanonicalFactOrItsSubject() {
    assertThrows(IllegalArgumentException.class,
        () -> occurrence(Map.of("type", "object"), Map.of("mutable", new java.util.Date())));
    assertThrows(IllegalArgumentException.class,
        () -> BusinessEventSubject.instance("sales.Order", new ArrayList<>()));
  }

  @Test
  void modelSubjectSerializesWithoutInventingAnObjectId() {
    BusinessEventOccurrence<Map<String, Object>> occurrence = occurrence(
        Map.of("type", "object"), Map.of("reason", "maintenance"));

    assertEquals("sales.Order", ((Map<?, ?>) occurrence.toMap().get("subject")).get("modelFullName"));
    assertNull(((Map<?, ?>) occurrence.toMap().get("subject")).get("id"));
  }

  @Test
  void disclosureRestrictionSurvivesProjectionAndSerializationWithoutChangingLegacyFacts() {
    var original = occurrence(Map.of("type", "object"), Map.of("reason", "deleted"));
    assertEquals(false, original.requireUnfilteredModelRead());
    var restricted = original.withRequireUnfilteredModelRead(true);
    assertEquals(true, restricted.toMap().get("requireUnfilteredModelRead"));
    assertEquals(true, restricted.withPayloadProjection(fields -> fields.get("reason")).requireUnfilteredModelRead());
    assertEquals("deleted", restricted.withPayloadProjection(fields -> fields.get("reason")).payload());
    assertEquals(false, original.requireUnfilteredModelRead());
  }

  private BusinessEventOccurrence<Map<String, Object>> occurrence(Map<String, Object> schema,
                                                                   Map<String, Object> payload) {
    return new BusinessEventOccurrence<>("event-1", new BusinessEventIdentity("sales.Order", "submitted"),
        "revision-1", BusinessEventSubject.model("sales.Order"), schema, payload, Instant.EPOCH,
        "order-service", "user-1", "correlation-1", null, "execution-1", values -> values);
  }
}
