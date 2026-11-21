package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.CcTarget;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalApprovalStartServiceImpl;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartExecutor;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowInstanceService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFact;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartCommand;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartResult;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;

import io.vavr.control.Try;

/**
 * M5 结果型启动服务测试：路由判定、幂等、外部失败不回退、STARTED 统一结果事件。
 */
class ExternalApprovalStartServiceImplTest {

  private WorkflowEngineMappingService mappingService;
  private ExternalResultBindingService bindingService;
  private ExternalStartFactService startFactService;
  private Map<String, ExternalStartFact> startFacts;
  private WorkflowEngineAdapterRegistry registry;
  private ConnectionAwareAdapterResolver adapterResolver;
  private InstanceEventDispatcher eventDispatcher;
  private FakeResultOnlyEngineAdapter fakeAdapter;
  private ExternalApprovalStartServiceImpl service;
  private ResultOnlyStartExecutor executor;

  @BeforeEach
  void setUp() {
    mappingService = mock(WorkflowEngineMappingService.class);
    bindingService = mock(ExternalResultBindingService.class);
    startFacts = new LinkedHashMap<>();
    startFactService = new InMemoryStartFactService(mockStartFactDataModel(startFacts));
    eventDispatcher = mock(InstanceEventDispatcher.class);

    fakeAdapter = new FakeResultOnlyEngineAdapter();
    registry = new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(java.util.List.of()));
    registry.initialize(java.util.List.of(new ProviderFor(fakeAdapter)));
    adapterResolver = ConnectionAwareAdapterResolverTestSupport.resolver(registry);

    com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver scopeResolver =
        mock(com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("default");
    service = withRegistry(new ResultOnlyStartExecutor(
        bindingService, startFactService, adapterResolver, eventDispatcher, null));
  }

  /** registry 在生产由 Spring 注入；单测里显式注入，保证派生接入模式可用。 */
  private ExternalApprovalStartServiceImpl withRegistry(ResultOnlyStartExecutor target) {
    return withRegistry(target, registry);
  }

  private ExternalApprovalStartServiceImpl withRegistry(
      ResultOnlyStartExecutor target, WorkflowEngineAdapterRegistry targetRegistry) {
    executor = target;
    var router = new RoutedFlowInstanceService(
        providerOf(mock(com.ouroboros.workflow.approval.BuiltInFlowInstanceService.class)),
        mappingService, targetRegistry, adapterResolver,
        mock(com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService.class),
        bindingService, eventDispatcher, null, "reject", () -> "default",
        mock(com.ouroboros.workflow.approval.external.runtime.event.ExternalUserIdentityResolver.class),
        startFactService, mock(com.ouroboros.workflow.approval.external.runtime.action.ExternalActionExecutionService.class),
        null, target);
    return new ExternalApprovalStartServiceImpl(router);
  }

