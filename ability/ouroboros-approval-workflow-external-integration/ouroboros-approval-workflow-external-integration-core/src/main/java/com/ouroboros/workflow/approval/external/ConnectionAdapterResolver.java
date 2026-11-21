package com.ouroboros.workflow.approval.external;

/** Resolves a fresh connection-scoped adapter through the registered provider. */
public interface ConnectionAdapterResolver {
  ApprovalWorkflowEngineAdapter resolve(String adapterId, String connectionId);

  <T> T resolvePort(String adapterId, String connectionId, Class<T> portType);
}
