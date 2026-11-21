package com.ouroboros.logicflow.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodeUtilExpressionRuntimeTest {
  @Test
  void buildIsEqualPredicateShouldSupportRuntimeExpressions() {
    var expressionPredicate = NodeUtil.buildIsEqualPredicate("a == 0 || b == 1");

    assertTrue(expressionPredicate.test(Integer.valueOf(0), Integer.valueOf(1)));
    assertFalse(expressionPredicate.test(Integer.valueOf(2), Integer.valueOf(3)));
  }
}
