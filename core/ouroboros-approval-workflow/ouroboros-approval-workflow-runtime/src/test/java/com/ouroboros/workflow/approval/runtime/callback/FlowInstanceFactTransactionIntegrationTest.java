package com.ouroboros.workflow.approval.runtime.callback;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FlowInstanceFactTransactionIntegrationTest {
  @Test
  void h2SourceCursorAndOutboxCommitAndRollbackTogether() {
    verifySource(new DriverManagerDataSource("jdbc:h2:mem:source" + UUID.randomUUID()
        + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""), "H2");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "OUROBOROS_BEHAVIOR_MYSQL_URL", matches = ".+")
  void mysqlSourceCursorAndOutboxCommitAndRollbackTogetherAndKeepAnUnchangedMigration() {
    verifySource(new DriverManagerDataSource(System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL"),
        System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD")), "MySQL");
  }

  private void verifySource(DriverManagerDataSource source, String dialect) {
    var metadata = new java.util.ArrayList<>(TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.event").values().stream()
        .filter(model -> List.of("FlowEventOutbox", "FlowInstanceFactState").contains(model.getFullName())).toList());
    metadata.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.lifecycle").values().stream()
        .filter(model -> "ApprovalCase".equals(model.getFullName())).toList());
    var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    metadata.forEach(model -> model.setRawName(model.getRawName() + "_" + suffix));
    var station = new SqlDataStation("source-facts", source, metadata, MigrationStrategy.AUTO, dialect);
    var jdbc = new JdbcTemplate(source);
    station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
    var models = station.getDataModelList().stream().collect(Collectors.toMap(DataModel::getFullName, model -> model));
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    try (var center = mockStatic(DataModelCenter.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
      center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call ->
          java.util.Optional.ofNullable(models.get(call.<String>getArgument(0))));
      center.when(DataModelCenter::hasSnapshot).thenReturn(true);
      var cursor = new FlowInstanceFactStateService();
      var outbox = new FlowEventOutboxService();
      var cases = new com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService();
      transaction.executeWithoutResult(status -> {
        var submission = cases.reserveSubmission("sales.Order", "42", "source-flow");
        cases.bindExecution(submission.approvalCaseId(), submission.submissionRound(), "instance-1");
        var locked = cases.lockByBusinessRecord("sales.Order", "42", "source-flow").orElseThrow();
        assertEquals(submission.approvalCaseId(), locked.getApprovalCaseId());
        assertEquals("instance-1", locked.getActiveExecutionId());
        assertTrue(cases.lockByBusinessRecord("sales.Order", "missing", "source-flow").isEmpty());
      });
      var started = event("fact-start", FlowInstanceEvent.EventType.STARTED);
      transaction.executeWithoutResult(status -> { cursor.advance(started); outbox.enqueue(started); });
      assertEquals(1L, started.getStateSequence());

      var rolledBack = event("rolled-back", FlowInstanceEvent.EventType.SUSPENDED);
      assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
        cursor.advance(rolledBack);
        outbox.enqueue(rolledBack);
        throw new IllegalStateException("source rollback");
      }));
      assertTrue(outbox.findById("rolled-back").isEmpty());
      var approved = event("fact-approved", FlowInstanceEvent.EventType.APPROVED);
      transaction.executeWithoutResult(status -> { cursor.advance(approved); outbox.enqueue(approved); });
      assertEquals(2L, approved.getStateSequence());
      var persisted = outbox.fromInstanceJson(outbox.findById("fact-approved").orElseThrow()).orElseThrow();
      assertEquals(approved.getStateSequence(), persisted.getStateSequence());
      assertEquals(1, persisted.getProjectionVersion());
      assertNotNull(persisted.getOccurredAt());
      assertEquals(approved.getOccurredAt(), persisted.getOccurredAt());
      assertEquals(approved.getEventTime(), persisted.getEventTime());
      assertEquals(2, models.get("FlowEventOutbox").count(Map.of()).get());
      outbox.requirePersistedFact(persisted);
      persisted.setOutcome("forged");
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> outbox.requirePersistedFact(persisted));
      assertThrows(com.ouroboros.exception.BusinessException.class, () ->
          outbox.requirePersistedFact(event("uncommitted-spring-only", FlowInstanceEvent.EventType.STARTED)));
      var taskFact = new com.ouroboros.workflow.approval.model.events.FlowTaskEvent();
      taskFact.setEventFactId("source-task");
      taskFact.setTaskId("task");
      taskFact.setInstanceId("instance-1");
      taskFact.setFlowKey("source-flow");
      taskFact.setState(com.ouroboros.workflow.approval.model.TaskState.CREATED);
      taskFact.setProjectionVersion(1);
      transaction.executeWithoutResult(status -> outbox.enqueue(taskFact));
      assertNotNull(taskFact.getOccurredAt());
      assertEquals(taskFact.getOccurredAt(), outbox.fromTaskJson(outbox.findById("source-task").orElseThrow())
          .orElseThrow().getOccurredAt());
      outbox.requirePersistedFact(taskFact);
      taskFact.setTaskId("foreign-task");
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> outbox.requirePersistedFact(taskFact));
      transaction.executeWithoutResult(status -> assertEquals(2L,
          new FlowInstanceFactStateService().lock("instance-1").orElseThrow().getStateSequence()));
      verifyCanonicalCaseBoundary(transaction, cursor, outbox, cases);
      if ("MySQL".equals(dialect)) { verifyEngineParticipation(source, transaction, cursor, outbox, suffix); }
      if ("H2".equals(dialect)) {
        var otherSource = new DriverManagerDataSource("jdbc:h2:mem:other" + suffix
            + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var otherMetadata = TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.event").values().stream()
            .filter(meta -> "FlowEventOutbox".equals(meta.getFullName())).toList();
        var otherStation = new SqlDataStation("other", otherSource, otherMetadata, MigrationStrategy.AUTO, "H2");
        var otherJdbc = new JdbcTemplate(otherSource);
        otherStation.generateMigrationSql(otherStation.getDataModelList()).get().forEach(otherJdbc::execute);
        var original = models.put("FlowEventOutbox", otherStation.getDataModelList().getFirst());
        var otherTransaction = new TransactionTemplate(new DataSourceTransactionManager(otherSource));
        try {
          var failure = assertThrows(com.ouroboros.exception.BusinessException.class, () ->
              transaction.executeWithoutResult(status -> otherTransaction.executeWithoutResult(otherStatus -> {
                var fact = event("cross-source", FlowInstanceEvent.EventType.SUSPENDED);
                cursor.advance(fact);
                outbox.enqueue(fact);
              })));
          assertTrue(failure.getMessage().contains("同一提交资源"), failure.getMessage());
          assertTrue(outbox.findById("cross-source").isEmpty());
        } finally { models.put("FlowEventOutbox", original); }
        transaction.executeWithoutResult(status -> assertEquals(2L, cursor.lock("instance-1").orElseThrow().getStateSequence()));
      }
      if ("MySQL".equals(dialect)) {
        var upgrade = station.generateMigrationSql(station.getDataModelList()).get();
        assertTrue(upgrade.isEmpty(), upgrade.toString());
      }
    }
  }

  private void verifyCanonicalCaseBoundary(TransactionTemplate transaction, FlowInstanceFactStateService cursor,
      FlowEventOutboxService outbox, com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService cases) {
    var submission = transaction.execute(status -> cases.reserveSubmission("sales.Order", "case-record", "source-flow"));
    var started = event("case-start", FlowInstanceEvent.EventType.STARTED);
    started.setInstanceId("case-instance");
    started.setApprovalCaseId(submission.approvalCaseId());
    started.setSubmissionRound(submission.submissionRound());
    var dispatcher = new com.ouroboros.workflow.approval.event.InstanceEventDispatcher();
    var factory = new org.springframework.aop.framework.ProxyFactory(outbox);
    factory.setProxyTargetClass(true);
    factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
        transaction.getTransactionManager(), new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
    var transactionalOutbox = FlowEventOutboxService.class.cast(factory.getProxy());
    org.springframework.test.util.ReflectionTestUtils.setField(dispatcher, "outboxService", transactionalOutbox);
    org.springframework.test.util.ReflectionTestUtils.setField(dispatcher, "factStateService", cursor);
    org.springframework.test.util.ReflectionTestUtils.setField(dispatcher, "approvalCaseService", cases);
    dispatcher.setPublisher(delivered -> cases.onInstanceEvent((FlowInstanceEvent) delivered));
    transaction.executeWithoutResult(status -> dispatcher.dispatchEvent(started));
    var current = cases.findById(submission.approvalCaseId()).orElseThrow();
    assertEquals("PROCESSING", current.getBusinessStatus());
    assertEquals(1L, current.getLastStateSequence());
    assertEquals("case-instance", current.getLastStateInstanceId());
    assertEquals("case-instance", current.getActiveExecutionId());
    assertEquals(com.ouroboros.workflow.approval.event.FlowEventOutbox.STATUS_SUCCESS,
        outbox.findById("case-start").orElseThrow().getStatus());

    var suspended = event("case-rollback", FlowInstanceEvent.EventType.SUSPENDED);
    suspended.setInstanceId("case-instance"); // inherited immutable case and record identity
    assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
      dispatcher.dispatchEvent(suspended);
      throw new IllegalStateException("source-case rollback");
    }));
    assertEquals("PROCESSING", cases.findById(submission.approvalCaseId()).orElseThrow().getBusinessStatus());
    assertTrue(outbox.findById("case-rollback").isEmpty());
    var approved = event("case-approved", FlowInstanceEvent.EventType.APPROVED);
    approved.setInstanceId("case-instance");
    transaction.executeWithoutResult(status -> dispatcher.dispatchEvent(approved));
    assertEquals(2L, approved.getStateSequence());
    current = cases.findById(submission.approvalCaseId()).orElseThrow();
    assertEquals("APPROVED", current.getBusinessStatus());
    assertNull(current.getActiveExecutionId());
    assertEquals("case-instance", current.getLastStateInstanceId());
    cases.onInstanceEvent(started); // no versioned case mutation during Outbox retries
    transaction.executeWithoutResult(status -> {
      assertFalse(cases.applyInstanceEvent(started));
      var conflicting = outbox.fromInstanceJson(outbox.findById("case-approved").orElseThrow()).orElseThrow();
      conflicting.setEventFactId("case-conflict");
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> cases.applyInstanceEvent(conflicting));
      var legacy = event("legacy-start", FlowInstanceEvent.EventType.STARTED);
      legacy.setInstanceId("case-instance");
      legacy.setApprovalCaseId(submission.approvalCaseId());
      legacy.setSubmissionRound(submission.submissionRound());
      assertFalse(cases.applyInstanceEvent(legacy));
    });
    assertEquals("APPROVED", cases.findById(submission.approvalCaseId()).orElseThrow().getBusinessStatus());

    var gap = transaction.execute(status -> cases.reserveSubmission("sales.Order", "legacy-gap", "source-flow"));
    var completedBeforeOldStartedWasDelivered = event("case-gap-approved", FlowInstanceEvent.EventType.APPROVED);
    completedBeforeOldStartedWasDelivered.setInstanceId("case-gap-instance");
    completedBeforeOldStartedWasDelivered.setApprovalCaseId(gap.approvalCaseId());
    completedBeforeOldStartedWasDelivered.setSubmissionRound(gap.submissionRound());
    transaction.executeWithoutResult(status -> dispatcher.dispatchEvent(completedBeforeOldStartedWasDelivered));
    assertEquals("APPROVED", cases.findById(gap.approvalCaseId()).orElseThrow().getBusinessStatus());
    assertEquals(1L, cases.findById(gap.approvalCaseId()).orElseThrow().getLastStateSequence());
  }

  private void verifyEngineParticipation(DriverManagerDataSource source, TransactionTemplate transaction,
      FlowInstanceFactStateService cursor, FlowEventOutboxService outbox, String suffix) {
    var config = new org.flowable.spring.SpringProcessEngineConfiguration();
    config.setDataSource(source);
    config.setTransactionManager(transaction.getTransactionManager());
    config.setDatabaseSchemaUpdate("true");
    config.setAsyncExecutorActivate(false);
    config.setEngineName("source-" + suffix);
    var engine = config.buildProcessEngine();
    try {
      String key = "sourceAtomic" + suffix;
      String bpmn = """
          <?xml version="1.0" encoding="UTF-8"?>
          <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="source">
            <process id="%s" isExecutable="true">
              <startEvent id="start"/><sequenceFlow id="toTask" sourceRef="start" targetRef="task"/>
              <userTask id="task"/><sequenceFlow id="toEnd" sourceRef="task" targetRef="end"/><endEvent id="end"/>
            </process>
          </definitions>
          """.formatted(key);
      var deployed = engine.getRepositoryService().createDeployment().addString(key + ".bpmn20.xml", bpmn).deploy();
      var verifier = new com.ouroboros.workflow.approval.flowable.event.FlowableSourceTransaction(config);
      assertThrows(com.ouroboros.exception.BusinessException.class, verifier::requireParticipation);
      var instance = transaction.execute(status -> {
        verifier.requireParticipation();
        var started = engine.getRuntimeService().startProcessInstanceByKey(key);
        var fact = event("engine-commit-" + suffix, FlowInstanceEvent.EventType.STARTED);
        fact.setInstanceId(started.getId());
        cursor.advance(fact);
        outbox.enqueue(fact);
        return started.getId();
      });
      assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processDefinitionKey(key).count());
      assertTrue(outbox.findById("engine-commit-" + suffix).isPresent());
      assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
        verifier.requireParticipation();
        engine.getRuntimeService().deleteProcessInstance(instance, "rollback-test");
        var fact = event("engine-rollback-" + suffix, FlowInstanceEvent.EventType.CANCELLED);
        fact.setInstanceId(instance);
        cursor.advance(fact);
        outbox.enqueue(fact);
        throw new IllegalStateException("roll back engine, state and outbox");
      }));
      assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processDefinitionKey(key).count());
      assertTrue(outbox.findById("engine-rollback-" + suffix).isEmpty());
      transaction.executeWithoutResult(status -> assertEquals(1L, cursor.lock(instance).orElseThrow().getStateSequence()));
      var foreignSource = new DriverManagerDataSource("jdbc:h2:mem:foreignEngine" + suffix + ";DB_CLOSE_DELAY=-1", "sa", "");
      var foreignConfig = new org.flowable.spring.SpringProcessEngineConfiguration();
      foreignConfig.setDataSource(foreignSource);
      var foreignVerifier = new com.ouroboros.workflow.approval.flowable.event.FlowableSourceTransaction(foreignConfig);
      transaction.executeWithoutResult(status -> assertThrows(com.ouroboros.exception.BusinessException.class,
          foreignVerifier::requireParticipation));
      verifyLocalStartCaseOwnership(engine, verifier, transaction, cursor, outbox, key);
      engine.getRepositoryService().deleteDeployment(deployed.getId(), true);
    } finally { engine.close(); }
  }

  private void verifyLocalStartCaseOwnership(org.flowable.engine.ProcessEngine engine,
      com.ouroboros.workflow.approval.flowable.event.FlowableSourceTransaction verifier,
      TransactionTemplate transaction, FlowInstanceFactStateService cursor, FlowEventOutboxService outbox, String key) {
    var cases = new com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService();
    var service = new com.ouroboros.workflow.approval.flowable.service.FlowableFlowInstanceService(
        engine.getTaskService(), engine.getRuntimeService(), engine.getRepositoryService(), engine.getHistoryService(),
        mock(com.ouroboros.workflow.approval.FlowDefinitionService.class), List.of(), null);
    org.springframework.test.util.ReflectionTestUtils.setField(service, "approvalCaseService", cases);
    org.springframework.test.util.ReflectionTestUtils.setField(service, "sourceTransaction", verifier);
    var committed = transaction.execute(status -> {
      var result = service.start(key, "actor", Map.of(),
          Map.of("businessRecordType", "sales.Order", "businessRecordId", "local-commit"), Map.of(),
          new java.util.concurrent.CompletableFuture<Void>());
      assertTrue(result.isSuccess(), () -> String.valueOf(result.getCause()));
      var current = cases.findByBusinessRecord("sales.Order", "local-commit", key).orElseThrow();
      assertEquals(result.get(), current.getActiveExecutionId());
      assertEquals(current.getApprovalCaseId(), engine.getRuntimeService().getVariable(result.get(), "approvalCaseId"));
      var fact = event("local-commit-" + key, FlowInstanceEvent.EventType.STARTED);
      fact.setFlowKey(key); fact.setInstanceId(result.get());
      cursor.advance(fact); outbox.enqueue(fact);
      return result.get();
    });
    assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(committed).singleResult());
    assertTrue(outbox.findById("local-commit-" + key).isPresent());
    var rolledBackInstance = new java.util.concurrent.atomic.AtomicReference<String>();
    assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
      var result = service.start(key, "actor", Map.of(),
          Map.of("businessRecordType", "sales.Order", "businessRecordId", "local-rollback"), Map.of(),
          new java.util.concurrent.CompletableFuture<Void>());
      assertTrue(result.isSuccess(), () -> String.valueOf(result.getCause()));
      rolledBackInstance.set(result.get());
      var fact = event("local-rollback-" + key, FlowInstanceEvent.EventType.STARTED);
      fact.setFlowKey(key); fact.setInstanceId(result.get()); cursor.advance(fact); outbox.enqueue(fact);
      throw new IllegalStateException("roll back local start, case and source fact");
    }));
    assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(rolledBackInstance.get()).singleResult());
    assertTrue(cases.findByBusinessRecord("sales.Order", "local-rollback", key).isEmpty());
    assertTrue(outbox.findById("local-rollback-" + key).isEmpty());
  }

  private FlowInstanceEvent event(String factId, FlowInstanceEvent.EventType type) {
    var event = new FlowInstanceEvent();
    event.setEventFactId(factId);
    event.setInstanceId("instance-1");
    event.setFlowKey("source-flow");
    event.setEventType(type);
    return event;
  }
}
