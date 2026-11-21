package com.ouroboros.logicflow.support;

import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;

import io.vavr.control.Either;

import com.ouroboros.logicflow.expression.LogicFlowExpression;

@Priority(1000)
public class TestLogicFlowExpression implements LogicFlowExpression {
  @Override
  public Either<Throwable, Object> evaluate(String expression, Map<String, Object> variables) {
    try {
      return Either.right(evaluateExpression(unwrapExpression(expression), variables));
    } catch (RuntimeException e) {
      return Either.left(e);
    }
  }

  @Override
  public String wrap(String expression) {
    if (expression == null || expression.startsWith("${") && expression.endsWith("}")) {
      return expression;
    }
    return "${ " + expression + " }";
  }

  private Object evaluateExpression(String expression, Map<String, Object> variables) {
    if (expression.isBlank()) {
      return false;
    }
    if ("true".equalsIgnoreCase(expression) || "false".equalsIgnoreCase(expression)) {
      return Boolean.parseBoolean(expression);
    }
    var equalityIndex = expression.indexOf("==");
    if (equalityIndex >= 0) {
      var left = expression.substring(0, equalityIndex).trim();
      var right = expression.substring(equalityIndex + 2).trim();
      return String.valueOf(resolveValue(left, variables)).equals(String.valueOf(resolveValue(right, variables)));
    }
    var inequalityIndex = expression.indexOf("!=");
    if (inequalityIndex >= 0) {
      var left = expression.substring(0, inequalityIndex).trim();
      var right = expression.substring(inequalityIndex + 2).trim();
      return !String.valueOf(resolveValue(left, variables)).equals(String.valueOf(resolveValue(right, variables)));
    }
    return resolveValue(expression, variables);
  }

  private Object resolveValue(String token, Map<String, Object> variables) {
    if (token.length() >= 2 && token.startsWith("'") && token.endsWith("'")) {
      return token.substring(1, token.length() - 1);
    }
    if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
      return token.substring(1, token.length() - 1);
    }
    return Optional.ofNullable(variables.get(token))
        .orElseThrow(() -> new IllegalArgumentException("Missing expression variable: " + token));
  }

  private String unwrapExpression(String expression) {
    if (expression == null) {
      return "";
    }
    var trimmed = expression.trim();
    if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
      return trimmed.substring(2, trimmed.length() - 1).trim();
    }
    return trimmed;
  }
}
