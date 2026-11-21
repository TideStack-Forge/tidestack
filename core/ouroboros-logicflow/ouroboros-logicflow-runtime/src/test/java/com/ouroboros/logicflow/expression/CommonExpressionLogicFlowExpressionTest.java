package com.ouroboros.logicflow.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class CommonExpressionLogicFlowExpressionTest {
  @Test
  void evaluatesWithCommonExpressionContextBindings() {
    var expression = new CommonExpressionLogicFlowExpression();
    var result = expression.evaluate(expression.wrap("Converter.toInteger(value)"), Map.of("value", "42"));

    assertTrue(result.isRight(), () -> "Converter binding should evaluate: " + result.getLeft());
    assertEquals(42, result.get());
  }

  @Test
  void isAvailableThroughLogicFlowExpressionService() {
    var result = LogicFlowExpression.eval(
        LogicFlowExpression.wrapExpression("Converter.toInteger(value)"),
        Map.of("value", "42"));

    assertTrue(result.isRight(), () -> "Converter binding should evaluate: " + result.getLeft());
    assertEquals(42, result.get());
  }
}
