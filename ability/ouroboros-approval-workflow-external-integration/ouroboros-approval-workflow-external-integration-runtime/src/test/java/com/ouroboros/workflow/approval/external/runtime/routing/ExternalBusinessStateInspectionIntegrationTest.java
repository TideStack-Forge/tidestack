package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.workflow.WorkflowBusinessFactProjectionAutoConfiguration;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection;
import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;
import com.ouroboros.data.model.DataModelFieldMeta;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalInstanceState;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.external.ExternalInstanceDetailReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingServiceImpl;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExternalBusinessStateInspectionIntegrationTest {
  private MockedStatic<DataModelCenter> center;
  private SqlDataStation station;
  private JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private ExternalInstanceBindingService instances;
  private ExternalResultBindingServiceImpl results;
  private final ApprovalCaseService cases = new ApprovalCaseService();
  private final FlowInstanceFactStateService states = new FlowInstanceFactStateService();
  private final FlowEventOutboxService outbox = new FlowEventOutboxService();
  private ApprovalCaseService.Submission submission;
  private WorkflowEngineMapping mapping;
  private ExternalInstanceDetailReader reader;
  private ConnectionAwareAdapterResolver adapters;
  private ExternalBusinessStateInspection inspection;
  private Map<String, DataModel> models;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String url = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
    boolean mysql = url != null && !url.isBlank();
    var source = mysql ? new DriverManagerDataSource(url, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"),
        System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD")) : new DriverManagerDataSource(
            "jdbc:h2:mem:external_inspection_" + suffix + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); jdbc = new JdbcTemplate(source);
    var metadata = new ArrayList<DataModelMeta>();
    add(metadata, "com.ouroboros.workflow.approval.external.runtime.binding", "ExternalInstanceBinding");
    add(metadata, "com.ouroboros.workflow.approval.external.runtime.binding", "ExternalResultBinding");
    add(metadata, "com.ouroboros.workflow.approval.lifecycle", "ApprovalCase");
    add(metadata, "com.ouroboros.workflow.approval.event", "FlowEventOutbox");
    add(metadata, "com.ouroboros.workflow.approval.event", "FlowInstanceFactState");
    add(metadata, "com.ouroboros.businessmodel.workflow.store", "WorkflowBusinessFactCursor");
    add(metadata, "com.ouroboros.businessmodel.workflow.store", "WorkflowBusinessFactReceipt");
    var data = new DataModelMeta(); data.setNamespace("sales"); data.setName("Order"); data.setRawName("inspection_order");
    data.setPrimaryKeys(List.of("id"));
    var id = new DataModelFieldMeta(); id.setName("id"); id.setRawName("id"); id.setType("Long");
    var statusField = new DataModelFieldMeta(); statusField.setName("status"); statusField.setRawName("status"); statusField.setType("String");
    data.setFields(List.of(id, statusField)); metadata.add(data);
    metadata.forEach(meta -> meta.setRawName(meta.getRawName() + "_" + suffix));
    station = new SqlDataStation("external-inspection", source, metadata, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
    station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
    models = station.getDataModelList().stream().collect(Collectors.toMap(DataModel::getFullName, model -> model));
    center = mockStatic(DataModelCenter.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
    center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call -> Optional.ofNullable(models.get(call.<String>getArgument(0))));
    center.when(DataModelCenter::hasSnapshot).thenReturn(true);
    models.get("sales.Order").insert(Map.of("id", 42L, "status", "draft")).get();
    instances = new ExternalInstanceBindingService(); results = new ExternalResultBindingServiceImpl();
    submission = transaction.execute(status -> cases.reserveSubmission("sales.Order", "42", "approval"));
    transaction.executeWithoutResult(status -> cases.bindExecution(submission.approvalCaseId(), 1, "instance"));
    mapping = new WorkflowEngineMapping(); mapping.setId("mapping"); mapping.setVersion(1); mapping.setScope("tenant");
    mapping.setFlowKey("approval"); mapping.setAdapterId("engine"); mapping.setConnectionId("connection");
    mapping.setAuthority("EXTERNAL_ENGINE");
    mapping.setResultMappingsJson("""
        [{"externalStatus":"APPROVED","systemEvent":"APPROVED","outcome":"approved"},
         {"externalStatus":"REJECTED","systemEvent":"REJECTED","outcome":"rejected"},
         {"externalStatus":"CANCELED","systemEvent":"CANCELLED","outcome":"cancelled"},
         {"externalStatus":"ERROR","systemEvent":"FAILED","outcome":"failed"}]
        """);
    var mappings = mock(WorkflowEngineMappingService.class);
    when(mappings.findByIdForSystem("mapping")).thenReturn(Optional.of(mapping));
    adapters = mock(ConnectionAwareAdapterResolver.class); reader = mock(ExternalInstanceDetailReader.class);
    when(adapters.resolvePort(mapping, ExternalInstanceDetailReader.class)).thenReturn(reader);
    when(reader.getInstance(any())).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      return Try.success(snapshot("RUNNING", null, null));
    });
    inspection = new ExternalBusinessStateInspection(instances, results, mappings, adapters);
  }

  @AfterEach
  void tearDown() {
    try { if (station != null) { station.getDataModelList().forEach(model -> jdbc.execute("DROP TABLE " + model.getRawName())); } }
    finally { if (center != null) { center.close(); } }
  }

  @Test
  void missingOrBuiltInBindingDoesNotQueryARemoteEngine() {
    assertNull(inspection.prepare("approval", "missing"));
    createInstance("RUNNING", null); mapping.setAuthority("OUROBOROS");
    assertNull(inspection.prepare("approval", "instance")); verifyNoInteractions(adapters);
  }

  @Test
  void runningAndResumedUseThePersistedLifecycleHistoryAndPreserveTheOfficialIdentity() {
    createInstance("RUNNING", null);
    var prepared = inspection.prepare("approval", "instance");
    var state = locked(prepared); assertEquals(FlowInstanceEvent.EventType.STARTED, state.eventType());
    assertEquals(new FlowInstanceEvent.BusinessContext("sales.Order", "42"), state.businessContext());
    assertEquals(submission.approvalCaseId(), state.approvalCaseId()); assertEquals(1, state.submissionRound());
    assertEquals("sales.Order.processCallback", state.callbackFlow());
    transaction.executeWithoutResult(status -> {
      var resumed = new FlowInstanceEvent(); resumed.setEventFactId("resumed"); resumed.setFlowKey("approval");
      resumed.setInstanceId("instance"); resumed.setEventType(FlowInstanceEvent.EventType.RESUMED); outbox.enqueue(resumed);
    });
    assertEquals(FlowInstanceEvent.EventType.RESUMED, locked(inspection.prepare("approval", "instance")).eventType());
    verify(reader, times(2)).getInstance(any());
  }

  @ParameterizedTest
  @CsvSource({"FINISHED,APPROVED,approved,APPROVED", "FINISHED,REJECTED,rejected,REJECTED",
      "CANCELED,CANCELED,withdrawn,CANCELLED", "FINISHED,CANCELED,cancelled,CANCELLED", "FINISHED,ERROR,failed,FAILED"})
  void terminalStateMustAgreeWithTheCommittedBinding(String lifecycle, String result, String outcome, String type) {
    createInstance("FINISHED", outcome);
    if ("withdrawn".equals(outcome)) {
      mapping.setResultMappingsJson(mapping.getResultMappingsJson().replace("cancelled", "withdrawn"));
    }
    doReturn(Try.success(snapshot(lifecycle, result, outcome))).when(reader).getInstance(any());
    var state = locked(inspection.prepare("approval", "instance"));
    assertEquals(type, state.eventType().name()); assertEquals(outcome, state.outcome());
  }

  @Test
  void suspendedSnapshotUsesTheSuspendedBinding() {
    createInstance("SUSPENDED", null);
    doReturn(Try.success(snapshot("SUSPENDED", null, null))).when(reader).getInstance(any());
    assertEquals(FlowInstanceEvent.EventType.SUSPENDED, locked(inspection.prepare("approval", "instance")).eventType());
  }

  @Test
  void resultOnlyBindingUsesItsTrackingIdentityAndTheSamePinnedRemoteReader() {
    createResult("FINISHED", "approved");
    doReturn(Try.success(snapshot("FINISHED", "APPROVED", null))).when(reader).getInstance(any());
    var state = locked(inspection.prepare("approval", "instance"));
    assertEquals(FlowInstanceEvent.EventType.APPROVED, state.eventType()); assertEquals("instance", state.instanceId());
    assertNull(state.callbackFlow());
  }

  @Test
  void bindingOrPinnedMappingChangesInvalidateAPreparedRemoteRead() {
    createInstance("RUNNING", null);
    var prepared = inspection.prepare("approval", "instance");
    transaction.executeWithoutResult(status -> instances.updateStatus("instance", "SUSPENDED", null, "2", null, "suspend").get());
    assertThrows(BusinessException.class, () -> locked(prepared));
    var updated = inspection.prepare("approval", "instance"); mapping.setVersion(2);
    assertThrows(BusinessException.class, () -> locked(updated));
  }

  @Test
  void divergentUnknownOrMalformedRemoteStateCannotEstablishABaseline() {
    createInstance("RUNNING", null);
    doReturn(Try.success(snapshot("FINISHED", "APPROVED", "approved"))).when(reader).getInstance(any());
    assertThrows(BusinessException.class, () -> locked(inspection.prepare("approval", "instance")));
    doReturn(Try.success(snapshot("UNKNOWN", null, null))).when(reader).getInstance(any());
    assertThrows(BusinessException.class, () -> inspection.prepare("approval", "instance"));
    doReturn(Try.failure(new BusinessException("remote unavailable"))).when(reader).getInstance(any());
    assertThrows(BusinessException.class, () -> inspection.prepare("approval", "instance"));
    doReturn(null).when(reader).getInstance(any());
    assertThrows(BusinessException.class, () -> inspection.prepare("approval", "instance"));
  }

  @Test
  void foreignRemoteInstanceOrFlowOrConflictingBindingsAreRejected() {
    createInstance("RUNNING", null);
    assertThrows(BusinessException.class, () -> inspection.prepare("wrong-flow", "instance"));
    doReturn(Try.success(new ExternalInstanceSnapshot("foreign", "RUNNING", null, null, null, Map.of())))
        .when(reader).getInstance(any());
    assertThrows(BusinessException.class, () -> inspection.prepare("approval", "instance"));
    createResult("RUNNING", null);
    assertThrows(BusinessException.class, () -> inspection.prepare("approval", "instance"));
  }

  @Test
  void remoteReadRequiresNoTransactionAndPreparedCheckRequiresAnActualCommitResource() {
    createInstance("RUNNING", null);
    transaction.executeWithoutResult(status -> assertThrows(BusinessException.class,
        () -> inspection.prepare("approval", "instance")));
    var prepared = inspection.prepare("approval", "instance");
    assertThrows(BusinessException.class, prepared::get);
    assertEquals(FlowInstanceEvent.EventType.STARTED, locked(prepared).eventType());
    verify(reader, times(1)).getInstance(any());
  }

  @Test
  void pinnedResultMappingControlsTheBaselineAndConflictingProviderOutcomesAreRejected() {
    createInstance("FINISHED", "accepted");
    mapping.setResultMappingsJson("[{\"externalStatus\":\"APPROVED\",\"systemEvent\":\"REJECTED\",\"outcome\":\"accepted\"}]");
    doReturn(Try.success(snapshot("FINISHED", "APPROVED", null))).when(reader).getInstance(any());
    assertEquals(FlowInstanceEvent.EventType.REJECTED, locked(inspection.prepare("approval", "instance")).eventType());
    doReturn(Try.success(snapshot("FINISHED", "APPROVED", "foreign"))).when(reader).getInstance(any());
    assertThrows(BusinessException.class, () -> locked(inspection.prepare("approval", "instance")));
    mapping.setResultMappingsJson("[]");
    assertThrows(BusinessException.class, () -> locked(inspection.prepare("approval", "instance")));
    mapping.setResultMappingsJson("invalid");
    assertThrows(IllegalStateException.class, () -> locked(inspection.prepare("approval", "instance")));
  }

  @Test
  void aBindingOnAnotherDataSourceCannotBeUsedAsTransactionalSourceProof() {
    createInstance("RUNNING", null);
    var prepared = inspection.prepare("approval", "instance");
    DataModel canonical = models.get("ExternalInstanceBinding");
    var foreignSource = new DriverManagerDataSource("jdbc:h2:mem:foreign_" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE", "sa", "");
    var foreignStation = new SqlDataStation("foreign", foreignSource, List.of(TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.external.runtime.binding").get("ExternalInstanceBinding").deepCopy()), MigrationStrategy.AUTO, "H2");
    DataModel foreign = spy(canonical);
    var adapter = mock(com.ouroboros.data.adapter.DataAdapter.class, org.mockito.AdditionalAnswers.delegatesTo(canonical.getAdapter()));
    doAnswer(call -> foreignStation.getDataModelList().getFirst().getAdapter().requireTransactionResource())
        .when(adapter).requireTransactionResource();
    when(foreign.getAdapter()).thenReturn(adapter);
    models.put("ExternalInstanceBinding", foreign);
    try {
      transaction.executeWithoutResult(status -> new TransactionTemplate(new DataSourceTransactionManager(foreignSource))
          .executeWithoutResult(other -> assertTrue(assertThrows(BusinessException.class, prepared::get).getMessage().contains("同一提交资源"))));
    } finally { models.put("ExternalInstanceBinding", canonical); }
  }

  @Test
  void productionReceiverSharesTheBaselineCallbackAndReceiptTransactionAndDeduplicatesAfterRestart() {
    createInstance("RUNNING", null);
    var legacy = legacy();
    var eventPort = mock(BusinessEventPort.class);
    withProjection(eventPort, receiver -> {
      assertThrows(IllegalStateException.class, () -> receiver.projectInstance(legacy, callbackBinding(), () -> {
        models.get("sales.Order").update(42L, Map.of("status", "processing")).get();
        throw new IllegalStateException("rollback callback");
      }));
      assertEquals("draft", models.get("sales.Order").get(42L).get().get("status"));
      assertTrue(Optional.ofNullable(states.getByPrimaryKey("instance")).isEmpty());
      assertEquals(0L, models.get("WorkflowBusinessFactReceipt").count(Map.of()).get());
      assertTrue(receiver.projectInstance(legacy, callbackBinding(), () ->
          models.get("sales.Order").update(42L, Map.of("status", "processing")).get()));
    });
    assertEquals(0L, Optional.ofNullable(states.getByPrimaryKey("instance")).orElseThrow().getStateSequence());
    assertEquals(1L, models.get("WorkflowBusinessFactReceipt").count(Map.of()).get());
    doReturn(Try.failure(new BusinessException("unavailable"))).when(reader).getInstance(any());
    withProjection(eventPort, receiver -> assertTrue(receiver.projectInstance(legacy, callbackBinding(), () -> fail("duplicate"))));
    verifyNoInteractions(eventPort);
  }

  @Test
  void aBindingCommittedWhileTheRemoteReadIsPendingInvalidatesTheWholeReceiverAttempt() {
    createInstance("RUNNING", null);
    var legacy = legacy();
    try (var executor = Executors.newSingleThreadExecutor()) {
      doAnswer(call -> {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        executor.submit(() -> inThread(() -> transaction.execute(status ->
            instances.updateStatus("instance", "SUSPENDED", null, "2", null, "suspended").get()))).get(5, TimeUnit.SECONDS);
        return Try.success(snapshot("RUNNING", null, null));
      }).when(reader).getInstance(any());
      var eventPort = mock(BusinessEventPort.class);
      withProjection(eventPort, receiver -> assertThrows(BusinessException.class,
          () -> receiver.projectInstance(legacy, callbackBinding(), () -> fail("stale remote snapshot"))));
      assertTrue(Optional.ofNullable(states.getByPrimaryKey("instance")).isEmpty());
      assertEquals(0L, models.get("WorkflowBusinessFactReceipt").count(Map.of()).get());
      assertEquals("PENDING", outbox.findById(legacy.getEventFactId()).orElseThrow().getStatus());
      verifyNoInteractions(eventPort);
    }
  }

  @Test
  void aSourceCommandWaitingOnTheCaseAdvancesAfterTheLegacyBaselineCommits() {
    createInstance("RUNNING", null);
    var legacy = legacy();
    var updated = new CountDownLatch(1);
    var eventPort = mock(BusinessEventPort.class);
    try (var executor = Executors.newSingleThreadExecutor()) {
      withProjection(eventPort, receiver -> assertTrue(receiver.projectInstance(legacy, callbackBinding(), () -> {
        var sourceCommand = executor.submit(() -> inThread(() -> transaction.execute(status -> {
          instances.updateStatus("instance", "FINISHED", "approved", "2", null, "approved").get();
          updated.countDown();
          var event = event(FlowInstanceEvent.EventType.APPROVED); event.setOutcome("approved");
          cases.lockForSource(event); states.advance(event); cases.applyInstanceEvent(event); outbox.enqueue(event);
          return true;
        })));
        try {
          assertTrue(updated.await(5, TimeUnit.SECONDS));
          assertThrows(java.util.concurrent.TimeoutException.class, () -> sourceCommand.get(150, TimeUnit.MILLISECONDS));
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt(); throw new IllegalStateException(error);
        }
      })));
    }
    assertEquals(1L, Optional.ofNullable(states.getByPrimaryKey("instance")).orElseThrow().getStateSequence());
    assertEquals("APPROVED", Optional.ofNullable(states.getByPrimaryKey("instance")).orElseThrow().getEventType());
    assertEquals("FINISHED", instances.findByLocalId("instance").orElseThrow().getStatus());
    assertEquals(1L, models.get("WorkflowBusinessFactReceipt").count(Map.of()).get());
    verifyNoInteractions(eventPort);
  }

  private FlowInstanceEvent legacy() {
    var event = event(FlowInstanceEvent.EventType.STARTED);
    transaction.executeWithoutResult(status -> outbox.enqueue(event));
    return event;
  }

  private FlowInstanceEvent event(FlowInstanceEvent.EventType type) {
    var event = new FlowInstanceEvent(); event.setEventFactId(UUID.randomUUID().toString());
    event.setFlowKey("approval"); event.setInstanceId("instance"); event.setEventType(type);
    event.setBusinessContext(new FlowInstanceEvent.BusinessContext("sales.Order", "42"));
    event.setApprovalCaseId(submission.approvalCaseId()); event.setSubmissionRound(1);
    event.setCallbackFlow("sales.Order.processCallback"); return event;
  }

  private CallbackBinding callbackBinding() {
    return new CallbackBinding("approval", "sales.Order.processCallback", "sales.Order", "status", Map.of());
  }

  private void withProjection(BusinessEventPort eventPort, Consumer<ApprovalBusinessFactProjection> operation) {
    var model = mock(BusinessModel.class); when(model.getFullName()).thenReturn("sales.Order");
    when(model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId")).thenReturn(Optional.of("approval"));
    var validator = mock(ApprovalBusinessBindingValidator.class);
    when(validator.prepareInstanceInspection("approval", "instance")).thenAnswer(call -> inspection.prepare("approval", "instance"));
    try (var business = mockStatic(BusinessModelCenter.class); var context = new AnnotationConfigApplicationContext()) {
      business.when(() -> BusinessModelCenter.getBusinessModel("sales.Order")).thenReturn(Optional.of(model));
      context.registerBean(BusinessEventPort.class, () -> eventPort);
      context.registerBean(FlowEventOutboxService.class, () -> outbox);
      context.registerBean(FlowInstanceFactStateService.class, () -> states);
      context.registerBean(ApprovalCaseService.class, () -> cases);
      context.registerBean(org.springframework.transaction.PlatformTransactionManager.class, () -> transaction.getTransactionManager());
      context.registerBean(ApprovalBusinessBindingValidator.class, () -> validator);
      context.register(WorkflowBusinessFactProjectionAutoConfiguration.class); context.refresh();
      operation.accept(context.getBean(ApprovalBusinessFactProjection.class));
    }
  }

  private <T> T inThread(Supplier<T> work) {
    try (var scoped = mockStatic(DataModelCenter.class)) {
      scoped.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call -> Optional.ofNullable(models.get(call.<String>getArgument(0))));
      scoped.when(DataModelCenter::hasSnapshot).thenReturn(true);
      return work.get();
    }
  }

  private ApprovalInstanceState locked(Supplier<ApprovalInstanceState> prepared) {
    return transaction.execute(status -> { cases.lockByBusinessRecord("sales.Order", "42", "approval").orElseThrow(); return prepared.get(); });
  }

  private ExternalInstanceSnapshot snapshot(String lifecycle, String result, String outcome) {
    return new ExternalInstanceSnapshot("remote", lifecycle, result, outcome, null, Map.of());
  }

  private void createInstance(String status, String outcome) {
    var binding = new ExternalInstanceBinding(); binding.setLocalInstanceId("instance"); binding.setFlowKey("approval");
    binding.setScope("tenant"); binding.setMappingId("mapping"); binding.setMappingVersion(1); binding.setAdapterId("engine");
    binding.setConnectionId("connection"); binding.setExternalInstanceId("remote"); binding.setExternalDefinitionId("definition");
    binding.setStatus(status); binding.setOutcome(outcome); binding.setBusinessRecordType("sales.Order"); binding.setBusinessRecordId("42");
    binding.setApprovalCaseId(submission.approvalCaseId()); binding.setSubmissionRound(1); binding.setCallbackFlow("sales.Order.processCallback");
    transaction.executeWithoutResult(tx -> instances.create(binding).get());
  }

  private void createResult(String status, String outcome) {
    var binding = new ExternalResultBinding(); binding.setTrackingId("instance"); binding.setFlowKey("approval");
    binding.setScope("tenant"); binding.setMappingId("mapping"); binding.setMappingVersion(1); binding.setAdapterId("engine");
    binding.setConnectionId("connection"); binding.setExternalInstanceId("remote"); binding.setExternalDefinitionId("definition");
    binding.setStatus(status); binding.setOutcome(outcome); binding.setBusinessRecordType("sales.Order"); binding.setBusinessRecordId("42");
    binding.setApprovalCaseId(submission.approvalCaseId()); binding.setSubmissionRound(1);
    transaction.executeWithoutResult(tx -> results.create(binding).get());
  }

  private void add(List<DataModelMeta> metadata, String packageName, String name) {
    metadata.add(TypedModelMetaHelper.scanModelMate(packageName).get(name).deepCopy());
  }
}
