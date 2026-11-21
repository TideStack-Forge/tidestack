package com.ouroboros.businessmodel.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Immutable business fact. Its canonical JSON payload is copied on creation and never exposed mutably. */
public final class BusinessEventOccurrence<T> {
  private final String eventId;
  private final BusinessEventIdentity identity;
  private final String modelRevision;
  private final BusinessEventSubject subject;
  private final Map<String, Object> payloadSchema;
  private final Map<String, Object> payloadFields;
  private final Instant occurredAt;
  private final String producerRef;
  private final String actorRef;
  private final String correlationId;
  private final String causationId;
  private final String executionId;
  private final boolean requireUnfilteredModelRead;
  private final Function<Map<String, Object>, T> payloadProjection;

  public static BusinessEventOccurrence<Map<String, Object>> create(
      String eventId, BusinessEventIdentity identity, String modelRevision, BusinessEventSubject subject,
      Map<String, Object> payloadSchema, Map<String, Object> payloadFields, Instant occurredAt,
      String producerRef, String actorRef, String correlationId, String causationId, String executionId) {
    return new BusinessEventOccurrence<>(eventId, identity, modelRevision, subject, payloadSchema, payloadFields,
        occurredAt, producerRef, actorRef, correlationId, causationId, executionId, Function.identity());
  }

  BusinessEventOccurrence(String eventId,
                          BusinessEventIdentity identity,
                          String modelRevision,
                          BusinessEventSubject subject,
                          Map<String, Object> payloadSchema,
                          Map<String, Object> payloadFields,
                          Instant occurredAt,
                          String producerRef,
                          String actorRef,
                          String correlationId,
                          String causationId,
                          String executionId,
                          Function<Map<String, Object>, T> payloadProjection) {
    this(eventId, identity, modelRevision, subject, payloadSchema, payloadFields, occurredAt, producerRef,
        actorRef, correlationId, causationId, executionId, payloadProjection, false);
  }

  private BusinessEventOccurrence(String eventId, BusinessEventIdentity identity, String modelRevision,
                                  BusinessEventSubject subject, Map<String, Object> payloadSchema,
                                  Map<String, Object> payloadFields, Instant occurredAt, String producerRef,
                                  String actorRef, String correlationId, String causationId, String executionId,
                                  Function<Map<String, Object>, T> payloadProjection, boolean requireUnfilteredModelRead) {
    this.eventId = requireText(eventId, "eventId");
    this.identity = Objects.requireNonNull(identity, "identity must not be null");
    this.modelRevision = requireText(modelRevision, "modelRevision");
    this.subject = Objects.requireNonNull(subject, "subject must not be null");
    this.payloadSchema = immutableMap(payloadSchema);
    this.payloadFields = immutableMap(payloadFields);
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    this.producerRef = producerRef;
    this.actorRef = actorRef;
    this.correlationId = correlationId;
    this.causationId = causationId;
    this.executionId = executionId;
    this.requireUnfilteredModelRead = requireUnfilteredModelRead;
    this.payloadProjection = Objects.requireNonNull(payloadProjection, "payloadProjection must not be null");
  }

  public String eventId() {
    return eventId;
  }

  public BusinessEventIdentity identity() {
    return identity;
  }

  public String modelRevision() {
    return modelRevision;
  }

  public BusinessEventSubject subject() {
    return subject;
  }

  public Map<String, Object> payloadSchema() {
    return immutableMap(payloadSchema);
  }

  public Map<String, Object> payloadFields() {
    return immutableMap(payloadFields);
  }

  public T payload() {
    return payloadProjection.apply(immutableMap(payloadFields));
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public String producerRef() {
    return producerRef;
  }

  public String actorRef() {
    return actorRef;
  }

  public String correlationId() {
    return correlationId;
  }

  public String causationId() {
    return causationId;
  }

  public String executionId() {
    return executionId;
  }

  public boolean requireUnfilteredModelRead() {
    return requireUnfilteredModelRead;
  }

  public BusinessEventOccurrence<T> withRequireUnfilteredModelRead(boolean required) {
    return new BusinessEventOccurrence<>(eventId, identity, modelRevision, subject, payloadSchema,
        payloadFields, occurredAt, producerRef, actorRef, correlationId, causationId, executionId, payloadProjection, required);
  }

  public <R> BusinessEventOccurrence<R> withPayloadProjection(Function<Map<String, Object>, R> projection) {
    return new BusinessEventOccurrence<>(eventId, identity, modelRevision, subject, payloadSchema,
        payloadFields, occurredAt, producerRef, actorRef, correlationId, causationId, executionId, projection, requireUnfilteredModelRead);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("eventId", eventId);
    result.put("modelFullName", identity.modelFullName());
    result.put("eventName", identity.eventName());
    result.put("modelRevision", modelRevision);
    Map<String, Object> subjectValues = new LinkedHashMap<>();
    subjectValues.put("modelFullName", subject.modelFullName());
    subjectValues.put("id", subject.id());
    result.put("subject", Collections.unmodifiableMap(subjectValues));
    result.put("payload", payloadFields());
    result.put("occurredAt", occurredAt.toString());
    result.put("producerRef", producerRef);
    result.put("actorRef", actorRef);
    result.put("correlationId", correlationId);
    result.put("causationId", causationId);
    result.put("executionId", executionId);
    result.put("requireUnfilteredModelRead", requireUnfilteredModelRead);
    return Collections.unmodifiableMap(result);
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  static Map<String, Object> immutableMap(Map<String, Object> source) {
    return Collections.unmodifiableMap(copyMap(source));
  }

  private static Map<String, Object> copyMap(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (source != null) {
      source.forEach((key, value) -> result.put(key, immutableValue(value)));
    }
    return result;
  }

  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, nested) -> copy.put(String.valueOf(key), immutableValue(nested)));
      return Collections.unmodifiableMap(copy);
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      list.forEach(nested -> copy.add(immutableValue(nested)));
      return Collections.unmodifiableList(copy);
    }
    if (value == null || value instanceof String || value instanceof Boolean
        || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
        || value instanceof Float || value instanceof Double || value instanceof java.math.BigInteger
        || value instanceof java.math.BigDecimal) {
      return value;
    }
    throw new IllegalArgumentException("Business Event values must be immutable JSON values: " + value.getClass().getName());
  }
}
