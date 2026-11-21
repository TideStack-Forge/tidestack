package com.ouroboros.workflow.approval.external.runtime.action;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.workflow.approval.event.BuiltInFlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.event.FlowEventRetryJob;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.external.ExternalActionResult;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalTaskRef;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SQL and production transaction advice; provider and persistence failures are controlled. */
class ExternalTaskFactTransactionIntegrationTest {
  private MockedStatic<DataModelCenter> center;
  private Map<String, DataModel> models;
  private SqlDataStation station;
  private JdbcTemplate jdbc;
  private DataSourceTransactionManager manager;
  private ExternalActionFactService facts;
  private ExternalActionFactService rawFacts;
  private ExternalTaskBindingService tasks;
  private FlowEventOutboxService outbox;
  private FlowEventOutboxService rawOutbox;
  private ExternalWorkflowActionExecutor executor;
  private ConnectionAwareAdapterResolver resolver;
  private TaskEventDispatcher dispatcher;
  private boolean mysql;
  private BuiltInFlowTaskFactContextProvider internal;
  private final List<FlowTaskEvent> delivered = new ArrayList<>();
  private ExternalTaskBinding task;
  private final AtomicBoolean failDelivery = new AtomicBoolean();

  @BeforeEach
  void setUp() {
    var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String url = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
    mysql = url != null && !url.isBlank();
    var source = mysql ? new DriverManagerDataSource(url, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"),
        System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD")) : new DriverManagerDataSource(
            "jdbc:h2:mem:external_task_" + suffix + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    manager = new DataSourceTransactionManager(source);
    jdbc = new JdbcTemplate(source);
    List<DataModelMeta> metadata = new ArrayList<>();
    addModel(metadata, "com.ouroboros.workflow.approval.event", "FlowEventOutbox");
    addModel(metadata, "com.ouroboros.workflow.approval.external.runtime.action", "ExternalActionFact");
    addModel(metadata, "com.ouroboros.workflow.approval.external.runtime.binding", "ExternalTaskBinding");
    addModel(metadata, "com.ouroboros.workflow.approval.external.runtime.binding", "ExternalInstanceBinding");
    metadata.forEach(meta -> meta.setRawName(meta.getRawName() + "_" + suffix));
    station = new SqlDataStation("external-task-facts", source, metadata, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
    station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
    models = station.getDataModelList().stream().collect(Collectors.toMap(DataModel::getFullName, model -> model));
    center = mockStatic(DataModelCenter.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
    center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call ->
        java.util.Optional.ofNullable(models.get(call.<String>getArgument(0))));
    center.when(DataModelCenter::hasSnapshot).thenReturn(true);
    rawFacts = spy(new ExternalActionFactService());
    facts = transactional(rawFacts, ExternalActionFactService.class);
    tasks = transactional(new ExternalTaskBindingService(), ExternalTaskBindingService.class);
    var instances = transactional(new ExternalInstanceBindingService(), ExternalInstanceBindingService.class);
    var mappings = mock(WorkflowEngineMappingService.class);
    var mapping = new WorkflowEngineMapping(); mapping.setId("mapping"); mapping.setVersion(1); mapping.setAuthority("EXTERNAL_ENGINE");
    when(mappings.findByIdForSystem("mapping")).thenReturn(java.util.Optional.of(mapping));
    internal = mock(BuiltInFlowTaskFactContextProvider.class);
    var context = new RoutedFlowTaskFactContextProvider(internal, instances, tasks, mappings);
    rawOutbox = spy(new FlowEventOutboxService());
    outbox = transactional(rawOutbox, FlowEventOutboxService.class);
    dispatcher = new TaskEventDispatcher();
    ReflectionTestUtils.setField(dispatcher, "factContextProvider", context);
    ReflectionTestUtils.setField(dispatcher, "outboxService", outbox);
    dispatcher.setPublisher(event -> {
      if (failDelivery.get()) { throw new IllegalStateException("delivery unavailable"); }
      delivered.add((FlowTaskEvent) event);
    });
    resolver = mock(ConnectionAwareAdapterResolver.class);
    executor = mock(ExternalWorkflowActionExecutor.class);
    when(resolver.resolvePort("remote", "connection", ExternalWorkflowActionExecutor.class)).thenReturn(executor);
    when(executor.execute(any())).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "remote call holds no local transaction");
      var command = call.<com.ouroboros.workflow.approval.external.ExternalActionCommand>getArgument(0);
      assertNotNull(facts.findById(command.idempotencyKey()).orElseThrow().getTaskFactJson());
      return Try.success(new ExternalActionResult(true, "accepted", null, null, Map.of()));
    });
    var instance = new ExternalInstanceBinding();
    instance.setLocalInstanceId("instance"); instance.setScope("tenant"); instance.setMappingId("mapping"); instance.setMappingVersion(1);
    instance.setAdapterId("remote"); instance.setConnectionId("connection"); instance.setExternalInstanceId("remote-instance");
    instance.setFlowKey("approval"); instance.setStatus("RUNNING");
    instance.setExternalDefinitionId("remote-definition");
    instance.setExternalRevision("1"); instance.setExternalEventTime(LocalDateTime.now());
    instance.setLastExternalEventId("started"); instance.setExternalUrl("https://approval.invalid/instance");
    instance.setBusinessRecordType("sales.Order"); instance.setBusinessRecordId("42");
    instance.setApprovalCaseId("case"); instance.setSubmissionRound(2); instance.setCallbackFlow("sales.Order.processCallback");
    assertSuccess(instances.create(instance));
    task = new ExternalTaskBinding(); task.setLocalTaskId("task"); task.setLocalInstanceId("instance");
    task.setScope("tenant"); task.setMappingId("mapping"); task.setMappingVersion(1);
    task.setAdapterId("remote"); task.setConnectionId("connection"); task.setExternalTaskId("remote-task");
    task.setStatus("CREATED"); task.setAssignee("actor"); task.setNodeId("review");
    task.setExternalRevision("1"); task.setExternalEventTime(LocalDateTime.now());
    task.setLastExternalEventId("task-created"); task.setExternalUrl("https://approval.invalid/task");
    assertSuccess(tasks.create(task));
  }

  @AfterEach
  void tearDown() {
    try {
      if (station != null) {
        for (var model : station.getDataModelList()) { jdbc.execute("DROP TABLE " + model.getRawName()); }
      }
    } finally { if (center != null) { center.close(); } }
  }

  @Test
  void successfulTaskCommandCommitsItsJournalStateAndCanonicalOutboxTogether() {
    assertSuccess(service().executeTask(task, request("approve"), "APPROVED"));
    assertEquals("APPROVED", tasks.findByLocalId("task").orElseThrow().getStatus());
    var fact = onlyFact(); assertEquals(ExternalActionFact.STATUS_SUCCEEDED, fact.getStatus());
    assertEquals(ExternalActionFact.PHASE_REPLIED, fact.getRemoteCallPhase());
    assertNotNull(fact.getRepliedAt());
    var stored = outbox.findById(fact.getId()).orElseThrow();
    assertEquals(FlowEventOutbox.STATUS_PENDING, stored.getStatus());
    deliver(fact.getId());
    var event = outbox.fromTaskJson(stored).orElseThrow();
    assertEquals(fact.getId(), event.getEventFactId());
    assertEquals(FlowTaskEvent.FactKind.ACTION_COMPLETED, event.getFactKind());
    assertEquals(TaskState.APPROVED, event.getState());
    assertEquals("approve", event.getActionName());
    assertEquals(new FlowInstanceEvent.BusinessContext("sales.Order", "42"), event.getBusinessContext());
    assertEquals("case", event.getApprovalCaseId()); assertEquals(2, event.getSubmissionRound());
    assertEquals(fact.getRepliedAt(), event.getEventTime());
    assertEquals(1, event.getProjectionVersion());
    assertEquals(1, delivered.size());
    outbox.requirePersistedFact(event);
    verifyNoInteractions(internal);
    if (mysql) { assertTrue(station.generateMigrationSql(station.getDataModelList()).get().isEmpty()); }
  }

  @Test
  void anEnclosingBusinessRollbackCannotEraseRemoteResponsibilityOrRunItsConsumerUnderBusinessLocks() {
    var transaction = new TransactionTemplate(manager);
    var enclosingGuard = new com.ouroboros.data.model.DataModelWriteGuard() {
      public void beforeWrite(DataModel model) { throw new IllegalStateException("business resource is protected"); }
      public void markRollbackOnly() { }
    };
    transaction.executeWithoutResult(status -> com.ouroboros.data.model.DataModelExecutionContext.withWriteGuard(enclosingGuard, () -> {
      assertSuccess(service().executeTask(task, request("approve"), "APPROVED"));
      assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
      assertSame(enclosingGuard, com.ouroboros.data.model.DataModelExecutionContext.getWriteGuard());
      assertTrue(delivered.isEmpty(), "source delivery must not wait on the enclosing business transaction");
      status.setRollbackOnly();
      return null;
    }));
    var fact = onlyFact();
    assertEquals(ExternalActionFact.STATUS_SUCCEEDED, fact.getStatus());
    assertEquals("APPROVED", tasks.findByLocalId("task").orElseThrow().getStatus());
    assertEquals(FlowEventOutbox.STATUS_PENDING, outbox.findById(fact.getId()).orElseThrow().getStatus());
    new FlowEventRetryJob(outbox, mock(InstanceEventDispatcher.class), dispatcher).sweep();
    assertEquals(fact.getId(), delivered.getFirst().getEventFactId());
    verify(executor, times(1)).execute(any());
  }

  @Test
  void failedOutboxInsertRollsBackLocalCompletionAndRecoveryDoesNotCallRemoteAgain() {
    var failOnce = new AtomicBoolean(true);
    doAnswer(call -> {
      var result = call.callRealMethod();
      if (failOnce.getAndSet(false)) { throw new IllegalStateException("outbox write failed after insert"); }
      return result;
    }).when(rawOutbox).enqueue(any(FlowTaskEvent.class));
    assertTrue(service().executeTask(task, request("approve"), "APPROVED").isFailure());
    var fact = assertIncomplete();
    var replyTime = fact.getRepliedAt();
    assertTrue(models.get("ExternalInstanceBinding").update(Map.of("localInstanceId", Map.of("=", "instance")),
        Map.of("businessRecordId", "changed-after-command")).isSuccess());
    expire(fact);
    assertSuccess(service().recover(facts.claimForRecovery(fact.getId(), 300).orElseThrow()));
    assertEquals(ExternalActionFact.STATUS_SUCCEEDED, facts.findById(fact.getId()).orElseThrow().getStatus());
    assertEquals(replyTime, outbox.fromTaskJson(outbox.findById(fact.getId()).orElseThrow()).orElseThrow().getEventTime());
    assertEquals("42", outbox.fromTaskJson(outbox.findById(fact.getId()).orElseThrow()).orElseThrow().getBusinessContext().recordId());
    verify(executor, times(1)).execute(any());
    deliver(fact.getId());
    assertEquals(1, delivered.size());
  }

  @Test
  void staleCompletionClaimRollsBackTaskAndOutboxAndCanBeRecoveredOnTheOriginalFact() {
    var failures = new AtomicInteger(1);
    doAnswer(call -> failures.getAndDecrement() > 0 ? false : call.callRealMethod())
        .when(rawFacts).markSucceeded(anyString(), anyString());
    assertTrue(service().executeTask(task, request("approve"), "APPROVED").isFailure());
    var fact = assertIncomplete(); expire(fact);
    assertSuccess(service().recover(facts.claimForRecovery(fact.getId(), 300).orElseThrow()));
    assertEquals("APPROVED", tasks.findByLocalId("task").orElseThrow().getStatus());
    assertTrue(outbox.findById(fact.getId()).isPresent());
    verify(executor, times(1)).execute(any());
  }

  @Test
  void anOutboxOnAnotherDatasourceCannotPartiallyCommitTheAcceptedTaskCommand() {
    var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    var otherSource = new DriverManagerDataSource("jdbc:h2:mem:foreign_task_" + suffix
        + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    var metadata = TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.event").get("FlowEventOutbox").deepCopy();
    metadata.setRawName(metadata.getRawName() + "_foreign_" + suffix);
    var otherStation = new SqlDataStation("foreign-outbox", otherSource, List.of(metadata), MigrationStrategy.AUTO, "H2");
    var otherJdbc = new JdbcTemplate(otherSource);
    otherStation.generateMigrationSql(otherStation.getDataModelList()).get().forEach(otherJdbc::execute);
    var foreignModel = otherStation.getDataModelList().getFirst();
    var original = models.put("FlowEventOutbox", foreignModel);
    try {
      assertTrue(service().executeTask(task, request("approve"), "APPROVED").isFailure());
      assertIncomplete();
      assertEquals(0L, foreignModel.count(Map.of()).get());
    } finally {
      models.put("FlowEventOutbox", original);
      otherJdbc.execute("DROP TABLE " + foreignModel.getRawName());
    }
  }

  @Test
  void repeatedLegitimateRemindersKeepTheTaskOpenAndProduceIndependentFacts() {
    assertSuccess(service().executeTask(task, request("remind"), "CREATED"));
    var first = onlyFact();
    assertSuccess(service().executeTask(tasks.findByLocalId("task").orElseThrow(), request("remind"), "CREATED"));
    assertEquals("CREATED", tasks.findByLocalId("task").orElseThrow().getStatus());
    assertEquals(2L, models.get("ExternalActionFact").count(Map.of()).get());
    assertEquals(2L, models.get("FlowEventOutbox").count(Map.of()).get());
    for (var value : factsList()) { deliver(value.getId()); }
    assertEquals(2, delivered.size());
    assertNotEquals(delivered.get(0).getEventFactId(), delivered.get(1).getEventFactId());
    assertTrue(delivered.stream().anyMatch(event -> first.getId().equals(event.getEventFactId())));
    assertTrue(delivered.stream().allMatch(event -> event.getState() == TaskState.CREATED
        && event.getFactKind() == FlowTaskEvent.FactKind.ACTION_COMPLETED && "remind".equals(event.getActionName())));
    verify(executor, times(2)).execute(any());
  }

  @Test
  void missingTrustedOwnershipAndRemoteRejectionCannotProduceSuccessfulTaskFacts() {
    var instanceModel = models.get("ExternalInstanceBinding");
    assertTrue(instanceModel.update(Map.of("localInstanceId", Map.of("=", "instance")),
        Map.of("businessRecordId", " ")).isSuccess());
    assertTrue(service().executeTask(task, request("approve"), "APPROVED").isFailure());
    verifyNoInteractions(executor);
    assertEquals(0L, models.get("ExternalActionFact").count(Map.of()).get());
    assertTrue(instanceModel.update(Map.of("localInstanceId", Map.of("=", "instance")),
        Map.of("businessRecordId", "42")).isSuccess());
    doReturn(Try.success(new ExternalActionResult(false, "denied", null, null, null))).when(executor).execute(any());
    assertTrue(service().executeTask(task, request("approve"), "APPROVED").isFailure());
    assertEquals(ExternalActionFact.STATUS_REJECTED, onlyFact().getStatus());
    assertEquals("CREATED", tasks.findByLocalId("task").orElseThrow().getStatus());
    assertEquals(0L, models.get("FlowEventOutbox").count(Map.of()).get());
    assertTrue(delivered.isEmpty());
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "OUROBOROS_BEHAVIOR_MYSQL_URL", matches = ".+")
  void anExistingActionJournalUpgradesWithoutLosingItsOriginalRecoveryIdentity() throws Exception {
    var newModel = models.get("ExternalActionFact");
    var legacy = TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.external.runtime.action")
        .get("ExternalActionFact").deepCopy();
    legacy.setRawName(newModel.getRawName());
    legacy.getFields().removeIf(field -> List.of("taskFactJson", "repliedAt").contains(field.getName()));
    jdbc.execute("DROP TABLE " + newModel.getRawName());
    var legacyStation = new SqlDataStation("legacy-command", manager.getDataSource(), List.of(legacy),
        MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
    legacyStation.generateMigrationSql(legacyStation.getDataModelList()).get().forEach(jdbc::execute);
    var data = new LinkedHashMap<String, Object>();
    data.put("id", "legacy-command"); data.put("targetType", "TASK"); data.put("targetLocalId", "task");
    data.put("activeRouteKey", "legacy-active"); data.put("idempotencyRouteKey", "legacy-idempotency");
    data.put("requestFingerprint", "legacy-fingerprint"); data.put("sourceState", "CREATED");
    data.put("targetStatus", "APPROVED"); data.put("canonicalAction", "approve");
    data.put("adapterId", "remote"); data.put("connectionId", "connection"); data.put("mappingVersion", 1);
    data.put("status", ExternalActionFact.STATUS_EXECUTING); data.put("remoteCallPhase", ExternalActionFact.PHASE_REPLIED);
    data.put("accepted", true); data.put("claimToken", "old-worker");
    data.put("claimUntil", LocalDateTime.now().minusMinutes(1));
    data.put("createdAt", LocalDateTime.of(2026, 10, 1, 12, 0)); data.put("updatedAt", data.get("createdAt"));
    var command = new com.ouroboros.workflow.approval.external.ExternalActionCommand("approve", "approve", "actor", "ok",
        null, null, "legacy-command", 1, request("approve").externalInstanceRef(), request("approve").externalTaskRef());
    data.put("commandJson", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(command));
    assertSuccess(legacyStation.getDataModelList().getFirst().withoutPlugins().insert(data));
    station.generateMigrationSql(List.of(newModel)).get().forEach(jdbc::execute);
    var upgraded = facts.findById("legacy-command").orElseThrow();
    assertNull(upgraded.getRepliedAt()); assertNull(upgraded.getTaskFactJson());
    assertEquals("legacy-idempotency", upgraded.getIdempotencyRouteKey());
    assertSuccess(service().recover(facts.claimForRecovery("legacy-command", 300).orElseThrow()));
    verifyNoInteractions(executor);
    assertEquals(ExternalActionFact.STATUS_SUCCEEDED, facts.findById("legacy-command").orElseThrow().getStatus());
    var event = outbox.fromTaskJson(outbox.findById("legacy-command").orElseThrow()).orElseThrow();
    assertEquals("legacy-command", event.getEventFactId());
    assertEquals(data.get("createdAt"), event.getEventTime());
  }

  @Test
  void failedConsumerKeepsTheCommittedSourceFactForTheExistingOutboxToRedeliver() {
    failDelivery.set(true);
    assertSuccess(service().executeTask(task, request("approve"), "APPROVED"));
    var fact = onlyFact();
    assertEquals(ExternalActionFact.STATUS_SUCCEEDED, fact.getStatus());
    deliver(fact.getId());
    assertEquals(FlowEventOutbox.STATUS_FAILED, outbox.findById(fact.getId()).orElseThrow().getStatus());
    assertTrue(delivered.isEmpty());
    failDelivery.set(false);
    assertTrue(models.get("FlowEventOutbox").update(Map.of("id", Map.of("=", fact.getId())),
        Map.of("nextAttemptAt", LocalDateTime.now().minusSeconds(1))).isSuccess());
    var claim = outbox.claim(fact.getId(), "restarted-worker", 60).orElseThrow();
    dispatcher.deliverClaimedOutbox(fact.getId(), claim.claimToken());
    assertEquals(FlowEventOutbox.STATUS_SUCCESS, outbox.findById(fact.getId()).orElseThrow().getStatus());
    assertEquals(fact.getId(), delivered.getFirst().getEventFactId());
    verify(executor, times(1)).execute(any());
  }

  private ExternalActionFact assertIncomplete() {
    assertEquals("CREATED", tasks.findByLocalId("task").orElseThrow().getStatus());
    var fact = onlyFact(); assertEquals(ExternalActionFact.PHASE_REPLIED, fact.getRemoteCallPhase());
    assertEquals(ExternalActionFact.STATUS_EXECUTING, fact.getStatus());
    assertNotNull(fact.getRepliedAt());
    assertTrue(outbox.findById(fact.getId()).isEmpty());
    assertTrue(delivered.isEmpty());
    return fact;
  }

  private void deliver(String factId) {
    var claim = outbox.claim(factId, "existing-outbox-worker", 60).orElseThrow();
    dispatcher.deliverClaimedOutbox(factId, claim.claimToken());
  }

  private ExternalActionFact onlyFact() { return factsList().getFirst(); }

  private List<ExternalActionFact> factsList() {
    try { return facts.searchList(new LinkedHashMap<>()).stream().map(ExternalActionFact.class::cast).toList(); }
    catch (com.ouroboros.exception.OuroborosException ex) { throw new AssertionError(ex); }
  }

  private void expire(ExternalActionFact fact) {
    assertTrue(models.get("ExternalActionFact").update(Map.of("id", Map.of("=", fact.getId())),
        Map.of("claimUntil", LocalDateTime.now().minusSeconds(1))).isSuccess());
  }

  private ExternalActionExecutionService service() {
    return transactional(new ExternalActionExecutionService(facts, resolver, tasks, mock(ExternalInstanceBindingService.class),
        mock(ExternalResultBindingService.class), dispatcher, mock(InstanceEventDispatcher.class),
        mock(ExternalStartFactService.class), manager), ExternalActionExecutionService.class);
  }

  private ExternalActionRequest request(String action) {
    return new ExternalActionRequest(action, action, "actor", "comment", null, null, 1,
        new ExternalInstanceRef("remote-instance", "connection"), new ExternalTaskRef("remote-task", "remote-instance", "connection"));
  }

  private void addModel(List<DataModelMeta> metadata, String packageName, String name) {
    metadata.add(TypedModelMetaHelper.scanModelMate(packageName).get(name).deepCopy());
  }

  private <T> T transactional(T target, Class<T> type) {
    var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
    factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
    return type.cast(factory.getProxy());
  }

  private void assertSuccess(Try<?> result) {
    assertTrue(result.isSuccess(), () -> String.valueOf(result.getCause()));
  }
}
