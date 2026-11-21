package com.ouroboros.businessmodel.dev;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;

/** The public BusinessEventOccurrence.toMap contract used by producers and listeners. */
final class BusinessEventTypeShapes {
  private BusinessEventTypeShapes() {}

  static TypeShape envelope(TypeShape payload, TypeShape subjectId) {
    var fields = new LinkedHashMap<String, TypeShape>();
    for (var name : List.of("eventId", "modelFullName", "eventName", "modelRevision", "occurredAt", "producerRef", "actorRef",
        "correlationId", "causationId", "executionId")) fields.put(name, TypeShape.scalar("string"));
    for (var name : List.of("producerRef", "actorRef", "correlationId", "causationId", "executionId")) {
      fields.put(name, TypeShape.scalar("string").merge(TypeShape.scalar("null")));
    }
    fields.put("subject", TypeShape.object(Map.of("modelFullName", TypeShape.scalar("string"), "id", subjectId), "map"));
    fields.put("payload", payload);
    fields.put("requireUnfilteredModelRead", TypeShape.scalar("boolean"));
    return TypeShape.object(fields, "map");
  }
}
