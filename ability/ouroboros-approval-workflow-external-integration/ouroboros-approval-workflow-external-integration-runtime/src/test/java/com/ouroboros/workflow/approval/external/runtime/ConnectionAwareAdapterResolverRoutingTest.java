package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventIngestService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalApprovalStartServiceImpl;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartExecutor;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowInstanceService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFact;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartCommand;
import com.ouroboros.workflow.approval.external.runtime.web.EngineAdapterCatalogController;
import com.ouroboros.workflow.approval.external.runtime.web.EngineMappingPermissionGuard;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

import io.vavr.control.Try;

/**
 * Connection-aware resolver 路由测试：start、health、callback 都使用当前 connection 配置。
 */
class ConnectionAwareAdapterResolverRoutingTest {

  private RecordingProvider provider;
  private WorkflowEngineMappingService mappingService;
  private ExternalResultBindingService bindingService;
  private ExternalApprovalStartServiceImpl startService;
  private EngineAdapterCatalogController catalogController;
  private ConnectionAwareAdapterResolver adapterResolver;

  @BeforeEach
  void setUp() {
    provider = new RecordingProvider();
    WorkflowEngineAdapterRegistry registry = new WorkflowEngineAdapterRegistry(
        new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    registry.initialize(List.of(provider));
    ConnectionAwareAdapterResolver resolver = new ConnectionAwareAdapterResolver(
        registry, new RecordingConnectionConfigProvider());

    mappingService = mock(WorkflowEngineMappingService.class);
    bindingService = mock(ExternalResultBindingService.class);
    when(bindingService.findByIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
    when(bindingService.findRunningByBusinessRecord(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding binding = inv.getArgument(0);
      return Try.success(binding);
    });
    ExternalStartFactService startFactService = mock(ExternalStartFactService.class);
    Map<String, ExternalStartFact> journal = new java.util.HashMap<>();
    when(startFactService.reserve(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
      WorkflowEngineMapping mapping = inv.getArgument(1);
      ExternalStartFact fact = new ExternalStartFact();
      fact.setId("fact-" + mapping.getConnectionId());
      fact.setTrackingId(inv.getArgument(7)); fact.setFlowKey(mapping.getFlowKey());
      fact.setIntegrationMode(ExternalStartFact.MODE_RESULT_ONLY);
      fact.setBusinessRecordType(inv.getArgument(2)); fact.setBusinessRecordId(inv.getArgument(3));
      fact.setStatus(ExternalStartFact.STATUS_STARTING);
      fact.setClaimToken("token-" + mapping.getConnectionId());
      journal.put(fact.getId(), fact);
      return Try.success(new ExternalStartFactService.Reservation(fact, true));
    });
    when(startFactService.findById(any())).thenAnswer(inv -> Optional.ofNullable(journal.get(inv.getArgument(0))));
    when(startFactService.markInFlight(any(), any())).thenReturn(true);
    when(startFactService.markReplied(any(), any(), any(), any())).thenReturn(true);
    when(startFactService.markStarted(any(), any(), any())).thenReturn(true);
    WorkflowScopeResolver scopeResolver = mock(WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("default");
    var worker = new ResultOnlyStartExecutor(bindingService, startFactService, resolver,
        mock(InstanceEventDispatcher.class), null);
    var providers = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
    providers.registerSingleton("builtIn", mock(com.ouroboros.workflow.approval.BuiltInFlowInstanceService.class));
    var router = new RoutedFlowInstanceService(
        providers.getBeanProvider(com.ouroboros.workflow.approval.BuiltInFlowInstanceService.class), mappingService,
        registry, resolver, mock(com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService.class),
        bindingService, mock(InstanceEventDispatcher.class), null, "reject", scopeResolver,
        mock(com.ouroboros.workflow.approval.external.runtime.event.ExternalUserIdentityResolver.class), startFactService,
        mock(com.ouroboros.workflow.approval.external.runtime.action.ExternalActionExecutionService.class), null, worker);
    startService = new ExternalApprovalStartServiceImpl(router);
    catalogController = new EngineAdapterCatalogController(
        registry, resolver, mock(EnterpriseConnectionCatalog.class),
        mock(EngineMappingPermissionGuard.class), com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry.of(List.of()));
    adapterResolver = resolver;
  }

  @Test
  void startHealthAndCallbackUseRequestedConnectionConfiguration() {
    startWith("conn-a");
    startWith("conn-b");

    catalogController.checkHealth("recording", "conn-a");
    catalogController.checkHealth("recording", "conn-b");

    for (String id : List.of("conn-a", "conn-b")) {
      assertThat(adapterResolver.resolvePort("recording", id, ExternalCallbackCodec.class)
          .verifyAndParseCallback(new ExternalCallbackRequest("recording", id, Map.of(), "{}"))
          .isSuccess()).isTrue();
    }

    assertThat(provider.startedConnections).containsExactly("conn-a=marker-conn-a", "conn-b=marker-conn-b");
    assertThat(provider.healthConnections).containsExactly("conn-a=marker-conn-a", "conn-b=marker-conn-b");
    assertThat(provider.callbackConnections).containsExactly("conn-a=marker-conn-a", "conn-b=marker-conn-b");
  }

  private void startWith(String connectionId) {
    when(mappingService.findActive("default", "flow-a"))
        .thenReturn(Optional.of(mapping(connectionId)));
    Try<?> result = startService.start(new ResultOnlyStartCommand(
        "flow-a", "supplier", "sup-" + connectionId, "user-1",
        Map.of("amount", 100), "idem-" + connectionId, null, null, null));
    assertThat(result.isSuccess()).isTrue();
  }

  private WorkflowEngineMapping mapping(String connectionId) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("m-" + connectionId);
    mapping.setScope("default");
    mapping.setFlowKey("flow-a");
    mapping.setAdapterId("recording");
    mapping.setConnectionId(connectionId);
    mapping.setAuthority("EXTERNAL_ENGINE");
    mapping.setExternalDefinitionId("DEF-1");
    mapping.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    return mapping;
  }

  static final class RecordingConnectionConfigProvider extends EnterpriseConnectionCatalog {
    RecordingConnectionConfigProvider() {
      super(null, Optional.empty(), List.of(), List.of());
    }

    @Override
    public Optional<ResolvedEnterpriseConnection> resolve(
        String connectionId, String expectedProvider) {
      return Optional.of(new ResolvedEnterpriseConnection(
          connectionId, Map.of("provider", expectedProvider, "testMarker", "marker-" + connectionId), null));
    }
  }

  static final class RecordingProvider implements ApprovalWorkflowEngineAdapterProvider {
    final List<String> startedConnections = new ArrayList<>();
    final List<String> healthConnections = new ArrayList<>();
    final List<String> callbackConnections = new ArrayList<>();

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("recording", "recording", "1.0", Set.of(), Set.of(Capability.CALLBACK, Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new RecordingAdapter(connection, this);
    }
  }

  static final class RecordingAdapter
      implements ApprovalWorkflowEngineAdapter, ExternalWorkflowStarter, ExternalCallbackCodec {
    private final ResolvedEnterpriseConnection connection;
    private final RecordingProvider provider;

    RecordingAdapter(ResolvedEnterpriseConnection connection, RecordingProvider provider) {
      this.connection = connection;
      this.provider = provider;
    }

    @Override
    public Try<ExternalHealth> health() {
      provider.healthConnections.add(connectionKey());
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalStartResult> start(ExternalStartCommand command) {
      provider.startedConnections.add(connectionKey());
      return Try.success(new ExternalStartResult("ext-" + connection.connectionId(), null, null));
    }

    @Override
    public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
      provider.callbackConnections.add(connectionKey());
      return Try.success(List.of(new ExternalCallbackEnvelope(
          "evt-" + connection.connectionId(), "recording", connection.connectionId(),
          "ext-" + connection.connectionId(), null, null, null, "APPROVED", "approved", null)));
    }

    private String connectionKey() {
      return connection.connectionId() + "=" + connection.config().get("testMarker");
    }
  }
}
