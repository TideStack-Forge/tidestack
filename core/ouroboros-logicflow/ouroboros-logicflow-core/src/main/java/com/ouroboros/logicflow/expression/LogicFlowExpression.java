package com.ouroboros.logicflow.expression;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import io.vavr.control.Either;

import com.ouroboros.logicflow.support.LogicFlowServices;

public interface LogicFlowExpression extends BiFunction<String, Map<String, Object>, Either<Throwable, Object>> {
  LogicFlowExpression DEFAULT_EXPRESSION = Optional.ofNullable(LogicFlowServices.cachedReversedServiceStream(LogicFlowExpression.class)
          .findFirst()
          .orElse(null))
      .orElseGet(FailingLogicFlowExpression::new);

  static LogicFlowExpression getDefault() {
    return DEFAULT_EXPRESSION;
  }

  public static String wrapExpression(String expression) {
    return getDefault().wrap(expression);
  }

  public static Either<Throwable, Object> eval(String expression, Map<String, Object> variables) {
    return getDefault().evaluate(expression, variables);
  }

  Either<Throwable, Object> evaluate(String expression, Map<String, Object> variables);

  @Override
  default Either<Throwable, Object> apply(String expression, Map<String, Object> variables) {
    return evaluate(expression, variables);
  }

  default String wrap(String expression) {
    if (expression == null || expression.startsWith("${") && expression.endsWith("}")) {
      return expression;
    }
    return String.format("${ %s }", expression);
  }
}
