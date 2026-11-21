package com.ouroboros.logicflow.diagnostic;

import java.util.Objects;

/** Immutable identity of one flow invocation; capture policy remains outside the engine. */
public record FlowExecutionContext(String invocationId, String parentInvocationId, String callerNodeName, String scope) {
  public FlowExecutionContext {
    Objects.requireNonNull(invocationId, "invocationId");
    Objects.requireNonNull(scope, "scope");
  }

  public FlowExecutionContext withScope(String value) {
    return new FlowExecutionContext(invocationId, parentInvocationId, callerNodeName, value);
  }
}
