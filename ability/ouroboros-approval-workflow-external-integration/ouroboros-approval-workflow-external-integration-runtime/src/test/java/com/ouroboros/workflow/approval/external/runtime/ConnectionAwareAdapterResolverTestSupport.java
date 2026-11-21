package com.ouroboros.workflow.approval.external.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

/**
 * 测试侧 connection-aware resolver 夹具。
 */
public final class ConnectionAwareAdapterResolverTestSupport {

  private ConnectionAwareAdapterResolverTestSupport() {
  }

  public static ConnectionAwareAdapterResolver resolver(WorkflowEngineAdapterRegistry registry) {
    return resolver(registry, connectionConfigProvider());
  }

  public static ConnectionAwareAdapterResolver resolver(
      WorkflowEngineAdapterRegistry registry,
      EnterpriseConnectionCatalog connectionConfigProvider) {
    return new ConnectionAwareAdapterResolver(registry, connectionConfigProvider);
  }

  public static EnterpriseConnectionCatalog connectionConfigProvider() {
    return new EnterpriseConnectionCatalog(null, Optional.empty(), java.util.List.of(), java.util.List.of()) {
      @Override
      public Optional<ResolvedEnterpriseConnection> resolve(String connectionId) {
        return Optional.of(connection("fake-external-engine", connectionId));
      }

      @Override
      public Optional<ResolvedEnterpriseConnection> resolve(
          String connectionId, String expectedProvider) {
        return Optional.of(connection(expectedProvider == null ? "fake-external-engine" : expectedProvider, connectionId));
      }
    };
  }

  public static ResolvedEnterpriseConnection connection(String adapterId, String connectionId) {
    Map<String, Object> config = adapterId == null ? Map.of() : Map.of("provider", adapterId);
    return new ResolvedEnterpriseConnection(
        connectionId, config, null);
  }

  public static RegisteredAdapter registered(
      String adapterId,
      ApprovalWorkflowEngineAdapter adapter,
      Set<Capability> capabilities) {
    return registered(descriptor(adapterId), adapter, capabilities);
  }

  public static RegisteredAdapter registered(
      ApprovalWorkflowEngineAdapterDescriptor descriptor,
      ApprovalWorkflowEngineAdapter adapter,
      Set<Capability> capabilities) {
    return new RegisteredAdapter(descriptor, capabilities, provider(descriptor, adapter));
  }

  public static ApprovalWorkflowEngineAdapterProvider provider(
      ApprovalWorkflowEngineAdapterDescriptor descriptor,
      ApprovalWorkflowEngineAdapter adapter) {
    return new ApprovalWorkflowEngineAdapterProvider() {
      @Override
      public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
        return adapter;
      }
    };
  }

  private static ApprovalWorkflowEngineAdapterDescriptor descriptor(String adapterId) {
    return new ApprovalWorkflowEngineAdapterDescriptor(adapterId, "test", "1.0", Set.of("approve", "reject"), Set.of(), Set.of());
  }
}
