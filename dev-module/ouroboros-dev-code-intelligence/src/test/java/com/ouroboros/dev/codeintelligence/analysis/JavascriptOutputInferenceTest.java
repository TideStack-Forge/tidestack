package com.ouroboros.dev.codeintelligence.analysis;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.codeintelligence.type.TypeShape;

class JavascriptOutputInferenceTest {
  private final Map<String, TypeShape> bindings = Map.of("$0", TypeShape.object(Map.of("name", TypeShape.scalar("string"),
      "items", TypeShape.array(TypeShape.object(Map.of("id", TypeShape.scalar("integer")), "map"))), "map"));

  @Test void infersLocalsCollectionsAndAllReturnPaths() {
    var javascript = new JavascriptOutputInference().inferOutput("""
        const person = {name: $0.name, rows: [{id: $0.items[0].id}]};
        if (flag) return person;
        return {name: 'fallback', enabled: true};
        """, bindings);
    for (var result : List.of(javascript)) {
      assertEquals("string", result.property("name").type());
      assertEquals("integer", result.property("rows").element().property("id").type());
      assertEquals("boolean", result.property("enabled").type());
      assertFalse(result.required().contains("rows"));
    }
    assertEquals("null", new JavascriptOutputInference().inferOutput("42;", bindings).type());
  }

  @Test void branchAssignmentsAndEarlyReturnsPreserveContinuingPaths() {
    var js = new JavascriptOutputInference();
    assertEquals("boolean", js.inferOutput("let x = {old: 1}; if (flag) return {stop: true}; x = {ok: true}; return x;", bindings).property("ok").type());
    assertFalse(js.inferOutput("if (flag) return {only: true};", bindings).schema().path("anyOf").isMissingNode());
  }

  @Test void unknownInvalidAndOverBudgetPathsRemainUnknown() {
    for (JavascriptOutputInference provider : List.of(new JavascriptOutputInference())) {
      assertTrue(provider.inferOutput("return unknownCall()", bindings).isUnknown());
      assertTrue(provider.inferOutput("return {", bindings).isUnknown());
      assertTrue(provider.inferOutput("x".repeat(65537), bindings).isUnknown());
      assertTrue(provider.inferOutput("while (true) {}", bindings).isUnknown());
    }
  }

  @Test void mutationsAndCastsDoNotLeaveStaleObjectContracts() {
    var javascript = new JavascriptOutputInference();
    assertTrue(javascript.inferOutput("let x = {old: 1}; x += {newValue: true}; return x;", bindings).isUnknown());
    assertTrue(javascript.inferOutput("let x = {old: 1}; x++; return x;", bindings).isUnknown());
  }

  @Test void continuingBranchesMergeLocalsAndTerminatingElseDoesNotPoisonTheOtherPath() {
    var javascript = new JavascriptOutputInference();
    var javascriptUnion = javascript.inferOutput("let x = {}; if (flag) { x = {a: 'yes'}; } else { x = {b: 1}; } return x;", bindings);
    for (var shape : List.of(javascriptUnion)) {
      assertEquals("string", shape.property("a").type());
      assertTrue(java.util.Set.of("integer", "number").contains(shape.property("b").type()));
      assertTrue(shape.required().isEmpty());
    }
    assertEquals("boolean", javascript.inferOutput("let x = {}; if (flag) { x = {ok: true}; } else { return {stop: true}; } return x;", bindings).property("ok").type());
  }
}
