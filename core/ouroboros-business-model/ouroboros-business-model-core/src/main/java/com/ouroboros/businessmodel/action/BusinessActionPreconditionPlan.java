package com.ouroboros.businessmodel.action;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.expression.EL;
import com.ouroboros.expression.PredicatePlan;

import io.vavr.control.Either;
import io.vavr.control.Try;

/** The published predicate; invalid candidates retain diagnostics for model validation. */
public final class BusinessActionPreconditionPlan {
  private final boolean configured;
  private final Either<Throwable, PredicatePlan> predicate;
  private final String message;

  public BusinessActionPreconditionPlan(Object definition) {
    configured = definition != null;
    predicate = Try.of(() -> {
      if (!configured) { return null; }
      if (!(definition instanceof Map<?, ?> values)
          || !Set.of("expression", "message").containsAll(values.keySet())
          || !(values.get("expression") instanceof String expression)
          || (values.containsKey("message") && (!(values.get("message") instanceof String text) || text.isBlank()))) {
        throw new IllegalArgumentException("precondition requires expression and an optional static message");
      }
      PredicatePlan plan = EL.compilePredicate(expression, Set.of("input", "objectStates"))
          .getOrElseThrow(error -> new IllegalArgumentException("Invalid restricted precondition", error));
      for (var path : plan.accessPaths()) {
        if (path.get(0).equals("objectStates") && (path.size() < 3 || !path.get(1).startsWith("/"))) {
          throw new IllegalArgumentException("objectStates requires a literal role JSON Pointer and field");
        }
      }
      return plan;
    }).toEither();
    message = definition instanceof Map<?, ?> values && values.get("message") instanceof String text
        ? text : "业务 Action 前置条件不满足";
  }

  public boolean isValid() { return predicate.isRight(); }

  public boolean isConfigured() { return configured; }

  public List<List<String>> accessPaths() {
    requireValid();
    return configured ? predicate.get().accessPaths() : List.of();
  }

  public boolean requiresObjectStates() {
    return accessPaths().stream().anyMatch(path -> path.get(0).equals("objectStates"));
  }

  public String message() { return message; }

  public void requireValid() {
    if (predicate.isLeft()) { throw new BusinessException("业务 Action 前置条件配置无效", predicate.getLeft()); }
  }

  public void requireSatisfied(Map<String, Object> input, Map<String, ?> states) {
    requireValid();
    if (configured && !predicate.get().evaluate(Map.of("input", input, "objectStates", states))
        .getOrElseThrow(error -> new BusinessException("业务 Action 前置条件求值失败", error))) {
      throw new BusinessActionPreconditionException(message);
    }
  }
}
