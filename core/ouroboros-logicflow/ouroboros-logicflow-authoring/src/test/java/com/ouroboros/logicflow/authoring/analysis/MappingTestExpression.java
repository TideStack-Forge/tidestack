package com.ouroboros.logicflow.authoring.analysis;

/** Runs the same SpEL engine used by platform logicflow expressions. */
public final class MappingTestExpression implements com.ouroboros.logicflow.expression.LogicFlowExpression {
  public io.vavr.control.Either<Throwable, Object> evaluate(String source, java.util.Map<String, Object> variables) {
    return com.ouroboros.expression.EL.eval(source, variables);
  }
}
