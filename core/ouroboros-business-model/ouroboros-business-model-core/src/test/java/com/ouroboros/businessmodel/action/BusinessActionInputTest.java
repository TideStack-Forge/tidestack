package com.ouroboros.businessmodel.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class BusinessActionInputTest {
  @Test
  void preservesAbsentAndExplicitNullPartitions() {
    var empty = PreparedBusinessActionInput.prepare(BusinessActionInput.empty(), Map.of());
    assertFalse(empty.hasParams());
    assertFalse(empty.hasData());
    var explicitNull = PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly(null), Map.of());
    assertTrue(explicitNull.hasData());
    assertNull(explicitNull.data());
    assertEquals(Map.of(), empty);
  }

  @Test
  void preparesEveryFieldBeforeUseAndDefaultsOnlyMissingValues() {
    Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("params", Map.of(
        "type", "object", "required", List.of("notify"), "additionalProperties", false,
        "properties", Map.of("notify", Map.of("type", "boolean", "default", false),
            "limit", Map.of("type", "integer")))));
    var prepared = PreparedBusinessActionInput.prepare(BusinessActionInput.paramsOnly(Map.of("limit", "2")), schema);
    assertEquals(Map.of("notify", false, "limit", java.math.BigInteger.valueOf(2)), prepared.params());
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(
        BusinessActionInput.paramsOnly(Map.of("notify", "yes")), schema));
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(
        BusinessActionInput.paramsOnly(Map.of("limit", "2.5")), schema));
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(
        BusinessActionInput.paramsOnly(Map.of("limit", List.of("1", "2"))), schema));
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(
        BusinessActionInput.paramsOnly(Map.of("unknown", true)), schema));
    Map<String, Object> nullable = new LinkedHashMap<>();
    nullable.put("notify", null);
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(BusinessActionInput.paramsOnly(nullable), schema));
  }

  @Test
  void arbitraryInputIsReadOnceAndMutableContainersAreIsolated() {
    AtomicInteger reads = new AtomicInteger();
    Map<String, Object> data = new LinkedHashMap<>(Map.of("comment", "approved"));
    BusinessActionInput<Object, Object> input = new BusinessActionInput<>() {
      public boolean hasParams() { return false; }
      public Object params() { fail("absent partition must not be read"); return null; }
      public boolean hasData() { return true; }
      public Object data() { reads.incrementAndGet(); return data; }
    };
    var prepared = PreparedBusinessActionInput.prepare(input, Map.of());
    data.put("comment", "changed");
    assertEquals(Map.of("comment", "approved"), prepared.data());
    assertEquals(prepared.data(), prepared.data());
    assertEquals(1, reads.get());
    Object work = BusinessActionInputValues.workCopy(prepared.data());
    assertNotSame(work, prepared.data());
    assertEquals(work, prepared.data());
  }

  @Test
  void cachedHttpImplementationKeepsItsIdentityAndCachesFailure() {
    AtomicInteger reads = new AtomicInteger();
    var input = new PreparedBusinessActionInput(() -> {
      reads.incrementAndGet();
      return Map.of("data", Map.of("comment", "ok"));
    });
    assertEquals(0, reads.get());
    assertSame(input, PreparedBusinessActionInput.prepare(input, Map.of()));
    input.data();
    input.entrySet();
    assertEquals(1, reads.get());
    var failed = new PreparedBusinessActionInput(() -> { throw new BusinessException("parse failed"); });
    RuntimeException error = assertThrows(BusinessException.class, failed::data);
    assertSame(error, assertThrows(BusinessException.class, failed::data));
  }

  @Test
  void typedRecordsUseTheSameDynamicBusinessShape() {
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.of(new Params(true), new Data("ok")), Map.of());
    assertEquals(Map.of("sendNotification", true), input.params());
    assertEquals(Map.of("comment", "ok"), input.data());
  }

  public record Params(boolean sendNotification) { }
  public record Data(String comment) { }

  @Test
  void generatedRecordPropertyNamesAndOmissionSemanticsArePreservedWithoutJsonSerialization() {
    var stream = new java.io.ByteArrayInputStream(new byte[]{1});
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly(new GeneratedData("ok", null, stream)), Map.of());
    assertEquals("ok", ((Map<?, ?>) input.data()).get("business-name"));
    assertFalse(((Map<?, ?>) input.data()).containsKey("optional"));
    assertSame(stream, ((Map<?, ?>) input.data()).get("file"));
  }

  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  record GeneratedData(@com.fasterxml.jackson.annotation.JsonProperty("business-name") String name, String optional,
                       java.io.InputStream file) { }

  @Test
  void explicitNullIsNotMissingAndNullableFieldsKeepTheirPresence() {
    Map<String, Object> schema = Map.of("type", "object", "required", List.of("data"), "properties",
        Map.of("data", Map.of("type", List.of("null", "integer"), "default", 7)));
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly(null), schema);
    assertTrue(input.hasData());
    assertNull(input.data());
    assertEquals(java.math.BigInteger.TEN, PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly("10"), schema).data());
  }

  @Test
  void nativeStreamsAreKeptWithoutReadingAndCannotMasqueradeAsJsonValues() {
    var stream = new java.io.InputStream() {
      @Override public int read() { fail("validation must not consume native resources"); return -1; }
    };
    var schema = Map.<String, Object>of("type", "object", "properties", Map.of("data", Map.of("type", "object",
        "required", List.of("file"), "properties", Map.of("file", Map.of()))));
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly(Map.of("file", stream)), schema);
    assertSame(stream, ((Map<?, ?>) input.data()).get("file"));
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly(stream),
        Map.of("type", "object", "properties", Map.of("data", Map.of("type", "string")))));
  }

  @Test
  void explicitIdentityAliasesRejectConflictAndDoNotCompareUnrelatedRoles() {
    var schema = Map.<String, Object>of("type", "object", "properties", Map.of("params", Map.of("type", "object"),
        "data", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string",
            "x-business-object-identity", "/params/record/id")))));
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(BusinessActionInput.of(
        Map.of("record", Map.of("id", "one")), Map.of("id", "two")), schema));
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.of(Map.of("record", Map.of("id", "one"),
        "reviewer", Map.of("id", "other")), Map.of("id", "one")), schema);
    assertEquals("one", ((Map<?, ?>) input.data()).get("id"));
  }

  @Test
  void publishedPlansKeepSchemaImmutableAndWorkViewsAreIsolatedPerInvocation() {
    var fields = new LinkedHashMap<String, Object>(Map.of("data", Map.of("type", "object")));
    var plan = new BusinessActionInputPlan(Map.of("type", "object", "properties", fields));
    fields.put("data", Map.of("type", "string"));
    var first = plan.prepare(BusinessActionInput.dataOnly(Map.of("comment", "one")));
    var second = plan.prepare(BusinessActionInput.dataOnly(Map.of("comment", "two")));
    var work = plan.flowInput(first, Map.of("executionId", "first"));
    assertSame(work.get("data"), work.get("data"));
    assertInstanceOf(Map.class, work.get("data")).clear();
    assertEquals(Map.of("comment", "one"), first.data());
    assertEquals(Map.of("comment", "two"), plan.flowInput(second, Map.of()).get("data"));
    assertEquals(Map.of("executionId", "first"), work.get("context"));
  }

  @Test
  void flowRequestViewAliasesBodyAndDataWithoutChangingTheValidatedInput() {
    var plan = new BusinessActionInputPlan(Map.of());
    var input = plan.prepare(BusinessActionInput.of(Map.of("notify", true), Map.of("state", "draft")));
    var flow = plan.flowInput(input, Map.of());

    assertTrue(flow.containsKey("body"));
    assertSame(flow.get("data"), flow.get("body"));
    assertInstanceOf(Map.class, flow.get("body")).clear();
    assertEquals(Map.of(), flow.get("data"));
    assertEquals(Map.of("state", "draft"), input.data());
    assertFalse(flow.containsKey("headers"));
    assertNull(flow.get("uri"));
  }

  @Test
  void requestFieldsRemainLazyAndBusinessParametersTakePrecedenceOverHttpValues() {
    var reads = new AtomicInteger();
    Map<String, Object> request = new java.util.AbstractMap<>() {
      @Override public boolean containsKey(Object key) { return java.util.Set.of("body", "params", "headers", "uri").contains(key); }
      @Override public java.util.Set<String> keySet() { return java.util.Set.of("body", "params", "headers", "uri"); }
      @Override public Object get(Object key) {
        reads.incrementAndGet();
        return switch (String.valueOf(key)) {
          case "body" -> throw new AssertionError("business body must come from explicit input");
          case "params" -> Map.of("id", "http-id", "trace", List.of("one", "two"));
          case "headers" -> Map.of("trace", List.of("one", "two"));
          case "uri" -> "/orders/http-id";
          default -> null;
        };
      }
      @Override public java.util.Set<Entry<String, Object>> entrySet() { throw new AssertionError("request values must stay lazy"); }
    };
    var plan = new BusinessActionInputPlan(Map.of());
    var input = plan.prepare(BusinessActionInput.of(Map.of("id", "input-id"), Map.of("state", "input")));
    var flow = plan.flowInput(input, Map.of("request", request));

    assertTrue(flow.containsKey("headers"));
    assertTrue(flow.keySet().contains("uri"));
    assertEquals(0, reads.get());
    assertEquals(Map.of("state", "input"), flow.get("body"));
    var params = assertInstanceOf(Map.class, flow.get("params"));
    assertEquals("input-id", params.get("id"));
    assertEquals(0, reads.get());
    assertEquals(List.of("one", "two"), params.get("trace"));
    assertEquals(1, reads.get());
    assertEquals("/orders/http-id", flow.get("uri"));
    assertEquals(Map.of("trace", List.of("one", "two")), flow.get("headers"));
    assertEquals(Map.of("id", "input-id"), input.params());
  }

  @Test
  void explicitNullPartitionsAndMappedFieldsNeverFallBackToHttpValues() {
    Map<String, Object> request = new java.util.AbstractMap<>() {
      @Override public Object get(Object key) { throw new AssertionError("explicit null must win over HTTP values"); }
      @Override public boolean containsKey(Object key) { return true; }
      @Override public java.util.Set<String> keySet() { return java.util.Set.of("params", "body", "data", "headers"); }
      @Override public java.util.Set<Entry<String, Object>> entrySet() { throw new AssertionError("request must stay lazy"); }
    };
    var plan = new BusinessActionInputPlan(Map.of());
    var flow = plan.flowInput(plan.prepare(BusinessActionInput.of(null, null)), Map.of("request", request));
    assertTrue(flow.containsKey("params"));
    assertTrue(flow.containsKey("body"));
    assertNull(flow.get("params"));
    assertNull(flow.get("body"));
    assertNull(flow.get("data"));

    var mapping = new LinkedHashMap<String, Object>();
    mapping.put("headers", null);
    var mapped = new BusinessActionInputPlan(Map.of(), mapping);
    assertNull(mapped.flowInput(mapped.prepare(BusinessActionInput.empty()), Map.of("request", request)).get("headers"));
  }

  @Test
  void crudProjectionKeepsCanonicalIdAndIsolatesParameterChangesFromInputAndRequest() {
    var httpParams = Map.of("id", "http-id", "notify", false, "trace", List.of("one", "two"));
    var request = Map.<String, Object>of("params", httpParams, "body", Map.of("state", "http"));
    var plan = new BusinessActionInputPlan(Map.of(), Map.of("params.id", "$.input.params.record.id",
        "params.notify", "$.input.params.notify", "data", "$.input.data"));
    var originalParams = new LinkedHashMap<String, Object>();
    originalParams.put("record", Map.of("id", "input-id"));
    originalParams.put("notify", null);
    var input = plan.prepare(BusinessActionInput.of(originalParams, Map.of("state", "input")));
    var flow = plan.flowInput(input, Map.of("request", request));
    var params = assertInstanceOf(Map.class, flow.get("params"));
    assertEquals("input-id", params.get("id"));
    assertTrue(params.containsKey("notify"));
    assertNull(params.get("notify"));
    assertEquals(List.of("one", "two"), params.get("trace"));
    params.compute("id", (key, previous) -> "changed");
    assertEquals("changed", params.get("id"));
    params.remove("trace");
    assertFalse(params.containsKey("trace"));
    assertNull(params.get("trace"));
    params.clear();
    assertTrue(params.isEmpty());
    assertNull(params.get("id"));
    assertSame(flow.get("data"), flow.get("body"));
    assertEquals(Map.of("state", "input"), flow.get("body"));
    assertEquals(originalParams, input.params());
    assertEquals("http-id", httpParams.get("id"));
    var next = plan.flowInput(input, Map.of("request", request));
    assertEquals("input-id", assertInstanceOf(Map.class, next.get("params")).get("id"));
  }

  @Test
  void customFlowProjectionPreservesRequestIdentityAndCopiesOnlyBusinessWorkValues() {
    var plan = new BusinessActionInputPlan(Map.of(), Map.of("request", "$.context.request",
        "data", "$.input.data", "options", List.of("literal", "$.input.params.notify"),
        "metadata", Map.of("executionId", "$.context.executionId")));
    var request = new java.util.AbstractMap<String, Object>() {
      @Override public Object get(Object key) { return "headers".equals(key) ? Map.of("token", "hidden") : null; }
      @Override public java.util.Set<Entry<String, Object>> entrySet() { return java.util.Set.of(); }
    };
    var input = plan.prepare(BusinessActionInput.of(Map.of("notify", true), Map.of("state", "draft")));
    var work = plan.flowInput(input, Map.of("request", request, "executionId", "one"));
    assertSame(request, work.get("request"));
    assertEquals(Map.of("token", "hidden"), assertInstanceOf(Map.class, work.get("request")).get("headers"));
    assertInstanceOf(Map.class, work.get("data")).clear();
    assertEquals(Map.of("state", "draft"), input.data());
    assertEquals(List.of("literal", true), work.get("options"));
    assertEquals(Map.of("executionId", "one"), work.get("metadata"));
  }
}
