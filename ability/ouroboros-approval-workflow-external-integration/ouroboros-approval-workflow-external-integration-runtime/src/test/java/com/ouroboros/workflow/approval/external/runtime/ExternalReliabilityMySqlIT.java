package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.event.FlowEventRetryJob;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.runtime.callback.ApprovalProcessCallbackBindingCatalog;
import com.ouroboros.workflow.approval.runtime.callback.ApprovalProcessCallbackListener;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalInstancePage;
import com.ouroboros.workflow.approval.external.ExternalInstanceQuery;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolverTestSupport;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalCallbackReceipt;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventInbox;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventInboxService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventIngestService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventIngestServiceImpl;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalPollingCompensationJob;
import com.ouroboros.workflow.approval.external.runtime.reconcile.ExternalReconcileCheckpoint;
import com.ouroboros.workflow.approval.external.runtime.reconcile.ExternalReconcileCheckpointService;
import com.ouroboros.workflow.approval.external.runtime.reconcile.ExternalStateReconcileJob;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalSyncState;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalSyncStateService;
import com.ouroboros.workflow.approval.external.runtime.event.PersistentExternalCallbackReceiptService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import io.vavr.control.Try;

/**
 * MySQL failure-injection gates for the common callback and event delivery mechanisms.
 *
 * <p>The tests use the production Spring transaction advice and production services. Only
 * provider/listener failures and process restart are controlled by the test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalReliabilityMySqlIT {

  private static final String URL_PROPERTY = "ouroboros.mysql.url";
  private static final String USER_PROPERTY = "ouroboros.mysql.user";
  private static final String PASSWORD_ENVIRONMENT = "OUROBOROS_MYSQL_PASSWORD";

  private DriverManagerDataSource dataSource;
  private SqlDataStation station;
  private String outboxTableName;
  private String receiptTableName;
  private String inboxTableName;
  private String syncStateTableName;
  private String checkpointTableName;

  @BeforeAll
  void migrateReliabilityModels() {
    dataSource = new DriverManagerDataSource(
        requireProperty(URL_PROPERTY),
        requireProperty(USER_PROPERTY),
        requireEnvironment(PASSWORD_ENVIRONMENT));

    String suffix = UUID.randomUUID().toString().replace("-", "");
    outboxTableName = "t_awf_it_flow_outbox_" + suffix;
    receiptTableName = "t_awf_it_receipt_recovery_" + suffix;
    inboxTableName = "t_awf_it_inbox_recovery_" + suffix;
    syncStateTableName = "t_awf_it_sync_state_" + suffix;
    checkpointTableName = "t_awf_it_reconcile_checkpoint_" + suffix;

    Map<String, DataModelMeta> coreModels = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.event");
    Map<String, DataModelMeta> runtimeModels = TypedModelMetaHelper.scanModelMate(
        "com.ouroboros.workflow.approval.external.runtime");
    List<DataModelMeta> models = List.of(
        copyForIntegration(coreModels, "FlowEventOutbox", "FlowEventOutboxReliabilityMySqlIT", outboxTableName),
        copyForIntegration(runtimeModels, "ExternalCallbackReceipt",
            "ExternalCallbackReceiptReliabilityMySqlIT", receiptTableName),
        copyForIntegration(runtimeModels, "ExternalEventInbox",
            "ExternalEventInboxReliabilityMySqlIT", inboxTableName),
        copyForIntegration(runtimeModels, "ExternalSyncState",
            "ExternalSyncStateReliabilityMySqlIT", syncStateTableName),
        copyForIntegration(runtimeModels, "ExternalReconcileCheckpoint",
            "ExternalReconcileCheckpointReliabilityMySqlIT", checkpointTableName));

    station = new SqlDataStation(
        "external-reliability-mysql-integration",
        dataSource,
        models,
        MigrationStrategy.AUTO,
        "MySQL");
    var migrated = station.migrate();
    assertTrue(migrated.isRight(), () -> migrated.isLeft() ? migrated.getLeft().getMessage() : "");
    assertTrue(migrated.get(), "reliability model migration must execute");
  }

  @AfterAll
  void dropReliabilityTables() throws SQLException {
    if (dataSource == null || outboxTableName == null) {
      return;
    }
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS `" + outboxTableName + "`");
      statement.execute("DROP TABLE IF EXISTS `" + receiptTableName + "`");
      statement.execute("DROP TABLE IF EXISTS `" + inboxTableName + "`");
      statement.execute("DROP TABLE IF EXISTS `" + syncStateTableName + "`");
      statement.execute("DROP TABLE IF EXISTS `" + checkpointTableName + "`");
    }
  }

  @Test
  void springAfterCommitFailureRetriesTheSamePersistentFactToSuccess() {
    RecordingTaskEventListener listener = new RecordingTaskEventListener(1);
    try (AnnotationConfigApplicationContext context = flowEventContext(listener)) {
      FlowTaskEvent event = taskEvent("retry-success");
      TaskEventDispatcher dispatcher = context.getBean(TaskEventDispatcher.class);
      TransactionTemplate transaction = new TransactionTemplate(
          context.getBean(PlatformTransactionManager.class));

      transaction.executeWithoutResult(status -> dispatcher.dispatchEvent(event));

      TestFlowEventOutboxService outboxService = context.getBean(TestFlowEventOutboxService.class);
      FlowEventOutbox failed = outboxService.findById(event.getEventFactId()).orElseThrow();
      assertThat(failed.getStatus()).isEqualTo(FlowEventOutbox.STATUS_FAILED);
      assertThat(failed.getRetryCount()).isEqualTo(1);
      assertThat(listener.factIds()).containsExactly(event.getEventFactId());

      outboxService.makeDue(event.getEventFactId());
      context.getBean(FlowEventRetryJob.class).sweep();

      FlowEventOutbox succeeded = outboxService.findById(event.getEventFactId()).orElseThrow();
      assertThat(succeeded.getStatus()).isEqualTo(FlowEventOutbox.STATUS_SUCCESS);
      assertThat(succeeded.getRetryCount()).isEqualTo(1);
      assertThat(listener.factIds())
          .containsExactly(event.getEventFactId(), event.getEventFactId());
    }
  }

  @Test
  void productionCallbackListenerUsesPersistentOutboxAndRetriesAfterFailure() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    when(executionFacade.execute(any(String.class), anyList()))
        .thenReturn(Try.failure(new IllegalStateException("callback failed")), Try.success("ok"));
    var callbackCatalog = mock(ApprovalProcessCallbackBindingCatalog.class);
    when(callbackCatalog.hasCallbackFlow("supplier.processCallback")).thenReturn(true);

    try (AnnotationConfigApplicationContext context = callbackFlowEventContext(
        executionFacade, callbackCatalog)) {
      InstanceEventDispatcher dispatcher = context.getBean(InstanceEventDispatcher.class);
      TransactionTemplate transaction = new TransactionTemplate(
          context.getBean(PlatformTransactionManager.class));
      FlowInstanceEvent event = new FlowInstanceEvent();
      event.setEventFactId("callback-fact-" + UUID.randomUUID());
      event.setFlowKey("callback-flow");
      event.setInstanceId("callback-instance");
      event.setEventType(FlowInstanceEvent.EventType.APPROVED);
      event.setCallbackFlow("supplier.processCallback");

      transaction.executeWithoutResult(status -> dispatcher.dispatchEvent(event));

      TestFlowEventOutboxService outboxService = context.getBean(TestFlowEventOutboxService.class);
      FlowEventOutbox failed = outboxService.findById(event.getEventFactId()).orElseThrow();
      assertThat(failed.getStatus()).isEqualTo(FlowEventOutbox.STATUS_FAILED);
      assertThat(failed.getRetryCount()).isEqualTo(1);

      outboxService.makeDue(event.getEventFactId());
      context.getBean(FlowEventRetryJob.class).sweep();

      FlowEventOutbox succeeded = outboxService.findById(event.getEventFactId()).orElseThrow();
      assertThat(succeeded.getStatus()).isEqualTo(FlowEventOutbox.STATUS_SUCCESS);
      verify(executionFacade, times(2)).execute(any(String.class), anyList());
      var payloadCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
      verify(executionFacade, times(2)).execute(any(String.class), payloadCaptor.capture());
      assertThat(payloadCaptor.getAllValues()).allSatisfy(inputs -> {
        Object payloadObject = inputs.get(0);
        assertThat(payloadObject).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) payloadObject;
        assertThat(payload.get("eventFactId")).isEqualTo(event.getEventFactId());
      });
    }
  }

  @Test
  void persistentOutboxReachesDeadAndRejectsAStaleClaimToken() {
    RecordingTaskEventListener listener = new RecordingTaskEventListener(Integer.MAX_VALUE);
    try (AnnotationConfigApplicationContext context = flowEventContext(listener)) {
      TestFlowEventOutboxService outboxService = context.getBean(TestFlowEventOutboxService.class);
      TaskEventDispatcher dispatcher = context.getBean(TaskEventDispatcher.class);
      TransactionTemplate transaction = new TransactionTemplate(
          context.getBean(PlatformTransactionManager.class));
      FlowTaskEvent deadEvent = taskEvent("retry-dead");

      transaction.executeWithoutResult(status -> dispatcher.dispatchEvent(deadEvent));
      for (int retry = 1; retry <= FlowEventOutbox.MAX_RETRY_COUNT; retry++) {
        outboxService.makeDue(deadEvent.getEventFactId());
        context.getBean(FlowEventRetryJob.class).sweep();
      }

      FlowEventOutbox dead = outboxService.findById(deadEvent.getEventFactId()).orElseThrow();
      assertThat(dead.getStatus()).isEqualTo(FlowEventOutbox.STATUS_DEAD);
      assertThat(dead.getRetryCount()).isEqualTo(FlowEventOutbox.MAX_RETRY_COUNT + 1);
      assertThat(listener.factIds()).hasSize(FlowEventOutbox.MAX_RETRY_COUNT + 1)
          .containsOnly(deadEvent.getEventFactId());
      assertThat(outboxService.claim(deadEvent.getEventFactId(), "dead-worker", 60)).isEmpty();

      FlowTaskEvent fencedEvent = taskEvent("claim-fencing");
      outboxService.enqueue(fencedEvent);
      FlowEventOutboxService.Claim oldClaim = outboxService.claim(
          fencedEvent.getEventFactId(), "old-worker", 1).orElseThrow();
      outboxService.expireClaim(fencedEvent.getEventFactId());
      FlowEventOutboxService.Claim newClaim = outboxService.claim(
          fencedEvent.getEventFactId(), "new-worker", 60).orElseThrow();

      assertThat(outboxService.markSuccess(fencedEvent.getEventFactId(), oldClaim.claimToken())).isFalse();
      assertThat(outboxService.findById(fencedEvent.getEventFactId()).orElseThrow().getClaimToken())
          .isEqualTo(newClaim.claimToken());
      assertThat(outboxService.markSuccess(fencedEvent.getEventFactId(), newClaim.claimToken())).isTrue();
    }
  }

  @Test
  void partialCallbackBatchRecoversAfterProcessRestartWithoutDeletingIncompleteReceipt() {
    String route = UUID.randomUUID().toString();
    String firstEventId = "event-first-" + route;
    String secondEventId = "event-second-" + route;
    List<ExternalCallbackEnvelope> batch = List.of(
        callbackEnvelope(firstEventId, "digest-first-" + route),
        callbackEnvelope(secondEventId, "digest-second-" + route));
    TransactionObservation observation = new TransactionObservation();
    String receiptId;

    try (AnnotationConfigApplicationContext firstProcess = receiptContext(
        observation, secondEventId)) {
      PersistentExternalCallbackReceiptService receiptService = firstProcess.getBean(
          PersistentExternalCallbackReceiptService.class);
      Try<Void> firstAttempt = receiptService.receive(
          "fake", "connection", "nonce-" + route, "payload-" + route, batch);

      assertThat(firstAttempt.isFailure()).isTrue();
      assertThat(firstAttempt.getCause().getMessage()).contains("controlled ingest failure");
      TestExternalEventInboxService inboxService = firstProcess.getBean(
          TestExternalEventInboxService.class);
      assertThat(observation.attemptedEventIds())
          .containsExactly(firstEventId, secondEventId);
      assertThat(observation.delegatedEventIds()).containsExactly(firstEventId);
      assertThat(inboxService.eventIds()).containsExactly(firstEventId);

      TestPersistentExternalCallbackReceiptService persistentReceipt = firstProcess.getBean(
          TestPersistentExternalCallbackReceiptService.class);
      ExternalCallbackReceipt receipt = persistentReceipt.findByNonce("nonce-" + route).orElseThrow();
      receiptId = receipt.getId();
      assertThat(receipt.getStatus()).isEqualTo(ExternalCallbackReceipt.STATUS_RECEIVING);
      assertThat(receipt.getClaimToken()).isNull();
      assertThat(receipt.getLastError()).contains("controlled ingest failure");
      persistentReceipt.expire(receiptId);
    }

    try (AnnotationConfigApplicationContext restartedProcess = receiptContext(observation, null)) {
      PersistentExternalCallbackReceiptService receiptService = restartedProcess.getBean(
          PersistentExternalCallbackReceiptService.class);
      Try<Void> recovered = receiptService.receive(
          "fake", "connection", "nonce-" + route, "payload-" + route, batch);

      assertThat(recovered.isSuccess()).isTrue();
      TestPersistentExternalCallbackReceiptService persistentReceipt = restartedProcess.getBean(
          TestPersistentExternalCallbackReceiptService.class);
      ExternalCallbackReceipt completed = persistentReceipt.findByNonce("nonce-" + route).orElseThrow();
      assertThat(completed.getId()).isEqualTo(receiptId);
      assertThat(completed.getStatus()).isEqualTo(ExternalCallbackReceipt.STATUS_COMPLETED);
      assertThat(completed.getClaimToken()).isNull();
      assertThat(completed.getLastError()).isNull();

      TestExternalEventInboxService inboxService = restartedProcess.getBean(
          TestExternalEventInboxService.class);
      assertThat(inboxService.eventIds())
          .as("persisted inbox event IDs")
          .containsExactlyInAnyOrder(firstEventId, secondEventId);
    }

    assertThat(observation.transactionActive()).isNotEmpty().containsOnly(true);
  }

  @Test
  void pollingRunPersistsFixedWindowAndFencesStaleLease() {
    try (AnnotationConfigApplicationContext context = baseTransactionContext()) {
      TypedDataModel<String, ExternalSyncState> model = new BaseTypedDataModel<>(station
          .getDataModel("ExternalSyncStateReliabilityMySqlIT").orElseThrow().withoutPlugins(), ExternalSyncState.class);
      TestExternalSyncStateService stateService = new TestExternalSyncStateService(model);
      context.registerBean(TestExternalSyncStateService.class, () -> stateService);
      context.refresh();
      TestExternalSyncStateService service = context.getBean(TestExternalSyncStateService.class);

      ExternalSyncStateService.Claim first = service.claim("poll-adapter", "poll-connection", "worker-a", 60)
          .orElseThrow();
      LocalDateTime runFrom = LocalDateTime.of(2026, 9, 3, 10, 0);
      LocalDateTime runUntil = LocalDateTime.of(2026, 9, 3, 10, 5);
      assertThat(service.beginRun(first.state().getId(), first.claimToken(), runFrom, runUntil)).isTrue();
      assertThat(service.recordPage(first.state().getId(), first.claimToken(), "cursor-2")).isTrue();

      ExternalSyncState afterPage = service.findById(first.state().getId()).orElseThrow();
      assertThat(afterPage.getRunFrom()).isEqualTo(runFrom);
      assertThat(afterPage.getRunUntil()).isEqualTo(runUntil);
      assertThat(afterPage.getCursor()).isEqualTo("cursor-2");
      assertThat(afterPage.getClaimToken()).isNull();
      assertThat(afterPage.getClaimUntil()).isNull();

      ExternalSyncStateService.Claim second = service.claim(
          "poll-adapter", "poll-connection", "worker-b", 60).orElseThrow();
      assertThat(service.recordPage(first.state().getId(), first.claimToken(), "stale-cursor")).isFalse();
      assertThat(service.completeRun(second.state().getId(), second.claimToken(), runUntil)).isTrue();

      ExternalSyncState completed = service.findById(first.state().getId()).orElseThrow();
      assertThat(completed.getLastSyncAt()).isEqualTo(runUntil);
      assertThat(completed.getRunFrom()).isNull();
      assertThat(completed.getRunUntil()).isNull();
      assertThat(completed.getCursor()).isNull();
      assertThat(completed.getClaimToken()).isNull();
      assertThat(completed.getClaimUntil()).isNull();
    }
  }

  @Test
  void reconcileCheckpointAdvancesByKeysetAndRejectsStaleCompletion() {
    try (AnnotationConfigApplicationContext context = baseTransactionContext()) {
      TypedDataModel<String, ExternalReconcileCheckpoint> model = new BaseTypedDataModel<>(station
          .getDataModel("ExternalReconcileCheckpointReliabilityMySqlIT").orElseThrow().withoutPlugins(),
          ExternalReconcileCheckpoint.class);
      context.registerBean(TestExternalReconcileCheckpointService.class,
          () -> new TestExternalReconcileCheckpointService(model));
      context.refresh();
      TestExternalReconcileCheckpointService service = context.getBean(TestExternalReconcileCheckpointService.class);

      ExternalReconcileCheckpointService.Claim first = service.claim("worker-a", 60).orElseThrow();
      assertThat(service.advance(first.claimToken(), "instance-200")).isTrue();
      service.expireClaim();
      ExternalReconcileCheckpointService.Claim second = service.claim("worker-b", 60).orElseThrow();
      assertThat(service.complete(first.claimToken())).isFalse();
      assertThat(service.complete(second.claimToken())).isTrue();

      ExternalReconcileCheckpoint completed = service.findForTest();
      assertThat(completed.getAfterLocalInstanceId()).isNull();
      assertThat(completed.getClaimToken()).isNull();
      assertThat(completed.getClaimUntil()).isNull();
    }
  }

  @Test
  void pollingJobUsesOnePersistedWindowAcrossPagesAndRetainsCursorOnFailure() {
    try (AnnotationConfigApplicationContext context = baseTransactionContext()) {
      TypedDataModel<String, ExternalSyncState> model = new BaseTypedDataModel<>(station
          .getDataModel("ExternalSyncStateReliabilityMySqlIT").orElseThrow().withoutPlugins(), ExternalSyncState.class);
      TestExternalSyncStateService stateService = new TestExternalSyncStateService(model);
      context.registerBean(TestExternalSyncStateService.class, () -> stateService);

      PollingIntegrationAdapter adapter = new PollingIntegrationAdapter();
      WorkflowEngineAdapterRegistry registry = new WorkflowEngineAdapterRegistry(
          new WorkflowEngineAdapterProviderBeanBridge(List.of()));
      registry.initialize(List.of(new PollingIntegrationProvider(adapter)));
      WorkflowEngineMapping mapping = new WorkflowEngineMapping();
      mapping.setId("polling-mapping");
      mapping.setVersion(1);
      mapping.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
      mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
      mapping.setAdapterId("mysql-poll-adapter");
      mapping.setConnectionId("mysql-poll-connection");
      mapping.setFlowKey("polling-flow");
      WorkflowEngineMappingService mappings = mock(WorkflowEngineMappingService.class);
      when(mappings.findActiveAllForSystem()).thenReturn(List.of(mapping));
      ExternalInstanceBindingService bindings = mock(ExternalInstanceBindingService.class);
      when(bindings.searchRunningPage(any(), eq(200))).thenReturn(List.of());
      ExternalEventIngestService ingest = mock(ExternalEventIngestService.class);
      when(ingest.ingest(any())).thenReturn(Try.success(null));
      context.refresh();

      adapter.page = new ExternalInstancePage(List.of(
          new ExternalInstanceSnapshot("poll-ext-1", "RUNNING", null, null, null, Map.of())), "poll-cursor-2");
      ExternalPollingCompensationJob job = new ExternalPollingCompensationJob(
          registry, ConnectionAwareAdapterResolverTestSupport.resolver(registry), mappings, bindings,
          stateService, ingest);
      job.run();
      ExternalSyncState afterFirstPage = stateService.findById(
          ExternalSyncState.buildId("mysql-poll-adapter", "mysql-poll-connection")).orElseThrow();
      assertThat(afterFirstPage.getCursor()).isEqualTo("poll-cursor-2");
      assertThat(afterFirstPage.getRunFrom()).isNotNull();
      assertThat(afterFirstPage.getRunUntil()).isNotNull();
      LocalDateTime runFrom = afterFirstPage.getRunFrom();
      LocalDateTime runUntil = afterFirstPage.getRunUntil();

      adapter.page = new ExternalInstancePage(List.of(
          new ExternalInstanceSnapshot("poll-ext-2", "FINISHED", "APPROVED", "approved", null, Map.of())), null);
      job.run();
      ExternalSyncState completed = stateService.findById(afterFirstPage.getId()).orElseThrow();
      assertThat(completed.getLastSyncAt()).isEqualTo(runUntil);
      assertThat(completed.getRunFrom()).isNull();
      assertThat(completed.getRunUntil()).isNull();
      assertThat(completed.getCursor()).isNull();
      assertThat(adapter.queries).hasSize(2);
      assertThat(adapter.queries.get(0).changedFrom()).isEqualTo(adapter.queries.get(1).changedFrom());
      assertThat(adapter.queries.get(0).changedUntil()).isEqualTo(adapter.queries.get(1).changedUntil());
      assertThat(adapter.queries.get(0).cursor()).isNull();
      assertThat(adapter.queries.get(1).cursor()).isEqualTo("poll-cursor-2");
      assertThat(adapter.queries.get(0).changedFrom()).contains(runFrom.toString().substring(0, 19));

      adapter.page = new ExternalInstancePage(List.of(
          new ExternalInstanceSnapshot("poll-ext-3", "RUNNING", null, null, null, Map.of())), "poll-cursor-3");
      when(ingest.ingest(any())).thenReturn(Try.success(null));
      job.run();
      ExternalSyncState inProgress = stateService.findById(completed.getId()).orElseThrow();
      assertThat(inProgress.getCursor()).isEqualTo("poll-cursor-3");
      assertThat(inProgress.getLastSyncAt()).isEqualTo(runUntil);

      adapter.page = new ExternalInstancePage(List.of(
          new ExternalInstanceSnapshot("poll-ext-4", "FINISHED", "APPROVED", "approved", null, Map.of())), null);
      when(ingest.ingest(any())).thenReturn(Try.failure(new BusinessException("inbox unavailable")));
      job.run();
      ExternalSyncState retained = stateService.findById(completed.getId()).orElseThrow();
      assertThat(retained.getCursor()).isEqualTo("poll-cursor-3");
      assertThat(retained.getLastSyncAt()).isEqualTo(runUntil);
    }
  }

  @Test
  void reconcileJobUsesPersistedCheckpointForRetiredMappingsAndAllKeysetPages() {
    try (AnnotationConfigApplicationContext context = baseTransactionContext()) {
      TypedDataModel<String, ExternalReconcileCheckpoint> model = new BaseTypedDataModel<>(station
          .getDataModel("ExternalReconcileCheckpointReliabilityMySqlIT").orElseThrow().withoutPlugins(),
          ExternalReconcileCheckpoint.class);
      TestExternalReconcileCheckpointService checkpointService =
          new TestExternalReconcileCheckpointService(model);

      PollingIntegrationAdapter adapter = new PollingIntegrationAdapter();
      adapter.instanceStatus = "FINISHED";
      WorkflowEngineAdapterRegistry registry = new WorkflowEngineAdapterRegistry(
          new WorkflowEngineAdapterProviderBeanBridge(List.of()));
      registry.initialize(List.of(new PollingIntegrationProvider(adapter)));

      WorkflowEngineMapping retiredMapping = new WorkflowEngineMapping();
      retiredMapping.setId("retired-polling-mapping");
      retiredMapping.setVersion(7);
      retiredMapping.setStatus(WorkflowEngineMapping.STATUS_RETIRED);
      retiredMapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
      retiredMapping.setAdapterId("mysql-poll-adapter");
      retiredMapping.setConnectionId("mysql-poll-connection");
      retiredMapping.setFlowKey("polling-flow");

      WorkflowEngineMappingService mappings = mock(WorkflowEngineMappingService.class);
      when(mappings.findByIdForSystem("retired-polling-mapping")).thenReturn(Optional.of(retiredMapping));
      ExternalInstanceBindingService bindings = mock(ExternalInstanceBindingService.class);
      List<ExternalInstanceBinding> firstPage = new ArrayList<>();
      for (int index = 0; index < 200; index++) {
        firstPage.add(reconcileBinding("inst-" + String.format("%03d", index),
            "ext-" + String.format("%03d", index)));
      }
      ExternalInstanceBinding finalBinding = reconcileBinding("inst-200", "ext-200");
      when(bindings.searchRunningPage(any(), eq(200))).thenAnswer(invocation -> {
        String after = invocation.getArgument(0);
        if (after == null) {
          return firstPage;
        }
        if ("inst-199".equals(after)) {
          return List.of(finalBinding);
        }
        return List.of();
      });
      ExternalEventIngestService ingest = mock(ExternalEventIngestService.class);
      when(ingest.ingest(any())).thenReturn(Try.success(null));

      ExternalStateReconcileJob job = new ExternalStateReconcileJob(
          mappings, bindings, registry, ConnectionAwareAdapterResolverTestSupport.resolver(registry),
          ingest, checkpointService);
      job.reconcile();

      verify(bindings).searchRunningPage(null, 200);
      verify(bindings).searchRunningPage("inst-199", 200);
      verify(bindings).searchRunningPage("inst-200", 200);
      org.mockito.Mockito.verify(ingest, org.mockito.Mockito.times(201)).ingest(any());
      ExternalReconcileCheckpoint completed = checkpointService.findForTest();
      assertThat(completed.getAfterLocalInstanceId()).isNull();
      assertThat(completed.getClaimToken()).isNull();
      assertThat(completed.getClaimUntil()).isNull();
    }
  }

  private ExternalInstanceBinding reconcileBinding(String localInstanceId, String externalInstanceId) {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId(localInstanceId);
    binding.setMappingId("retired-polling-mapping");
    binding.setMappingVersion(7);
    binding.setAdapterId("mysql-poll-adapter");
    binding.setConnectionId("mysql-poll-connection");
    binding.setExternalInstanceId(externalInstanceId);
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    return binding;
  }

  private AnnotationConfigApplicationContext flowEventContext(RecordingTaskEventListener listener) {
    AnnotationConfigApplicationContext context = baseTransactionContext();
    TypedDataModel<String, FlowEventOutbox> model = new BaseTypedDataModel<>(station
        .getDataModel("FlowEventOutboxReliabilityMySqlIT").orElseThrow().withoutPlugins(), FlowEventOutbox.class);
    context.registerBean(TestFlowEventOutboxService.class,
        () -> new TestFlowEventOutboxService(model));
    context.registerBean(TaskEventDispatcher.class);
    context.registerBean(InstanceEventDispatcher.class);
    context.registerBean(FlowEventRetryJob.class);
    context.registerBean(RecordingTaskEventListener.class, () -> listener);
    context.refresh();
    return context;
  }

  private AnnotationConfigApplicationContext callbackFlowEventContext(
      LogicFlowExecutionFacade executionFacade,
      ApprovalProcessCallbackBindingCatalog callbackCatalog) {
    AnnotationConfigApplicationContext context = baseTransactionContext();
    TypedDataModel<String, FlowEventOutbox> model = new BaseTypedDataModel<>(station
        .getDataModel("FlowEventOutboxReliabilityMySqlIT").orElseThrow().withoutPlugins(), FlowEventOutbox.class);
    context.registerBean(TestFlowEventOutboxService.class, () -> new TestFlowEventOutboxService(model));
    context.registerBean(TaskEventDispatcher.class);
    context.registerBean(InstanceEventDispatcher.class);
    context.registerBean(FlowEventRetryJob.class);
    context.registerBean(LogicFlowExecutionFacade.class, () -> executionFacade);
    context.registerBean(ApprovalProcessCallbackBindingCatalog.class, () -> callbackCatalog);
    context.registerBean(ApprovalProcessCallbackListener.class,
        () -> new ApprovalProcessCallbackListener(
            executionFacade, context.getBean(PlatformTransactionManager.class), callbackCatalog));
    context.refresh();
    return context;
  }

  private AnnotationConfigApplicationContext receiptContext(
      TransactionObservation observation, String failOnceEventId) {
    AnnotationConfigApplicationContext context = baseTransactionContext();
    TypedDataModel<String, ExternalEventInbox> inboxModel = new BaseTypedDataModel<>(station
        .getDataModel("ExternalEventInboxReliabilityMySqlIT").orElseThrow().withoutPlugins(), ExternalEventInbox.class);
    TypedDataModel<String, ExternalCallbackReceipt> receiptModel = new BaseTypedDataModel<>(station
        .getDataModel("ExternalCallbackReceiptReliabilityMySqlIT").orElseThrow().withoutPlugins(), ExternalCallbackReceipt.class);
    context.registerBean(TestExternalEventInboxService.class,
        () -> new TestExternalEventInboxService(inboxModel));
    context.registerBean(ExternalEventIngestServiceImpl.class);
    context.registerBean(ControlledIngestService.class, () -> new ControlledIngestService(
        context.getBean(ExternalEventIngestServiceImpl.class), observation, failOnceEventId));
    context.registerBean(TestPersistentExternalCallbackReceiptService.class,
        () -> new TestPersistentExternalCallbackReceiptService(
            receiptModel, context.getBean(ControlledIngestService.class)));
    context.refresh();
    return context;
  }

  private AnnotationConfigApplicationContext baseTransactionContext() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(TransactionConfiguration.class);
    context.registerBean(PlatformTransactionManager.class,
        () -> new DataSourceTransactionManager(dataSource));
    return context;
  }

  private FlowTaskEvent taskEvent(String suffix) {
    FlowTaskEvent event = new FlowTaskEvent();
    event.setEventFactId("fact-" + suffix + "-" + UUID.randomUUID());
    event.setFlowKey("flow");
    event.setInstanceId("instance-" + suffix);
    event.setTaskId("task-" + suffix);
    event.setState(TaskState.HANDLED);
    event.setActionName("approve");
    event.setEventTime(LocalDateTime.now());
    return event;
  }

  private ExternalCallbackEnvelope callbackEnvelope(String eventId, String digest) {
    return new ExternalCallbackEnvelope(
        eventId,
        "fake",
        "connection",
        "external-instance",
        "external-task",
        eventId,
        "2026-09-03T12:00:00Z",
        "TASK_COMPLETED",
        "APPROVED",
        digest);
  }

  private DataModelMeta copyForIntegration(
      Map<String, DataModelMeta> models,
      String sourceName,
      String testName,
      String tableName) {
    DataModelMeta source = models.get(sourceName);
    assertThat(source).as("model metadata for %s", sourceName).isNotNull();
    DataModelMeta copy = source.deepCopy();
    copy.setName(testName);
    copy.setRawName(tableName);
    copy.setMigrationStrategy(MigrationStrategy.AUTO);
    return copy;
  }

  private String requireProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "mysql-integration requires -D" + name + " and a dedicated test schema");
    }
    return value;
  }

  private String requireEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "mysql-integration requires environment variable " + name + " for its dedicated test schema");
    }
    return value;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TransactionConfiguration {
  }

  static final class RecordingTaskEventListener {
    private final AtomicInteger failuresRemaining;
    private final List<String> factIds = new ArrayList<>();

    RecordingTaskEventListener(int failures) {
      this.failuresRemaining = new AtomicInteger(failures);
    }

    @EventListener
    public void onTaskEvent(FlowTaskEvent event) {
      factIds.add(event.getEventFactId());
      if (failuresRemaining.getAndUpdate(current -> Math.max(current - 1, 0)) > 0) {
        throw new BusinessException("controlled listener failure");
      }
    }

    List<String> factIds() {
      return List.copyOf(factIds);
    }
  }

  static final class TransactionObservation {
    private final List<Boolean> transactionActive = new ArrayList<>();
    private final List<String> attemptedEventIds = new ArrayList<>();
    private final List<String> delegatedEventIds = new ArrayList<>();

    void recordAttempt(String eventId) {
      transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
      attemptedEventIds.add(eventId);
    }

    void recordDelegated(String eventId) {
      delegatedEventIds.add(eventId);
    }

    List<Boolean> transactionActive() {
      return List.copyOf(transactionActive);
    }

    List<String> attemptedEventIds() {
      return List.copyOf(attemptedEventIds);
    }

    List<String> delegatedEventIds() {
      return List.copyOf(delegatedEventIds);
    }
  }

  static final class ControlledIngestService implements ExternalEventIngestService {
    private final ExternalEventIngestService delegate;
    private final TransactionObservation observation;
    private final String failOnceEventId;
    private boolean failed;

    ControlledIngestService(
        ExternalEventIngestService delegate,
        TransactionObservation observation,
        String failOnceEventId) {
      this.delegate = delegate;
      this.observation = observation;
      this.failOnceEventId = failOnceEventId;
    }

    @Override
    public Try<Void> ingest(ExternalCallbackEnvelope envelope) {
      observation.recordAttempt(envelope.eventId());
      if (!failed && envelope.eventId().equals(failOnceEventId)) {
        failed = true;
        return Try.failure(new BusinessException("controlled ingest failure"));
      }
      observation.recordDelegated(envelope.eventId());
      return delegate.ingest(envelope);
    }
  }

  static class TestFlowEventOutboxService extends FlowEventOutboxService {
    private final TypedDataModel<String, FlowEventOutbox> dataModel;

    TestFlowEventOutboxService(TypedDataModel<String, FlowEventOutbox> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, FlowEventOutbox> getDataModel() {
      return dataModel;
    }

    void makeDue(String id) {
      updateFields(id, Map.of("nextAttemptAt", LocalDateTime.now().minusSeconds(1)));
    }

    void expireClaim(String id) {
      updateFields(id, Map.of("claimUntil", LocalDateTime.now().minusSeconds(1)));
    }

    private void updateFields(String id, Map<String, Object> fields) {
      var result = dataModel.update(Map.of("id", Map.of("=", id)), fields);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.get()).isEqualTo(1L);
    }
  }

  static class TestExternalEventInboxService extends ExternalEventInboxService {
    private final TypedDataModel<String, ExternalEventInbox> dataModel;

    TestExternalEventInboxService(TypedDataModel<String, ExternalEventInbox> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalEventInbox> getDataModel() {
      return dataModel;
    }

    int countByEventId(String eventId) {
      try {
        return searchList(Map.of("eventId", Map.of("=", eventId))).size();
      } catch (Exception ex) {
        throw new IllegalStateException("failed to count inbox events", ex);
      }
    }

    List<String> eventIds() {
      try {
        return searchList(Map.of()).stream().map(ExternalEventInbox::getEventId).toList();
      } catch (Exception ex) {
        throw new IllegalStateException("failed to query inbox events", ex);
      }
    }
  }

  static class TestPersistentExternalCallbackReceiptService
      extends PersistentExternalCallbackReceiptService {
    private final TypedDataModel<String, ExternalCallbackReceipt> dataModel;

    TestPersistentExternalCallbackReceiptService(
        TypedDataModel<String, ExternalCallbackReceipt> dataModel,
        ExternalEventIngestService ingestService) {
      super(ingestService);
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalCallbackReceipt> getDataModel() {
      return dataModel;
    }

    java.util.Optional<ExternalCallbackReceipt> findByNonce(String nonce) {
      try {
        return java.util.Optional.ofNullable(searchOne(Map.of("nonce", Map.of("=", nonce))));
      } catch (Exception ex) {
        throw new IllegalStateException("failed to query callback receipt", ex);
      }
    }

    void expire(String id) {
      var result = dataModel.update(
          Map.of("id", Map.of("=", id)),
          Map.of("expiresAt", LocalDateTime.now().minusSeconds(1)));
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.get()).isEqualTo(1L);
    }
  }

  static class TestExternalSyncStateService extends ExternalSyncStateService {
    private final TypedDataModel<String, ExternalSyncState> dataModel;

    TestExternalSyncStateService(TypedDataModel<String, ExternalSyncState> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalSyncState> getDataModel() {
      return dataModel;
    }
  }

  static class TestExternalReconcileCheckpointService extends ExternalReconcileCheckpointService {
    private final TypedDataModel<String, ExternalReconcileCheckpoint> dataModel;

    TestExternalReconcileCheckpointService(TypedDataModel<String, ExternalReconcileCheckpoint> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, ExternalReconcileCheckpoint> getDataModel() {
      return dataModel;
    }

    void expireClaim() {
      var result = dataModel.update(
          Map.of("id", Map.of("=", ExternalReconcileCheckpoint.ID)),
          Map.of("claimUntil", LocalDateTime.now().minusSeconds(1)));
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.get()).isEqualTo(1L);
    }

    ExternalReconcileCheckpoint findForTest() {
      try {
        return searchOne(Map.of("id", Map.of("=", ExternalReconcileCheckpoint.ID)));
      } catch (Exception ex) {
        throw new IllegalStateException("failed to query reconcile checkpoint", ex);
      }
    }
  }


  static final class PollingIntegrationProvider implements ApprovalWorkflowEngineAdapterProvider {
    private final PollingIntegrationAdapter adapter;

    PollingIntegrationProvider(PollingIntegrationAdapter adapter) {
      this.adapter = adapter;
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("mysql-poll-adapter", "mysql-poll", "1.0", Set.of(), Set.of(Capability.POLLING, Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return adapter;
    }
  }

  static final class PollingIntegrationAdapter implements ApprovalWorkflowEngineAdapter, ExternalInstanceReader {
    ExternalInstancePage page = new ExternalInstancePage(List.of(), null);
    List<ExternalInstanceQuery> queries = new ArrayList<>();
    String instanceStatus = "RUNNING";

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
      return Try.success(new ExternalInstanceSnapshot(
          instance.externalInstanceId(), instanceStatus, "APPROVED", "approved", null, Map.of()));
    }

    @Override
    public Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query) {
      queries.add(query);
      return Try.success(page);
    }
  }
}
