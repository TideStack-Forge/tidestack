package com.ouroboros.businessmodel.action;

/** Persists the Action execution state in its owning business transaction. */
@FunctionalInterface
public interface BusinessActionExecutionAuditRecorder {
  default void begin(BusinessActionExecutionAuditRecord execution) {
  }

  void record(BusinessActionExecutionAuditRecord execution);
}
