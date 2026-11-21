package com.ouroboros.logicflow.expression;

import java.util.Map;

import io.vavr.control.Either;

public class FailingLogicFlowExpression implements LogicFlowExpression {
  @Override
  public Either<Throwable, Object> evaluate(String expression, Map<String, Object> variables) {
    return Either.left(new LogicFlowExpressionException("No LogicFlowExpression runtime adapter is available"));
  }
}
