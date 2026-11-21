package com.ouroboros.businessmodel.behavior;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessBehaviorMappingUpgradeTest {
  private static final BehaviorDefinitionSource OLD = new BehaviorDefinitionSource("upgrade.Order", "actions.approve.binding", null);
  private static final BehaviorDefinitionSource NEW = new BehaviorDefinitionSource("upgrade.Order", "actions.approve.binding", "2.0");

  @Test
  void flowUpgradePreservesAbsentAndNullValuesLiteralExpressionsAndContextIdentity() {
    Map<String, Object> mapping = Map.of("id", "$.input.params.id", "literal", "${ 1 / 0 }", "context", "$.context");
    Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("params", Map.of("type", "object",
        "properties", Map.of("id", Map.of("type", List.of("string", "null"))))));
    var converted = BusinessBehaviorMappingPlan.upgradeFlowInput(mapping, schema, OLD);
    var before = BusinessBehaviorMappingPlan.flowInput(mapping, schema, OLD);
    var after = BusinessBehaviorMappingPlan.flowInput(converted, schema, NEW);
    for (var params : List.of(Map.of(), java.util.Collections.singletonMap("id", null), Map.of("id", "order-1"))) {
      Map<String, Object> context = new LinkedHashMap<>(); context.put("principal", "user");
      var values = Map.<String, Object>of("input", Map.of("params", params), "context", context);
      var oldValue = before.map(values, value -> value);
      var newValue = after.map(values, value -> value);
      assertEquals(oldValue, newValue);
      assertTrue(newValue.containsKey("id"));
      assertEquals("${ 1 / 0 }", newValue.get("literal"));
      assertSame(context, newValue.get("context"));
    }
    assertEquals("$.input.params.id", mapping.get("id"));
  }

  @Test
  void literalOutputTreesRemainLiteralIncludingLegacySelectorsAndExpressionText() {
    Map<String, Object> output = Map.of("selector", "$.result.id", "template", "${ 1 / 0 }",
        "nested", List.of("quote's value", Map.of("text", "$${ keep }")));
    Object converted = BusinessBehaviorMappingPlan.upgradeFlowOutput(output, OLD);
    assertEquals(output, BusinessBehaviorMappingPlan.flowOutput(converted, NEW).evaluate(Map.of()));
  }

  @Test
  void upgradeRejectsUnprovenCollectionTraversalWithItsOriginalLocation() {
    var error = assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorMappingPlan.upgradeFlowInput(
        Map.of("ids", "$.input.data.records.id"), Map.of(), OLD));
    assertTrue(error.getMessage().contains(OLD.location()));
    assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorMappingPlan.upgradeFlowOutput("$.ids.id", OLD));
  }
}
