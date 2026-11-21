package com.ouroboros.logicflow.expression;

import java.util.Map;

import jakarta.annotation.Priority;

import io.vavr.control.Either;

import com.ouroboros.expression.EL;

@Priority(1000)
public class CommonExpressionLogicFlowExpression implements LogicFlowExpression {
  @Override
  public Either<Throwable, Object> evaluate(String expression, Map<String, Object> variables) {
    return EL.eval(expression, variables);
  }

  @Override
  public String wrap(String expression) {
    return EL.wrapExpression(expression);
  }
}
