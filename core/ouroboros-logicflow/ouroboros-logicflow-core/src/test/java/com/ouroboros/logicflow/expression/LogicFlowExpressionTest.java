package com.ouroboros.logicflow.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class LogicFlowExpressionTest {

  @Test
  void wrapsPlainExpressionAndKeepsWrappedExpression() {
    var expression = new FailingLogicFlowExpression();

    assertEquals("${ name }", expression.wrap("name"));
    assertEquals("${name}", expression.wrap("${name}"));
    assertEquals(null, expression.wrap(null));
  }

  @Test
  void failsExplicitlyWhenNoRuntimeAdapterIsAvailable() {
    var result = new FailingLogicFlowExpression().evaluate("${ name }", Map.of("name", "Ada"));

    assertTrue(result.isLeft());
    assertInstanceOf(LogicFlowExpressionException.class, result.getLeft());
  }
}
