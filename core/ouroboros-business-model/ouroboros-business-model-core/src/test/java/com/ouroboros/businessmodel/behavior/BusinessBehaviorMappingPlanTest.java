package com.ouroboros.businessmodel.behavior;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.action.BusinessActionInputPlan;
import com.ouroboros.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class BusinessBehaviorMappingPlanTest {
  private static final BehaviorDefinitionSource CURRENT = new BehaviorDefinitionSource("orders.business.json", "actions.review.binding", "2.0");
  private static final BehaviorDefinitionSource LEGACY = new BehaviorDefinitionSource("orders.business.json", "actions.review.binding", null);

  @Test
  void editorSubscriptionMappingBuildsBothPartitionsAndQuotesLiteralStringsUsingOfficialEl() {
    var mapping = new LinkedHashMap<String, Object>();
    mapping.put("params.order.id", "${ event.subject.id }");
    mapping.put("data.amount", "${ event.payload.amount }");
    mapping.put("data.source", "${ 'O''Brien $${literal}' }");
    mapping.put("data.enabled", false);
    mapping.put("data.empty", null);
    mapping.put("data.absent", "${ event.payload.absent }");
    var context = Map.<String, Object>of("event", Map.of("subject", Map.of("id", "O-1"), "payload", Map.of("amount", 12)));
    var result = BusinessBehaviorMappingPlan.subscriptionInput(mapping, CURRENT).map(context, UnaryOperator.identity());
    assertEquals(Map.of("order", Map.of("id", "O-1")), result.get("params"));
    var data = assertInstanceOf(Map.class, result.get("data"));
    assertEquals(12, data.get("amount"));
    assertEquals("O'Brien ${literal}", data.get("source"));
    assertEquals(false, data.get("enabled"));
    assertTrue(data.containsKey("empty"));
    assertFalse(data.containsKey("absent"));
    var whole = BusinessBehaviorMappingPlan.subscriptionInput(Map.of("data", "${ event.payload }"), CURRENT).map(context, UnaryOperator.identity());
    assertEquals(Set.of("data"), whole.keySet());
    assertEquals(Map.of("amount", 12), whole.get("data"));
  }

  @Test
  void flowUsesOfficialExpressionsWithMissingNullComputationsAndMutableIsolatedValues() {
    var mapping = new LinkedHashMap<String, Object>();
    mapping.put("payload", "${ input.data }");
    mapping.put("params.nullValue", "${ input.params.nullValue }");
    mapping.put("params.absent", "${ input.params.absent }");
    mapping.put("params.limit", "${ input.params.limit ?: 20 }");
    mapping.put("labels", List.of("item-${ 1 + 2 }", "$${literal}"));
    var plan = new BusinessActionInputPlan(Map.of(), mapping, CURRENT);
    var input = plan.prepare(BusinessActionInput.of(java.util.Collections.singletonMap("nullValue", null), Map.of("name", "one")));
    var values = plan.flowInput(input, Map.of());
    assertEquals(Set.of("payload", "params", "labels"), values.keySet());
    var params = assertInstanceOf(Map.class, values.get("params"));
    assertFalse(params.containsKey("absent"));
    assertTrue(params.containsKey("nullValue"));
    assertNull(params.get("nullValue"));
    assertEquals(20, params.get("limit"));
    assertEquals(List.of("item-3", "${literal}"), values.get("labels"));
    assertSame(values.get("payload"), values.get("payload"));
    assertInstanceOf(Map.class, values.get("payload")).clear();
    assertEquals(Map.of("name", "one"), input.data());
    assertEquals(Map.of("name", "one"), plan.flowInput(input, Map.of()).get("payload"));
  }

  @Test
  void valuesAndFailuresAreLazyAndCachedPerTargetAndInvocation() {
    var reads = new AtomicInteger();
    Map<String, Object> request = new AbstractMap<>() {
      public Object get(Object key) { reads.incrementAndGet(); return Map.of("name", "one"); }
      public boolean containsKey(Object key) { return "body".equals(key); }
      public Set<String> keySet() { return Set.of("body"); }
      public Set<Entry<String, Object>> entrySet() { throw new AssertionError("request must not be enumerated"); }
    };
    var plan = new BusinessActionInputPlan(Map.of(), Map.of("payload", "${ context.request.body ?: null }",
        "request", "${ context.request }", "error", "${ 1 / 0 }"), CURRENT);
    var input = plan.prepare(BusinessActionInput.empty());
    var values = plan.flowInput(input, Map.of("request", request));
    assertEquals(Set.of("payload", "request", "error", "body"), values.keySet());
    assertEquals(Set.of("payload", "request", "error", "body"), values.entrySet().stream().map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toSet()));
    assertEquals(0, reads.get());
    assertSame(request, values.get("request"));
    assertSame(values.get("payload"), values.get("payload"));
    assertEquals(1, reads.get());
    var error = assertThrows(BusinessException.class, () -> values.get("error"));
    assertSame(error, assertThrows(BusinessException.class, () -> values.get("error")));
    assertTrue(error.getMessage().contains("orders.business.json:actions.review.binding.inputMapping.error"));
    assertEquals(Map.of("name", "one"), values.get("payload"));
    plan.flowInput(input, Map.of("request", request)).get("payload");
    assertEquals(2, reads.get());
  }

  @Test
  void legacyFlowRetainsLiteralsAndMaterializesNullWhileSchemaGuidesArrayProjection() {
    var schema = Map.<String, Object>of("type", "object", "properties", Map.of("params",
        Map.of("type", "object", "properties", Map.of("records", Map.of("type", List.of("array", "null"),
            "items", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "integer"))))))));
    var plan = new BusinessActionInputPlan(schema, Map.of("ids", "$.input.params.records.id",
        "literal", "${ 1 / 0 }", "missing", "$.input.params.absent"), LEGACY);
    var input = plan.prepare(BusinessActionInput.paramsOnly(Map.of("records", List.of(Map.of("id", 3), Map.of()))));
    var values = plan.flowInput(input, Map.of());
    assertEquals(java.util.Arrays.asList(3, null), values.get("ids"));
    assertTrue(values.containsKey("missing"));
    assertNull(values.get("missing"));
    assertEquals("${ 1 / 0 }", values.get("literal"));
    assertNull(plan.flowInput(plan.prepare(BusinessActionInput.paramsOnly(Map.of())), Map.of()).get("ids"));
    assertNull(plan.flowInput(plan.prepare(BusinessActionInput.paramsOnly(java.util.Collections.singletonMap("records", null))), Map.of()).get("ids"));
    assertEquals(List.of(), plan.flowInput(plan.prepare(BusinessActionInput.paramsOnly(Map.of("records", List.of()))), Map.of()).get("ids"));
  }

  @Test
  void subscriptionsUsePlatformElAndPresenceIndependentlyOfTheModelFormat() {
    var context = Map.<String, Object>of("event", Map.of("payload", Map.of("id", 4)));
    for (var source : List.of(LEGACY, CURRENT)) {
      assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorMappingPlan.subscriptionInput(
          Map.of("data.id", "$.event.payload.id"), source));
      var current = BusinessBehaviorMappingPlan.subscriptionInput(Map.of("data.id", "${ event.payload.id }",
          "data.absent", "${ event.payload.absent }", "data.null", "${ null }",
          "data.template", "id-${ event.payload.id }"), source).map(context, UnaryOperator.identity());
      var data = assertInstanceOf(Map.class, current.get("data"));
      assertEquals(Set.of("id", "null", "template"), data.keySet());
      assertEquals(4, data.get("id"));
      assertEquals("id-4", data.get("template"));
      assertNull(data.get("null"));
    }
  }

  @Test
  void outputPreservesNativeTypesNullsAndTheOldLiteralContract() {
    var result = new LinkedHashMap<String, Object>(Map.of("accepted", true));
    assertSame(result, BusinessBehaviorMappingPlan.flowOutput("${ result }", CURRENT).evaluate(Map.of("result", result)));
    assertEquals(7, BusinessBehaviorMappingPlan.flowOutput("$.id", LEGACY).evaluate(Map.of("result", Map.of("id", 7))));
    assertEquals("${ result }", BusinessBehaviorMappingPlan.flowOutput("${ result }", LEGACY).evaluate(Map.of("result", result)));
    assertEquals(Map.of("literal", "$.id"), BusinessBehaviorMappingPlan.flowOutput(Map.of("literal", "$.id"), LEGACY)
        .evaluate(Map.of("result", result)));
    assertNull(BusinessBehaviorMappingPlan.flowOutput("${ result.id }", CURRENT).evaluate(Map.of("result", Map.of())));
  }

  @Test
  void invalidSyntaxAndVersionsAreLocatedDuringCompilationWithoutEvaluation() {
    assertThrows(IllegalArgumentException.class, () -> new BehaviorDefinitionSource("test", "action", "1.0"));
    var failure = assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorMappingPlan.subscriptionInput(
        Map.of("data", "${ broken[ }"), CURRENT));
    assertTrue(failure.getMessage().contains(CURRENT.location()));
    assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorMappingPlan.subscriptionInput(Map.of("data", "$.event.payload"), CURRENT));
    assertDoesNotThrow(() -> BusinessBehaviorMappingPlan.subscriptionInput(Map.of("data", "${ 1 / 0 }"), CURRENT));
  }

  @Test
  void flowScriptsCanModifyTheirOwnViewWithoutChangingAnotherInvocationOrThePlan() {
    var plan = BusinessBehaviorMappingPlan.subscriptionInput(Map.of("data.name", "${ event.name }"), CURRENT);
    var context = Map.<String, Object>of("event", Map.of("name", "original"));
    var first = plan.map(context, UnaryOperator.identity());
    var second = plan.map(context, UnaryOperator.identity());
    first.put("data", Map.of("replacement", true));
    assertEquals(Map.of("replacement", true), first.get("data"));
    assertEquals(Map.of("name", "original"), second.get("data"));
    first.remove("data");
    assertFalse(first.containsKey("data"));
    first.put("additional", 3);
    first.clear();
    assertTrue(first.isEmpty());
    var literal = new LinkedHashMap<String, Object>(Map.of("name", "original"));
    var output = BusinessBehaviorMappingPlan.flowOutput(literal, LEGACY);
    literal.clear();
    assertInstanceOf(Map.class, output.evaluate(Map.of())).clear();
    assertEquals(Map.of("name", "original"), output.evaluate(Map.of()));
  }

  @Test
  void anUnprovenLegacyCollectionTraversalCannotSilentlyBecomeNull() {
    var plan = BusinessBehaviorMappingPlan.flowInput(Map.of("ids", "$.input.params.records.id"), Map.of(), LEGACY);
    var error = assertThrows(BusinessException.class, () -> plan.map(Map.of("input", Map.of("params",
        Map.of("records", List.of(Map.of("id", 3))))), UnaryOperator.identity()).get("ids"));
    assertTrue(error.getMessage().contains(LEGACY.location()));
    assertNull(plan.map(Map.of("input", Map.of()), UnaryOperator.identity()).get("ids"));
  }

  @Test
  void trustedContextValuesKeepTheirIdentityInBothSourceFormats() {
    var states = new LinkedHashMap<String, Object>(Map.of("record", Map.of("status", "open")));
    for (var source : List.of(CURRENT, LEGACY)) {
      var plan = new BusinessActionInputPlan(Map.of(), Map.of("states",
          source.current() ? "${ context.objectStates }" : "$.context.objectStates"), source);
      assertSame(states, plan.flowInput(plan.prepare(BusinessActionInput.empty()), Map.of("objectStates", states)).get("states"));
    }
  }

}
