package com.ouroboros.businessmodel.action;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;

class BusinessActionPreconditionPlanTest {
  @Test
  void noDefinitionAddsNoRuleOrObjectReads() {
    var plan = new BusinessActionPreconditionPlan(null);
    assertTrue(plan.isValid());
    assertFalse(plan.isConfigured());
    assertFalse(plan.requiresObjectStates());
    assertTrue(plan.accessPaths().isEmpty());
    plan.requireSatisfied(Map.of(), Map.of());
  }

  @Test
  void compiledRuleHasImmutableReadsAndStaticBusinessFailure() {
    var plan = new BusinessActionPreconditionPlan(Map.of("expression", "${objectStates['/params/order'].status == 'DRAFT'}",
        "message", "只有草稿可以提交"));
    assertTrue(plan.isValid());
    assertTrue(plan.requiresObjectStates());
    assertEquals(List.of(List.of("objectStates", "/params/order", "status")), plan.accessPaths());
    assertThrows(UnsupportedOperationException.class, () -> plan.accessPaths().get(0).add("secret"));
    plan.requireSatisfied(Map.of(), Map.of("/params/order", Map.of("status", "DRAFT")));
    var rejected = assertThrows(BusinessActionPreconditionException.class,
        () -> plan.requireSatisfied(Map.of(), Map.of("/params/order", Map.of("status", "CLOSED"))));
    assertEquals("只有草稿可以提交", rejected.getMessage());
  }

  @Test
  void invalidCandidatesRetainDiagnosticsAndNonBooleanValuesNeverCoerceToSuccess() {
    for (Object definition : List.of("${true}", Map.of("expression", "${42}"),
        Map.of("expression", "${true}", "message", 1), Map.of("expression", "${true}", "message", ""),
        Map.of("expression", "${request == null}"))) {
      var plan = new BusinessActionPreconditionPlan(definition);
      assertFalse(plan.isValid());
      assertThrows(BusinessException.class, plan::requireValid);
    }
    var typed = new BusinessActionPreconditionPlan(Map.of("expression", "${input.data.confirmed}"));
    assertThrows(BusinessException.class, () -> typed.requireSatisfied(Map.of("data", Map.of("confirmed", "true")), Map.of()));
    assertThrows(BusinessActionPreconditionException.class,
        () -> typed.requireSatisfied(Map.of("data", Map.of("confirmed", false)), Map.of()));
  }
}