  @Test
  void noActiveMappingFails() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.empty());

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.WORKFLOW_ROUTE_NOT_CONFIGURED);
  }

  @Test
  void leaseRenewalRequiresCurrentUnexpiredClaim() {
    ExternalStartFact fact = new ExternalStartFact();
    fact.setId("fact-1");
    fact.setStatus(ExternalStartFact.STATUS_STARTING);
    fact.setClaimToken("claim-1");
    fact.setClaimUntil(java.time.LocalDateTime.now().plusSeconds(30));
    startFacts.put(fact.getId(), fact);

    java.time.LocalDateTime previous = fact.getClaimUntil();
    assertThat(startFactService.renewClaim(fact.getId(), "claim-1", 60)).isTrue();
    assertThat(fact.getClaimUntil()).isAfter(previous);

    fact.setClaimUntil(java.time.LocalDateTime.now().minusSeconds(1));
    assertThat(startFactService.renewClaim(fact.getId(), "claim-1", 60)).isFalse();
  }

  @Test
  void mappingLookupFailureFailsBeforeBindingOrAdapterWork() {
    when(mappingService.findActive("default", "flow-a")).thenThrow(new BusinessException(
        "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED + "] 查询 ACTIVE 映射失败"));

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED);
    assertThat(fakeAdapter.startedCommands()).isEmpty();
    verify(bindingService, never()).create(any());
  }

  @Test
  void adapterWithoutStarterPortIsRejected() {
    // 结果型启动入口的准入由能力校验承担：没有 ExternalWorkflowStarter 端口的适配器必须被拒绝，
    // 而不是靠一个可填的接入模式字段。
    WorkflowEngineAdapterRegistry readOnlyRegistry =
        new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(java.util.List.of()));
    readOnlyRegistry.initialize(java.util.List.of(new ReadOnlyProvider()));
    ConnectionAwareAdapterResolver readOnlyResolver =
        ConnectionAwareAdapterResolverTestSupport.resolver(readOnlyRegistry);
    service = withRegistry(new ResultOnlyStartExecutor(
        bindingService, startFactService, readOnlyResolver, eventDispatcher, null), readOnlyRegistry);
    when(mappingService.findActive("default", "flow-a")).thenReturn(
        Optional.of(mapping("read-only")));

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("不支持端口");
  }

  /** 只有查询端口、没有发起端口的适配器。 */
  static final class ReadOnlyProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("read-only", "read-only", "1.0", Set.of(), Set.of(Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter create(
        ResolvedEnterpriseConnection connection) {
      return new com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter() {
        @Override
        public Try<com.ouroboros.workflow.approval.external.ExternalHealth> health() {
          return Try.success(new com.ouroboros.workflow.approval.external.ExternalHealth(true, "ok"));
        }
      };
    }
  }

  @Test
  void duplicateStartFactReturnsStartedResultAndSkipsSecondAdapterCall() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding b = inv.getArgument(0);
      return Try.success(b);
    });

    Try<ResultOnlyStartResult> first = service.start(command());
    Try<ResultOnlyStartResult> second = service.start(command());

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    assertThat(second.get().trackingId()).isEqualTo(startFacts.values().iterator().next().getTrackingId());
    assertThat(second.get().status()).isEqualTo(ExternalStartFact.STATUS_STARTED);
    assertThat(fakeAdapter.startedCommands()).hasSize(1);
    verify(bindingService, times(1)).create(any());
  }

  @Test
  void resultOnlyStartBindsApprovalCaseExecution() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.create(any())).thenAnswer(invocation -> Try.success(invocation.getArgument(0)));
    ApprovalCaseService caseService = mock(ApprovalCaseService.class);
    startFactService = org.mockito.Mockito.spy(startFactService);
    org.mockito.Mockito.doAnswer(invocation -> {
      var reserved = (Try<?>) invocation.callRealMethod();
      var actual = ExternalStartFactService.Reservation.class.cast(reserved.get());
      actual.fact().setApprovalCaseId("case-1"); actual.fact().setSubmissionRound(2);
      return Try.success(actual);
    }).when(startFactService).reserve(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    service = withRegistry(new ResultOnlyStartExecutor(
        bindingService, startFactService, adapterResolver, eventDispatcher, null, providerOf(caseService)));

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isSuccess()).isTrue();
    verify(caseService).bindExecution("case-1", 2, result.get().trackingId());
  }

  @Test
  void concurrentDuplicateStartFactsTriggerSingleRemoteStart() throws Exception {
    CountDownLatch bothMappingLookupsReached = new CountDownLatch(2);
    when(mappingService.findActive("default", "flow-a")).thenAnswer(inv -> {
      bothMappingLookupsReached.countDown();
      assertThat(bothMappingLookupsReached.await(5, TimeUnit.SECONDS)).isTrue();
      return Optional.of(mapping());
    });
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding b = inv.getArgument(0);
      return Try.success(b);
    });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Try<ResultOnlyStartResult>> first = executor.submit(() -> service.start(command()));
      Future<Try<ResultOnlyStartResult>> second = executor.submit(() -> service.start(command()));

      List<Try<ResultOnlyStartResult>> results = List.of(
          first.get(5, TimeUnit.SECONDS),
          second.get(5, TimeUnit.SECONDS));

      assertThat(results).allSatisfy(result -> assertThat(result.isSuccess()).isTrue());
      assertThat(results.stream().map(result -> result.get().status()))
          .contains(ExternalStartFact.STATUS_STARTED);
      assertThat(fakeAdapter.startedCommands()).hasSize(1);
      assertThat(startFacts.values()).singleElement().satisfies(fact -> {
        assertThat(fact.getStatus()).isEqualTo(ExternalStartFact.STATUS_STARTED);
        assertThat(fact.getRemoteCallPhase()).isEqualTo(ExternalStartFact.PHASE_REPLIED);
        assertThat(results.stream().map(result -> result.get().trackingId())).containsOnly(fact.getTrackingId());
      });
      verify(bindingService, times(1)).create(any());
      verify(eventDispatcher, times(1)).enqueueEvent(any());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void existingStartingStartFactReturnsStartingResultAndSkipsAdapterCall() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    ExternalStartFact existing = new ExternalStartFact();
    existing.setId("fact-in-flight");
    existing.setStatus(ExternalStartFact.STATUS_STARTING);
    existing.setClaimToken("owner-token");
    ExternalStartFactService claimedElsewhere = mock(ExternalStartFactService.class);
    when(claimedElsewhere.reserve(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Try.success(new ExternalStartFactService.Reservation(existing, false)));
    service = withRegistry(new ResultOnlyStartExecutor(
        bindingService, claimedElsewhere, adapterResolver, eventDispatcher, null));

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().status()).isEqualTo(ExternalStartFact.STATUS_STARTING);
    assertThat(result.get().startFactId()).isEqualTo("fact-in-flight");
    assertThat(fakeAdapter.startedCommands()).isEmpty();
    verify(claimedElsewhere, never()).markInFlight(any(), any());
    verify(bindingService, never()).create(any());
  }

  @Test
  void remoteTimeoutMarksStartUnknownWithoutPersistingBinding() {
    fakeAdapter.setStartFailure(ExternalOperationFailure.unknown(
        "external start result unknown", new IOException("timed out while waiting")));
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().status()).isEqualTo(ExternalStartFact.STATUS_START_UNKNOWN);
    assertThat(result.get().trackingId()).isNotBlank();
    assertThat(result.get().externalInstanceId()).isNull();
    assertThat(fakeAdapter.startedCommands()).hasSize(1);
    assertThat(startFacts.values()).singleElement().satisfies(fact -> {
      assertThat(fact.getStatus()).isEqualTo(ExternalStartFact.STATUS_START_UNKNOWN);
      assertThat(fact.getRemoteCallPhase()).isEqualTo(ExternalStartFact.PHASE_IN_FLIGHT);
      assertThat(fact.getClaimToken()).isNull();
      assertThat(fact.getClaimUntil()).isNull();
      assertThat(fact.getLastError()).contains("IOException").contains("timed out");
    });
    verify(bindingService, never()).create(any());
    verify(eventDispatcher, never()).enqueueEvent(any());
  }

  @Test
  void adapterThrownExceptionMarksStartUnknownInsteadOfLeavingStartingFact() {
    fakeAdapter.setStartException(new IllegalStateException("socket closed"));
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().status()).isEqualTo(ExternalStartFact.STATUS_START_UNKNOWN);
    assertThat(startFacts.values()).singleElement().satisfies(fact -> {
      assertThat(fact.getStatus()).isEqualTo(ExternalStartFact.STATUS_START_UNKNOWN);
      assertThat(fact.getRemoteCallPhase()).isEqualTo(ExternalStartFact.PHASE_IN_FLIGHT);
      assertThat(fact.getLastError()).contains("IllegalStateException").contains("socket closed");
    });
    verify(bindingService, never()).create(any());
  }

  @Test
  void lateRemoteResponseCanBeAdoptedOnlyForTheSameRequestFingerprint() {
    WorkflowEngineMapping mapping = mapping();
    ExternalStartFactService.Reservation reservation = startFactService.reserve(
        "default", mapping, "supplier", "sup-1", "idem-late", "fingerprint-late",
        ExternalStartFact.MODE_RESULT_ONLY, "tracking-late", "user-1", null, "{}").get();
    ExternalStartFact fact = reservation.fact();
    assertThat(startFactService.markInFlight(fact.getId(), fact.getClaimToken())).isTrue();
    String oldToken = fact.getClaimToken();

    var adopted = startFactService.adoptLateResponse(
        fact.getId(), "fingerprint-late", "external-late", "https://external/later", 60);

    assertThat(adopted).isPresent();
    assertThat(adopted.get().claimToken()).isNotEqualTo(oldToken);
    assertThat(adopted.get().fact().getExternalInstanceId()).isEqualTo("external-late");
    assertThat(adopted.get().fact().getRemoteCallPhase()).isEqualTo(ExternalStartFact.PHASE_REPLIED);
    assertThat(startFactService.adoptLateResponse(
        fact.getId(), "different-fingerprint", "external-other", null, 60)).isEmpty();
  }

  @Test
  void adapterStartFailureDoesNotPersistBinding() {
    fakeAdapter.setFailStart(true);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.findRunningByBusinessRecord(any(), any(), any(), any())).thenReturn(Optional.empty());

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE);
    verify(bindingService, never()).create(any());
    verify(eventDispatcher, never()).enqueueEvent(any());
  }

  @Test
  void failedStartReleasesBusinessReservationForANewRequest() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding binding = inv.getArgument(0);
      return Try.success(binding);
    });
    fakeAdapter.setFailStart(true);

    Try<ResultOnlyStartResult> failed = service.start(command("idem-failed"));
    fakeAdapter.setFailStart(false);
    Try<ResultOnlyStartResult> retried = service.start(command("idem-retry"));

    assertThat(failed.isFailure()).isTrue();
    assertThat(retried.isSuccess()).isTrue();
    assertThat(retried.get().status()).isEqualTo(ExternalStartFact.STATUS_STARTED);
    assertThat(fakeAdapter.startedCommands()).hasSize(2);
    assertThat(startFacts.values()).hasSize(2);
    assertThat(startFacts.values().stream()
        .filter(fact -> ExternalStartFact.STATUS_FAILED.equals(fact.getStatus()))
        .findFirst().orElseThrow().getBusinessStartRouteKey())
        .isNotEqualTo(startFactService.businessStartRouteKey(
            "default", "flow-a", "supplier", "sup-1"));
  }

  @Test
  void terminalReleaseKeepsHistoryAndAllowsANewBusinessStart() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding binding = inv.getArgument(0);
      return Try.success(binding);
    });

    Try<ResultOnlyStartResult> first = service.start(command("idem-first"));
    boolean released = startFactService.releaseBusinessReservation(
        "default", "flow-a", "supplier", "sup-1", first.get().trackingId());
    Try<ResultOnlyStartResult> second = service.start(command("idem-second"));

    assertThat(released).isTrue();
    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    assertThat(second.get().trackingId()).isNotEqualTo(first.get().trackingId());
    assertThat(fakeAdapter.startedCommands()).hasSize(2);
    assertThat(startFacts.values()).hasSize(2);
    assertThat(startFacts.values()).extracting(ExternalStartFact::getStatus)
        .containsOnly(ExternalStartFact.STATUS_STARTED);
  }

  @Test
  void successfulStartPersistsBindingAndPublishesStartedEvent() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.findRunningByBusinessRecord(any(), any(), any(), any())).thenReturn(Optional.empty());
    ArgumentCaptor<ExternalResultBinding> bindingCaptor = ArgumentCaptor.forClass(ExternalResultBinding.class);
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding b = inv.getArgument(0);
      return Try.success(b);
    });

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().trackingId()).isEqualTo(startFacts.values().iterator().next().getTrackingId());
    assertThat(result.get().externalInstanceId()).isEqualTo("ext-1");
    assertThat(fakeAdapter.startedCommands()).hasSize(1);
    assertThat(fakeAdapter.startedCommands().get(0).businessRecord())
        .isEqualTo(new ExternalStartCommand.ExternalBusinessRecord("supplier", "sup-1"));
    assertThat(fakeAdapter.startedCommands().get(0).externalDefinitionRef().definitionId()).isEqualTo("DEF-1");
    assertThat(fakeAdapter.startedCommands().get(0).idempotencyKey())
        .isEqualTo(startFacts.values().iterator().next().getRequestFingerprint())
        .isNotEqualTo("idem-1");

    verify(bindingService).create(bindingCaptor.capture());
    ExternalResultBinding created = bindingCaptor.getValue();
    assertThat(created.getStatus()).isEqualTo(ExternalResultBinding.STATUS_RUNNING);
    assertThat(created.getBusinessRecordType()).isEqualTo("supplier");
    assertThat(created.getBusinessRecordId()).isEqualTo("sup-1");
    assertThat(created.getExternalInstanceId()).isEqualTo("ext-1");

    ArgumentCaptor<FlowInstanceEvent> eventCaptor = ArgumentCaptor.forClass(FlowInstanceEvent.class);
    verify(eventDispatcher).enqueueEvent(eventCaptor.capture());
    FlowInstanceEvent event = eventCaptor.getValue();
    assertThat(event.getEventType()).isEqualTo(EventType.STARTED);
    assertThat(event.getInstanceId()).isEqualTo(startFacts.values().iterator().next().getTrackingId());
    assertThat(event.getTrackingId()).isEqualTo(startFacts.values().iterator().next().getTrackingId());
    assertThat(event.getBusinessContext())
        .isEqualTo(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
  }

  @Test
  void existingExternalInstanceWithDifferentBusinessIdentityIsRejected() {
    WorkflowEngineMapping mapping = mapping();
    ExternalResultBinding existing = binding("other-tracking", "ext-1");
    existing.setScope("default");
    existing.setFlowKey("flow-a");
    existing.setMappingId(mapping.getId());
    existing.setMappingVersion(mapping.getVersion());
    existing.setAdapterId(mapping.getAdapterId());
    existing.setConnectionId(mapping.getConnectionId());
    existing.setBusinessRecordType("supplier");
    existing.setBusinessRecordId("different-record");
    existing.setIdempotencyKey("other-idempotency");
    existing.setExternalDefinitionId(mapping.getExternalDefinitionId());
    when(bindingService.findByExternalInstance("fake-result-only", "conn-1", "ext-1"))
        .thenReturn(Optional.of(existing));

    Try<ResultOnlyStartResult> result = executor.persistStart(
        command(), mapping,
        new com.ouroboros.workflow.approval.external.ExternalStartResult(
            "ext-1", "https://external.example/inst/1", null),
        "default", "supplier", "sup-1", "tracking-request", "fact-1");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    verify(bindingService, never()).create(any());
    verify(eventDispatcher, never()).enqueueEvent(any());
  }

  @Test
  void aTransactionManagerWithoutAJournalResourceCannotPretendToCommitStart() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.findRunningByBusinessRecord(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding b = inv.getArgument(0);
      return Try.success(b);
    });
    org.springframework.transaction.PlatformTransactionManager transactionManager =
        mock(org.springframework.transaction.PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
    com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver txScopeResolver =
        mock(com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver.class);
    when(txScopeResolver.resolve()).thenReturn("default");
    service = withRegistry(new ResultOnlyStartExecutor(
        bindingService, startFactService, adapterResolver, eventDispatcher, transactionManager));

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().status()).isEqualTo(ExternalStartFact.STATUS_START_UNKNOWN);
    assertThat(startFacts.values().iterator().next().getLastError()).contains("源模型不可用");
    verify(transactionManager).rollback(any());
  }

  @Test
  void successfulRemoteStartMarksUnknownWhenBindingCreationFails() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.findRunningByBusinessRecord(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(bindingService.create(any())).thenReturn(Try.failure(
        new com.ouroboros.exception.BusinessException("绑定落库失败")));
    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().status()).isEqualTo(ExternalStartFact.STATUS_START_UNKNOWN);
    assertThat(result.get().externalInstanceId()).isEqualTo("ext-1");
    assertThat(startFacts.values()).singleElement().satisfies(fact -> {
      assertThat(fact.getStatus()).isEqualTo(ExternalStartFact.STATUS_START_UNKNOWN);
      assertThat(fact.getRemoteCallPhase()).isEqualTo(ExternalStartFact.PHASE_REPLIED);
      assertThat(fact.getExternalInstanceId()).isEqualTo("ext-1");
      assertThat(fact.getExternalUrl()).isEqualTo("https://external.example/inst/1");
      assertThat(fact.getLastError()).contains("绑定落库失败");
    });
    verify(eventDispatcher, never()).enqueueEvent(any());
  }

  private ResultOnlyStartCommand command() {
    return command("idem-1");
  }

  private ResultOnlyStartCommand command(String idempotencyKey) {
    return new ResultOnlyStartCommand(
        "flow-a", "supplier", "sup-1", "user-1", Map.of("amount", 100), idempotencyKey,
        null, null, null);
  }

  private WorkflowEngineMapping mapping() {
    return mapping("fake-result-only");
  }

  private WorkflowEngineMapping mapping(String adapterId) {
    WorkflowEngineMapping m = new WorkflowEngineMapping();
    m.setId("m-1");
    m.setScope("default");
    m.setFlowKey("flow-a");
    m.setAdapterId(adapterId);
    m.setConnectionId("conn-1");
    m.setExternalDefinitionNamespace("corp-a");
    m.setAuthority("EXTERNAL_ENGINE");
    m.setExternalDefinitionId("DEF-1");
    m.setVersion(1);
    m.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    return m;
  }

  private ExternalResultBinding binding(String trackingId, String externalInstanceId) {
    ExternalResultBinding b = new ExternalResultBinding();
    b.setTrackingId(trackingId);
    b.setExternalInstanceId(externalInstanceId);
    b.setStatus(ExternalResultBinding.STATUS_RUNNING);
    return b;
  }

  private <T> org.springframework.beans.factory.ObjectProvider<T> providerOf(T value) {
    return new org.springframework.beans.factory.ObjectProvider<T>() {
      @Override
      public T getObject() {
        return value;
      }

      @Override
      public Stream<T> stream() {
        return Stream.of(value);
      }
    };
  }

  @Test
  void sameIdempotencyKeyWithDifferentRequestFailsBeforeAdapterCall() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding b = inv.getArgument(0);
      b.setTrackingId("tracking-key");
      return Try.success(b);
    });

    Try<ResultOnlyStartResult> first = service.start(command());
    ResultOnlyStartCommand changed = new ResultOnlyStartCommand(
        "flow-a", "supplier", "sup-1", "user-1", Map.of("amount", 200), "idem-1", null, null, null);
    Try<ResultOnlyStartResult> second = service.start(changed);

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isFailure()).isTrue();
    assertThat(second.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    assertThat(fakeAdapter.startedCommands()).hasSize(1);
  }

  @Test
  void resultBindingConfigExtractsBusinessRecordFromFormData() {
    WorkflowEngineMapping m = mapping();
    m.setResultBindingJson("""
        {"businessRecordType":"expense","businessRecordIdPath":"$.supplierId"}
        """);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(m));
    when(bindingService.findRunningByBusinessRecord(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding b = inv.getArgument(0);
      return Try.success(b);
    });

    var commandWithForm = new ResultOnlyStartCommand(
        "flow-a", "ignored-type", "ignored-id", "user-1",
        Map.of("supplierId", "sup-99"), "idem-1", null, null, null);
    Try<ResultOnlyStartResult> result = service.start(commandWithForm);

    assertThat(result.isSuccess()).isTrue();
    assertThat(fakeAdapter.startedCommands()).hasSize(1);
    assertThat(fakeAdapter.startedCommands().get(0).businessRecord())
        .isEqualTo(new ExternalStartCommand.ExternalBusinessRecord("expense", "sup-99"));
  }

  @Test
  void resultBindingConfigWithUnresolvablePathFails() {
    WorkflowEngineMapping m = mapping();
    m.setResultBindingJson("""
        {"businessRecordType":"expense","businessRecordIdPath":"$.missing"}
        """);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(m));
    when(bindingService.findRunningByBusinessRecord(any(), any(), any(), any())).thenReturn(Optional.empty());

    Try<ResultOnlyStartResult> result = service.start(command());

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("无法按映射配置提取业务记录 ID");
    verify(bindingService, never()).create(any());
  }

  @Test
  void unifiedStartContractFieldsAreForwardedToAdapter() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    when(bindingService.findRunningByBusinessRecord(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(bindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding b = inv.getArgument(0);
      return Try.success(b);
    });

    var commandWithExtras = new ResultOnlyStartCommand(
        "flow-a", "supplier", "sup-1", "user-1", Map.of("amount", 100), "idem-1",
        java.util.List.of(new CcTarget("user", "cc-1", CcTarget.POSITION_START)),
        Map.of("node-1", java.util.List.of("user-9")),
        Map.of("node-2", java.util.List.of("user-8")));
    Try<ResultOnlyStartResult> result = service.start(commandWithExtras);

    assertThat(result.isSuccess()).isTrue();
    var external = fakeAdapter.startedCommands().get(0);
    assertThat(external.cc()).containsExactly(new CcTarget("user", "cc-1", CcTarget.POSITION_START));
    assertThat(external.approvers()).isEqualTo(Map.of("node-1", java.util.List.of("user-9")));
    assertThat(external.targetSelectActioners()).isEqualTo(Map.of("node-2", java.util.List.of("user-8")));
    assertThat(external.externalDefinitionRef().namespace()).isEqualTo("corp-a");
    assertThat(external.externalDefinitionRef().definitionId()).isEqualTo("DEF-1");
  }

  static final class ProviderFor implements ApprovalWorkflowEngineAdapterProvider {
    private final FakeResultOnlyEngineAdapter adapter;

    ProviderFor(FakeResultOnlyEngineAdapter adapter) {
      this.adapter = adapter;
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("fake-result-only", "fake", "1.0", Set.of("approve", "reject"), Set.of(Capability.POLLING, Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter create(
        ResolvedEnterpriseConnection connection) {
      return adapter;
    }
  }

  static TypedDataModel<String, ExternalStartFact> mockStartFactDataModel(
      Map<String, ExternalStartFact> store) {
    TypedDataModel<String, ExternalStartFact> dm = mock(TypedDataModel.class);
    when(dm.insert(any(ExternalStartFact.class))).thenAnswer(inv -> {
      ExternalStartFact fact = inv.getArgument(0);
      synchronized (store) {
        boolean duplicate = store.values().stream().anyMatch(existing ->
            Objects.equals(existing.getBusinessStartRouteKey(), fact.getBusinessStartRouteKey())
                || Objects.equals(existing.getIdempotencyStartRouteKey(), fact.getIdempotencyStartRouteKey()));
        if (duplicate) {
          return Try.failure(new UniqueConstraintViolationException("duplicate start fact"));
        }
        store.put(fact.getId(), fact);
        return Try.success(fact);
      }
    });
    when(dm.query(anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      synchronized (store) {
        return Try.success(new ArrayList<>(store.values().stream()
            .filter(fact -> matchesStartFactStatement(fact, statement))
            .toList()));
      }
    });
    when(dm.get(any(), any())).thenAnswer(inv -> {
      synchronized (store) {
        return Try.success(store.get(inv.getArgument(0)));
      }
    });
    when(dm.update(anyMap(), anyMap())).thenAnswer(inv -> {
      Map<String, Object> statement = inv.getArgument(0);
      Map<String, Object> data = inv.getArgument(1);
      synchronized (store) {
        long rows = 0;
        for (ExternalStartFact fact : store.values()) {
          if (matchesStartFactStatement(fact, statement)) {
            applyStartFactData(fact, data);
            rows++;
          }
        }
        return Try.success(rows);
      }
    });
    return dm;
  }

  static boolean matchesStartFactStatement(ExternalStartFact fact, Map<String, Object> statement) {
    for (Map.Entry<String, Object> entry : statement.entrySet()) {
      Object actual = switch (entry.getKey()) {
        case "id" -> fact.getId();
        case "businessStartRouteKey" -> fact.getBusinessStartRouteKey();
        case "idempotencyStartRouteKey" -> fact.getIdempotencyStartRouteKey();
        case "status" -> fact.getStatus();
        case "claimToken" -> fact.getClaimToken();
        case "trackingId" -> fact.getTrackingId();
        case "requestFingerprint" -> fact.getRequestFingerprint();
        case "externalInstanceId" -> fact.getExternalInstanceId();
        case "claimUntil" -> fact.getClaimUntil();
        default -> null;
      };
      Object value = entry.getValue();
      if (value == null) {
        if (actual != null) {
          return false;
        }
        continue;
      }
      if (!(value instanceof Map<?, ?> condition)) {
        continue;
      }
      if (condition.containsKey("=") && !Objects.equals(condition.get("="), actual)) {
        return false;
      }
      if (condition.containsKey(">=")
          && (actual == null || ((java.time.LocalDateTime) actual)
              .isBefore((java.time.LocalDateTime) condition.get(">=")))) {
        return false;
      }
    }
    return true;
  }

  static void applyStartFactData(ExternalStartFact fact, Map<String, Object> data) {
    data.forEach((key, value) -> {
      switch (key) {
        case "status" -> fact.setStatus((String) value);
        case "remoteCallPhase" -> fact.setRemoteCallPhase((String) value);
        case "claimToken" -> fact.setClaimToken((String) value);
        case "claimUntil" -> fact.setClaimUntil((java.time.LocalDateTime) value);
        case "externalInstanceId" -> fact.setExternalInstanceId((String) value);
        case "externalUrl" -> fact.setExternalUrl((String) value);
        case "trackingId" -> fact.setTrackingId((String) value);
        case "businessStartRouteKey" -> fact.setBusinessStartRouteKey((String) value);
        case "lastError" -> fact.setLastError((String) value);
        case "repliedAt" -> fact.setRepliedAt((java.time.LocalDateTime) value);
        case "updatedAt" -> fact.setUpdatedAt((java.time.LocalDateTime) value);
        default -> { }
      }
    });
  }

  static final class InMemoryStartFactService extends ExternalStartFactService {
    private final TypedDataModel<String, ExternalStartFact> dataModel;

    InMemoryStartFactService(TypedDataModel<String, ExternalStartFact> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalStartFact> getDataModel() {
      return dataModel;
    }
  }
}
