package com.ouroboros.workflow.approval.external.runtime.start;

import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource;
import com.ouroboros.businessmodel.action.DefaultBusinessActionRuntime;
import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.workflow.WorkflowBusinessActionValidator;
import com.ouroboros.logicflow.DefaultLogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.model.FlowNodePortMeta;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.BuiltInApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedBusinessBindingValidator;
import com.ouroboros.workflow.approval.external.runtime.routing.ExternalBusinessStateInspection;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.common.app.util.SpringContextUtil;
import org.springframework.context.support.GenericApplicationContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
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
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowInstanceService;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionExecutionService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalUserIdentityResolver;
import com.ouroboros.workflow.approval.BuiltInFlowInstanceService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingServiceImpl;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies the existing start Saga against real SQL, cases, source cursors and Outbox. */
class ExternalStartFactTransactionIntegrationTest {
  private MockedStatic<DataModelCenter> center;
  private SqlDataStation station;
  private Map<String, DataModel> models;
  private JdbcTemplate jdbc;
  private DataSourceTransactionManager manager;
  private ApprovalCaseService cases;
  private ApprovalCaseService rawCases;
  private ExternalStartFactService facts;
  private ExternalStartFactService rawFacts;
  private ExternalResultBindingService results;
  private ExternalResultBindingServiceImpl rawResults;
  private ExternalInstanceBindingService instances;
  private WorkflowEngineMappingService mappings;
  private WorkflowEngineMapping mapping;
  private ConnectionAwareAdapterResolver resolver;
  private ExternalWorkflowStarter starter;
  private InstanceEventDispatcher dispatcher;
  private FlowEventOutboxService outbox;
  private FlowEventOutboxService rawOutbox;
  private DefaultListableBeanFactory providers;
  private final List<FlowInstanceEvent> delivered = new ArrayList<>();

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String url = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
    boolean mysql = url != null && !url.isBlank();
    var source = mysql ? new DriverManagerDataSource(url, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"),
        System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD")) : new DriverManagerDataSource(
            "jdbc:h2:mem:external_start_" + suffix + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    manager = new DataSourceTransactionManager(source); jdbc = new JdbcTemplate(source);
    var metadata = new ArrayList<DataModelMeta>();
    add(metadata, "com.ouroboros.workflow.approval.external.runtime.start", "ExternalStartFact");
    add(metadata, "com.ouroboros.workflow.approval.external.runtime.binding", "ExternalResultBinding");
    add(metadata, "com.ouroboros.workflow.approval.external.runtime.binding", "ExternalInstanceBinding");
    add(metadata, "com.ouroboros.workflow.approval.lifecycle", "ApprovalCase");
    add(metadata, "com.ouroboros.workflow.approval.event", "FlowEventOutbox");
    add(metadata, "com.ouroboros.workflow.approval.event", "FlowInstanceFactState");
    metadata.forEach(meta -> meta.setRawName(meta.getRawName() + "_" + suffix));
    station = new SqlDataStation("external-start-saga", source, metadata, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
    station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
    models = station.getDataModelList().stream().collect(Collectors.toMap(DataModel::getFullName, model -> model));
    center = mockStatic(DataModelCenter.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
    center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call -> Optional.ofNullable(models.get(call.<String>getArgument(0))));
    center.when(DataModelCenter::hasSnapshot).thenReturn(true);
    rawCases = spy(new ApprovalCaseService()); cases = transactional(rawCases, ApprovalCaseService.class);
    rawFacts = spy(new ExternalStartFactService());
    ReflectionTestUtils.setField(rawFacts, "approvalCaseService", cases);
    facts = transactional(rawFacts, ExternalStartFactService.class);
    rawResults = spy(new ExternalResultBindingServiceImpl());
    results = transactional(rawResults, ExternalResultBindingService.class);
    instances = transactional(new ExternalInstanceBindingService(), ExternalInstanceBindingService.class);
    rawOutbox = spy(new FlowEventOutboxService()); outbox = transactional(rawOutbox, FlowEventOutboxService.class);
    dispatcher = new InstanceEventDispatcher();
    ReflectionTestUtils.setField(dispatcher, "outboxService", outbox);
    ReflectionTestUtils.setField(dispatcher, "factStateService", transactional(new FlowInstanceFactStateService(), FlowInstanceFactStateService.class));
    ReflectionTestUtils.setField(dispatcher, "approvalCaseService", cases);
    dispatcher.setPublisher(event -> delivered.add((FlowInstanceEvent) event));
    mapping = new WorkflowEngineMapping(); mapping.setId("mapping"); mapping.setVersion(1);
    mapping.setScope("tenant"); mapping.setFlowKey("approval"); mapping.setAdapterId("remote");
    mapping.setConnectionId("connection"); mapping.setExternalDefinitionId("definition"); mapping.setAuthority("EXTERNAL_ENGINE");
    mappings = mock(WorkflowEngineMappingService.class);
    when(mappings.findActive("tenant", "approval")).thenReturn(Optional.of(mapping));
    when(mappings.findByIdForSystem("mapping")).thenReturn(Optional.of(mapping));
    when(mappings.acquireBindingReferenceLock("mapping")).thenReturn(true);
    ReflectionTestUtils.setField(rawFacts, "mappingService", mappings);
    ReflectionTestUtils.setField(results, "mappingService", mappings);
    ReflectionTestUtils.setField(instances, "mappingService", mappings);
    providers = new DefaultListableBeanFactory(); providers.registerSingleton("cases", cases);
    starter = mock(ExternalWorkflowStarter.class); resolver = mock(ConnectionAwareAdapterResolver.class);
    when(resolver.resolvePort(mapping, ExternalWorkflowStarter.class)).thenReturn(starter);
    when(starter.start(any())).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      var fact = onlyFact();
      var current = cases.findById(fact.getApprovalCaseId()).orElseThrow();
      assertEquals(fact.getSubmissionRound(), current.getSubmissionRound());
      assertEquals("PENDING", current.getBusinessStatus());
      return Try.success(new ExternalStartResult("remote-instance", "https://approval.invalid/instance", null));
    });
  }

  @AfterEach
  void tearDown() {
    try {
      if (station != null) { for (var model : station.getDataModelList()) { jdbc.execute("DROP TABLE " + model.getRawName()); } }
    } finally { if (center != null) { center.close(); } }
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "FULL_PROCESS", "RESULT_ONLY"})
  void commonHttpStartRunsTheActualRouterForEveryMapping(String mode) throws Exception {
    var registry = mock(WorkflowEngineAdapterRegistry.class);
    when(registry.integrationModeOf("remote")).thenReturn(mode);
    when(mappings.findActive("tenant", "approval")).thenReturn(
        "INTERNAL".equals(mode) ? Optional.empty() : Optional.of(mapping));
    var builtIn = mock(BuiltInFlowInstanceService.class);
    when(builtIn.start(eq("approval"), eq("actor"), any(), any(), any(), any(java.util.concurrent.CompletableFuture.class)))
        .thenReturn(Try.success("built-in-execution"));
    providers.registerSingleton("builtIn", builtIn);
    var router = new RoutedFlowInstanceService(providers.getBeanProvider(BuiltInFlowInstanceService.class), mappings,
        registry, resolver, instances, results, dispatcher, manager, "internal", () -> "tenant",
        mock(ExternalUserIdentityResolver.class), facts, mock(ExternalActionExecutionService.class),
        providers.getBeanProvider(ApprovalCaseService.class), executor());
    var controller = new com.ouroboros.workflow.approval.web.controller.ApprovalWorkflowInstanceController();
    ReflectionTestUtils.setField(controller, "flowInstanceService", router);
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    try (var identity = mockStatic(com.ouroboros.workflow.approval.CurrentUserSupplier.class)) {
      identity.when(com.ouroboros.workflow.approval.CurrentUserSupplier::getCurrentUserId).thenReturn(Optional.of("actor"));
      var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
          .post("/api/approval-workflow/instance/approval/start").contentType("application/json").content("""
              {"userId":"forged","businessRecordType":"sales.Order","businessRecordId":"42",
               "idempotencyKey":"http-request","formData":{"amount":1200},
               "taskAssignee":{"review":[{"value":"reviewer"}]}}
              """)).andReturn().getResponse();
      assertEquals(200, response.getStatus());
      var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
      assertEquals(0, body.path("code").asInt(), body.toString());
      assertEquals("STARTED", body.path("startResult").path("status").asText());
      if ("INTERNAL".equals(mode)) {
        assertEquals("built-in-execution", body.path("data").asText()); verifyNoInteractions(starter);
      } else {
        assertEquals(onlyFact().getTrackingId(), body.path("data").asText());
        assertEquals("actor", onlyFact().getActorUserId());
        assertEquals(ExternalStartFact.STATUS_STARTED, onlyFact().getStatus());
        assertTrue(outbox.findById(onlyFact().getId()).isPresent());
        verify(starter, times(1)).start(any());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"FULL_PROCESS", "RESULT_ONLY"})
  void unifiedStartSupportsIndependentUnboundApprovalsAndIdempotentRetry(String mode) {
    var registry = mock(WorkflowEngineAdapterRegistry.class);
    when(registry.integrationModeOf("remote")).thenReturn(mode);
    providers.registerSingleton("builtIn", mock(BuiltInFlowInstanceService.class));
    var routed = new RoutedFlowInstanceService(providers.getBeanProvider(BuiltInFlowInstanceService.class), mappings,
        registry, resolver, instances, results, dispatcher, manager, "internal", () -> "tenant",
        mock(ExternalUserIdentityResolver.class), facts, mock(ExternalActionExecutionService.class),
        providers.getBeanProvider(ApprovalCaseService.class), executor());
    doAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      var request = (com.ouroboros.workflow.approval.external.ExternalStartCommand) call.getArgument(0);
      assertNull(request.businessRecord());
      return Try.success(new ExternalStartResult(UUID.randomUUID().toString(), null, null));
    }).when(starter).start(any());
    var firstCommand = new com.ouroboros.workflow.approval.FlowStartCommand(
        "approval", "actor", Map.of(), Map.of("callbackFlow", "unbound.callback"), Map.of("amount", 10), null, "first");
    var first = routed.start(firstCommand); assertSuccess(first);
    assertEquals(com.ouroboros.workflow.approval.FlowStartResult.Status.STARTED, first.get().status());
    assertEquals(first.get().executionId(), routed.start(firstCommand).get().executionId());
    var second = routed.start(new com.ouroboros.workflow.approval.FlowStartCommand(
        "approval", "actor", Map.of(), Map.of("callbackFlow", "unbound.callback"), Map.of("amount", 20), null, "second"));
    assertSuccess(second);
    assertNotEquals(first.get().executionId(), second.get().executionId());
    assertEquals(2L, models.get("ExternalStartFact").count(Map.of()).get());
    assertEquals(0L, models.get("ApprovalCase").count(Map.of()).get());
    assertEquals(2L, models.get("FlowEventOutbox").count(Map.of()).get());
    assertEquals(2L, models.get("RESULT_ONLY".equals(mode) ? "ExternalResultBinding" : "ExternalInstanceBinding")
        .count(Map.of()).get());
    verify(starter, times(2)).start(any());
  }

  @Test
  void aBusinessRollbackCannotEraseTheCommittedIntentCaseOrStartedFact() {
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      var result = service().start(command()); assertSuccess(result);
      assertEquals(ExternalStartFact.STATUS_STARTED, result.get().status());
      assertTrue(delivered.isEmpty());
      status.setRollbackOnly();
    });
    var fact = onlyFact();
    assertEquals(ExternalStartFact.STATUS_STARTED, fact.getStatus());
    var current = cases.findById(fact.getApprovalCaseId()).orElseThrow();
    assertEquals(fact.getTrackingId(), current.getActiveExecutionId());
    assertEquals("PROCESSING", current.getBusinessStatus());
    var event = outbox.fromInstanceJson(outbox.findById(fact.getId()).orElseThrow()).orElseThrow();
    assertEquals(fact.getId(), event.getEventFactId()); assertEquals(1L, event.getStateSequence());
    assertEquals(fact.getRepliedAt(), event.getEventTime());
    assertEquals(FlowEventOutbox.STATUS_PENDING, outbox.findById(fact.getId()).orElseThrow().getStatus());
    assertSuccess(service().start(command()));
    assertEquals(1L, models.get("ApprovalCase").count(Map.of()).get());
    verify(starter, times(1)).start(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "FULL_PROCESS", "RESULT_ONLY"})
  void sameBusinessActionAndRealLogicFlowStartAfterOnlyTheEngineMappingChanges(String mode) throws Exception {
    var registry = mock(WorkflowEngineAdapterRegistry.class);
    when(registry.integrationModeOf("remote")).thenReturn(mode);
    when(registry.capabilitiesOf("remote")).thenReturn(java.util.Set.of(
        Capability.EXTERNAL_START));
    when(mappings.findActive("tenant", "approval")).thenReturn(
        "INTERNAL".equals(mode) ? Optional.empty() : Optional.of(mapping));
    var builtIn = mock(BuiltInFlowInstanceService.class);
    when(builtIn.start(eq("approval"), eq("actor"), any(), any(), any(), any(java.util.concurrent.CompletableFuture.class)))
        .thenReturn(Try.success("built-in-execution"));
    providers.registerSingleton("builtIn", builtIn);
    var routed = new RoutedFlowInstanceService(providers.getBeanProvider(BuiltInFlowInstanceService.class), mappings,
        registry, resolver, instances, results, dispatcher, manager, "internal", () -> "tenant",
        mock(ExternalUserIdentityResolver.class), facts, mock(ExternalActionExecutionService.class),
        providers.getBeanProvider(ApprovalCaseService.class), executor());
    var bindings = new RoutedBusinessBindingValidator(
        mock(BuiltInApprovalBusinessBindingValidator.class), mappings, () -> "tenant",
        instances, mock(ExternalTaskBindingService.class),
        cases, registry, mock(ExternalBusinessStateInspection.class), "internal");
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("sales"); meta.setName("Order");
    meta.setExtraProp("workflow", Map.of("isEnabled", true, "bindings", List.of(Map.of("processId", "approval"))));
    var modelProvider = mock(BusinessModelProvider.class);
    when(modelProvider.getBusinessModel("sales.Order")).thenReturn(Optional.of(
        new DefaultBusinessModel(meta, modelProvider)));
    var action = new ActionDefinition();
    action.setName("startProcess"); action.setIsEnabled(true); action.setReadOnly(false);
    var binding = new ActionBinding();
    binding.setKind("logicflow"); binding.setFlowName("sales.Order.startProcess");
    binding.setInputMapping(Map.of("initiator", "${ 'actor' }", "formData", "${ input.data }",
        "businessRecordType", "${ 'sales.Order' }", "businessRecordId", "${ input.params.record.id }",
        "callbackFlow", "${ 'sales.Order.callback' }", "idempotencyKey", "${ 'request-key' }"));
    action.setBinding(binding);
    var behavior = new BusinessModelBehavior("sales.Order", List.of(action), List.of(), List.of(),
        List.of(), null, Map.of(), Map.of("startProcess", new BehaviorDefinitionSource(
            "sales.Order", "actions.startProcess", "2.0")), Map.of());
    var behaviors = mock(BusinessModelBehaviorProvider.class);
    when(behaviors.getBusinessModelBehavior("sales.Order")).thenReturn(Optional.of(behavior));
    var runtime = new DefaultBusinessActionRuntime(behaviors,
        new DefaultLogicFlowExecutionFacade(), List::of, manager, ignored -> { });
    runtime.setInvocationValidators(List.of(new WorkflowBusinessActionValidator(
        modelProvider, () -> bindings)));
    var node = FlowNodeMeta.builder().name("发起审批").type("startProcess")
        .extraProp("processId", "approval").inputPort("")
        .outputPort(FlowNodePortMeta.builder().name("").build()).build();
    var flow = FlowModelFactory.build(
        FlowModelMeta.builder().name("sales.Order.startProcess")
            .entryNode(node).exitNode(node.getName()).build(), NodeReferenceCatalog.empty());
    assertTrue(flow.isValid(), flow.getErrors().toString());
    var identity = mock(SecurityIdentity.class, CALLS_REAL_METHODS);
    when(identity.getPrincipal()).thenReturn("actor");
    var role = mock(Role.class);
    var grant = mock(OuroborosGrantedAuthority.class);
    when(grant.getAuthority()).thenReturn("sales.Order.startProcess");
    when(grant.getAccessControlParams()).thenReturn(ResolvedAccessControlParams.all());
    doReturn(List.of(grant)).when(role).getAuthorities(); when(identity.getRoles()).thenReturn(List.of(role));
    var authentication = mock(OuroborosAuthentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);
    doReturn(List.of(grant)).when(authentication).getAuthorities();
    var security = new CurrentSecurityContext() {
      public Optional<SecurityIdentity> findIdentity() { return Optional.of(identity); }
      public Optional<OuroborosAuthentication> findAuthentication() { return Optional.of(authentication); }
    };
    var bridge = new SpringContextUtil();
    try (var context = new GenericApplicationContext();
         var flows = mockStatic(FlowModelCenter.class)) {
      context.registerBean(FlowInstanceService.class, () -> routed);
      context.refresh(); bridge.setApplicationContext(context);
      flows.when(() -> FlowModelCenter.getFlowModel("sales.Order.startProcess"))
          .thenReturn(Optional.of(flow));
      Object execution = CurrentSecurityContextHolder.withContext(security,
          () -> runtime.invoke("sales.Order", "startProcess", BusinessActionInput.of(
              Map.of("record", Map.of("id", "42")), Map.of("amount", 1200))));
      if ("INTERNAL".equals(mode)) {
        assertEquals("built-in-execution", execution); verifyNoInteractions(starter);
      } else {
        var fact = onlyFact();
        assertEquals(fact.getTrackingId(), execution);
        assertEquals(mode, fact.getIntegrationMode()); assertEquals(ExternalStartFact.STATUS_STARTED, fact.getStatus());
        assertEquals("sales.Order.callback", fact.getCallbackFlow());
        var remoteCommand = new com.fasterxml.jackson.databind.ObjectMapper().readTree(fact.getRequestJson());
        assertTrue(remoteCommand.path("variables").isEmpty(), remoteCommand.toString());
        var event = outbox.fromInstanceJson(outbox.findById(fact.getId()).orElseThrow()).orElseThrow();
        assertEquals("sales.Order", event.getBusinessContext().recordType());
        assertEquals("42", event.getBusinessContext().recordId());
        assertEquals(execution, cases.findById(fact.getApprovalCaseId()).orElseThrow().getActiveExecutionId());
        Object repeated = CurrentSecurityContextHolder.withContext(security,
            () -> runtime.invoke("sales.Order", "startProcess", BusinessActionInput.of(
                Map.of("record", Map.of("id", "42")), Map.of("amount", 1200))));
        assertEquals(execution, repeated);
        verify(starter, times(1)).start(any()); verifyNoInteractions(builtIn);

        var terminal = new FlowInstanceEvent();
        terminal.setFlowKey("approval"); terminal.setInstanceId(execution.toString());
        terminal.setEventFactId("terminal-source"); terminal.setEventType(FlowInstanceEvent.EventType.APPROVED);
        terminal.setOutcome("approved");
        new TransactionTemplate(manager).executeWithoutResult(status -> dispatcher.enqueueEvent(terminal));
        var terminalEvent = outbox.fromInstanceJson(outbox.findById("terminal-source").orElseThrow()).orElseThrow();
        assertEquals("sales.Order.callback", terminalEvent.getCallbackFlow());
        assertEquals(event.getBusinessContext(), terminalEvent.getBusinessContext());
        assertEquals(event.getApprovalCaseId(), terminalEvent.getApprovalCaseId());
        assertEquals(2L, terminalEvent.getStateSequence());
      }
    } finally { bridge.destroy(); }
  }

  @Test
  void failedCaseReservationRollsBackTheIntentAndDoesNotCallRemote() {
    doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("case reservation failed after insert"); })
        .when(rawCases).reserveSubmission("sales.Order", "42", "approval");
    assertTrue(service().start(command()).isFailure());
    assertEquals(0L, models.get("ExternalStartFact").count(Map.of()).get());
    assertEquals(0L, models.get("ApprovalCase").count(Map.of()).get());
    verifyNoInteractions(starter);
  }

  @Test
  void failedSourceOutboxRollsBackBindingCaseAndCursorThenRecoveryUsesTheOriginalReply() {
    var failOnce = new AtomicBoolean(true);
    doAnswer(call -> {
      var result = call.callRealMethod();
      if (failOnce.getAndSet(false)) { throw new IllegalStateException("source write failed"); }
      return result;
    }).when(rawOutbox).enqueue(any(FlowInstanceEvent.class));
    var started = service().start(command()); assertSuccess(started);
    assertEquals(ExternalStartFact.STATUS_START_UNKNOWN, started.get().status());
    var fact = onlyFact(); assertNotNull(fact.getRepliedAt());
    assertEquals(ExternalStartFact.PHASE_REPLIED, fact.getRemoteCallPhase());
    assertEquals("PENDING", cases.findById(fact.getApprovalCaseId()).orElseThrow().getBusinessStatus());
    assertEquals(0L, models.get("ExternalResultBinding").count(Map.of()).get());
    assertEquals(0L, models.get("FlowInstanceFactState").count(Map.of()).get());
    assertTrue(outbox.findById(fact.getId()).isEmpty());
    var recovered = recovery().recover(facts.claimForRecovery(fact.getId(), 300).orElseThrow()); assertSuccess(recovered);
    assertEquals(fact.getTrackingId(), recovered.get());
    var event = outbox.fromInstanceJson(outbox.findById(fact.getId()).orElseThrow()).orElseThrow();
    assertEquals(fact.getRepliedAt(), event.getEventTime()); assertEquals(1L, event.getStateSequence());
    verify(starter, times(1)).start(any());
  }

  @Test
  void knownRemoteAbsenceReleasesTheIntentAndCaseTogether() {
    doReturn(Try.failure(ExternalOperationFailure.definitelyNotApplied("not created", null))).when(starter).start(any());
    assertTrue(service().start(command()).isFailure());
    var fact = onlyFact(); assertEquals(ExternalStartFact.STATUS_FAILED, fact.getStatus());
    assertEquals("DRAFT", cases.findById(fact.getApprovalCaseId()).orElseThrow().getBusinessStatus());
    assertEquals(0L, models.get("ExternalResultBinding").count(Map.of()).get());
    assertEquals(0L, models.get("FlowEventOutbox").count(Map.of()).get());
  }

  @Test
  void unknownRemoteOutcomeKeepsTheCaseAndDoesNotReplayAnUnprovenRequest() {
    doReturn(Try.failure(ExternalOperationFailure.unknown("timeout", null))).when(starter).start(any());
    var started = service().start(command()); assertSuccess(started);
    assertEquals(ExternalStartFact.STATUS_START_UNKNOWN, started.get().status());
    var fact = onlyFact();
    assertEquals("PENDING", cases.findById(fact.getApprovalCaseId()).orElseThrow().getBusinessStatus());
    assertTrue(recovery().recover(facts.claimForRecovery(fact.getId(), 300).orElseThrow()).isFailure());
    assertEquals(ExternalStartFact.STATUS_START_UNKNOWN, onlyFact().getStatus());
    assertEquals(0L, models.get("FlowEventOutbox").count(Map.of()).get());
    verify(starter, times(1)).start(any());
  }

  @Test
  void journalCompletionFailureRollsBackAllLocalEffectsAndKeepsTheReplyForRecovery() {
    doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("completion failed"); })
        .when(rawFacts).markStarted(any(), any(), any());
    var started = service().start(command()); assertSuccess(started);
    assertEquals(ExternalStartFact.STATUS_START_UNKNOWN, started.get().status());
    var fact = onlyFact();
    assertEquals("PENDING", cases.findById(fact.getApprovalCaseId()).orElseThrow().getBusinessStatus());
    assertEquals(0L, models.get("ExternalResultBinding").count(Map.of()).get());
    assertEquals(0L, models.get("FlowEventOutbox").count(Map.of()).get());
    doCallRealMethod().when(rawFacts).markStarted(any(), any(), any());
    assertSuccess(recovery().recover(facts.claimForRecovery(fact.getId(), 300).orElseThrow()));
    assertEquals(fact.getRepliedAt(), onlyFact().getRepliedAt());
    verify(starter, times(1)).start(any());
  }

  @Test
  void bindingCannotRewriteTheJournalTrackingIdentity() {
    doAnswer(call -> {
      var saved = (Try<?>) call.callRealMethod();
      var binding = com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding.class.cast(saved.get());
      binding.setTrackingId("different-identity"); return Try.success(binding);
    }).when(rawResults).create(any());
    var started = service().start(command()); assertSuccess(started);
    assertEquals(ExternalStartFact.STATUS_START_UNKNOWN, started.get().status());
    assertTrue(onlyFact().getLastError().contains("trackingId"));
    assertEquals(0L, models.get("ExternalResultBinding").count(Map.of()).get());
    assertEquals(0L, models.get("FlowEventOutbox").count(Map.of()).get());
  }

  @Test
  void fullProcessStartAndRecoveryShareTheSameCommittedCaseFactAndReply() {
    var registry = mock(WorkflowEngineAdapterRegistry.class);
    when(registry.integrationModeOf("remote")).thenReturn("FULL_PROCESS");
    providers.registerSingleton("builtIn", mock(BuiltInFlowInstanceService.class));
    var routed = new RoutedFlowInstanceService(providers.getBeanProvider(BuiltInFlowInstanceService.class), mappings,
        registry, resolver, instances, results, dispatcher, manager, "internal", () -> "tenant",
        mock(ExternalUserIdentityResolver.class), facts, mock(ExternalActionExecutionService.class),
        providers.getBeanProvider(ApprovalCaseService.class));
    var failOnce = new AtomicBoolean(true);
    doAnswer(call -> {
      var saved = call.callRealMethod();
      if (failOnce.getAndSet(false)) { throw new IllegalStateException("full process outbox failed"); }
      return saved;
    }).when(rawOutbox).enqueue(any(FlowInstanceEvent.class));
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      assertSuccess(routed.start("approval", "actor", Map.of(),
          Map.of("businessRecordType", "sales.Order", "businessRecordId", "42"), Map.of()));
      status.setRollbackOnly();
    });
    var fact = onlyFact(); assertEquals(ExternalStartFact.MODE_FULL_PROCESS, fact.getIntegrationMode());
    assertEquals(ExternalStartFact.STATUS_START_UNKNOWN, fact.getStatus());
    assertEquals(0L, models.get("ExternalInstanceBinding").count(Map.of()).get());
    assertSuccess(recovery().recover(facts.claimForRecovery(fact.getId(), 300).orElseThrow()));
    var event = outbox.fromInstanceJson(outbox.findById(fact.getId()).orElseThrow()).orElseThrow();
    assertEquals(fact.getId(), event.getEventFactId());
    assertEquals(fact.getRepliedAt(), event.getEventTime());
    assertEquals(fact.getApprovalCaseId(), event.getApprovalCaseId());
    assertEquals(fact.getTrackingId(), instances.findByLocalId(fact.getTrackingId()).orElseThrow().getLocalInstanceId());
    assertTrue(delivered.isEmpty()); verify(starter, times(1)).start(any());
  }

  @Test
  void changedPinnedMappingKeepsTheOriginalResponsibilityUnknown() {
    doThrow(new IllegalStateException("outbox failed")).when(rawOutbox).enqueue(any(FlowInstanceEvent.class));
    assertSuccess(service().start(command()));
    var fact = onlyFact(); mapping.setVersion(2);
    assertTrue(recovery().recover(facts.claimForRecovery(fact.getId(), 300).orElseThrow()).isFailure());
    assertEquals(ExternalStartFact.STATUS_START_UNKNOWN, onlyFact().getStatus());
    assertEquals(ExternalStartFact.PHASE_REPLIED, onlyFact().getRemoteCallPhase());
    assertEquals("PENDING", cases.findById(fact.getApprovalCaseId()).orElseThrow().getBusinessStatus());
    verify(starter, times(1)).start(any());
  }

  @Test
  void theFirstReplyTimeIsStableAndCannotBeRolledBackAsRemoteAbsence() {
    var fact = facts.reserve("tenant", mapping, "sales.Order", "42", "key", "fingerprint",
        ExternalStartFact.MODE_RESULT_ONLY, "tracking", "actor", null, "{}").get().fact();
    assertTrue(facts.markInFlight(fact.getId(), fact.getClaimToken()));
    assertTrue(facts.markReplied(fact.getId(), fact.getClaimToken(), "remote-instance", null));
    var replied = onlyFact();
    assertFalse(facts.markReplied(fact.getId(), fact.getClaimToken(), "remote-instance", null));
    assertEquals(replied.getRepliedAt(), onlyFact().getRepliedAt());
    assertThrows(com.ouroboros.exception.BusinessException.class,
        () -> facts.markFailed(fact.getId(), fact.getClaimToken(), "incorrect absence"));
    assertEquals("PENDING", cases.findById(fact.getApprovalCaseId()).orElseThrow().getBusinessStatus());
  }

  @Test
  void failedCaseReleaseCannotCommitOnlyTheFailedJournal() {
    doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("case release failed"); })
        .when(rawCases).markStartFailed(any(), anyInt(), any());
    doReturn(Try.failure(ExternalOperationFailure.definitelyNotApplied("not created", null))).when(starter).start(any());
    assertThrows(IllegalStateException.class, () -> service().start(command()));
    var fact = onlyFact(); assertEquals(ExternalStartFact.STATUS_STARTING, fact.getStatus());
    assertEquals("PENDING", cases.findById(fact.getApprovalCaseId()).orElseThrow().getBusinessStatus());
    assertEquals(0L, models.get("FlowEventOutbox").count(Map.of()).get());
  }

  @Test
  void startIntentAndCaseCannotBeReservedAcrossCommitResources() {
    var foreign = new DriverManagerDataSource("jdbc:h2:mem:foreign_case_" + UUID.randomUUID()
        + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    var metadata = new ArrayList<DataModelMeta>();
    add(metadata, "com.ouroboros.workflow.approval.lifecycle", "ApprovalCase");
    var otherStation = new SqlDataStation("foreign-case", foreign, metadata, MigrationStrategy.AUTO, "H2");
    var otherJdbc = new JdbcTemplate(foreign);
    otherStation.generateMigrationSql(otherStation.getDataModelList()).get().forEach(otherJdbc::execute);
    var original = models.put("ApprovalCase", otherStation.getDataModelList().getFirst());
    try {
      new TransactionTemplate(new DataSourceTransactionManager(foreign)).executeWithoutResult(status -> {
        var result = service().start(command()); assertTrue(result.isFailure());
        assertTrue(result.getCause().getMessage().contains("同一提交资源"), result.getCause().toString());
      });
      assertEquals(0L, models.get("ExternalStartFact").count(Map.of()).get());
      assertEquals(0L, models.get("ApprovalCase").count(Map.of()).get());
      verifyNoInteractions(starter);
    } finally { models.put("ApprovalCase", original); }
  }

  @Test
  void explicitCaseMustBeCommittedPendingAndUnboundBeforeItCanBeAttached() {
    var pending = cases.reserveSubmission("sales.Order", "42", "approval");
    var invalid = facts.reserve("tenant", mapping, "sales.Order", "42", "key", "fingerprint",
        ExternalStartFact.MODE_FULL_PROCESS, "tracking", "actor", null, "{}", pending.approvalCaseId(), 2);
    assertTrue(invalid.isFailure());
    assertEquals(0L, models.get("ExternalStartFact").count(Map.of()).get());
    var reserved = facts.reserve("tenant", mapping, "sales.Order", "42", "key", "fingerprint",
        ExternalStartFact.MODE_FULL_PROCESS, "tracking", "actor", null, "{}", pending.approvalCaseId(), 1);
    assertSuccess(reserved);
    assertEquals(pending.approvalCaseId(), reserved.get().fact().getApprovalCaseId());
    assertEquals(1, cases.findById(pending.approvalCaseId()).orElseThrow().getSubmissionRound());
    verifyNoInteractions(starter);
  }

  @Test
  void completionCannotCommitABindingOnAnotherResource() {
    var foreign = new DriverManagerDataSource("jdbc:h2:mem:foreign_binding_" + UUID.randomUUID()
        + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    var metadata = new ArrayList<DataModelMeta>();
    add(metadata, "com.ouroboros.workflow.approval.external.runtime.binding", "ExternalResultBinding");
    var otherStation = new SqlDataStation("foreign-binding", foreign, metadata, MigrationStrategy.AUTO, "H2");
    var otherJdbc = new JdbcTemplate(foreign);
    otherStation.generateMigrationSql(otherStation.getDataModelList()).get().forEach(otherJdbc::execute);
    var original = models.put("ExternalResultBinding", otherStation.getDataModelList().getFirst());
    try {
      doAnswer(call -> new TransactionTemplate(new DataSourceTransactionManager(foreign))
          .execute(status -> Try.of(call::callRealMethod).get())).when(rawResults).create(any());
      var result = service().start(command()); assertSuccess(result);
      assertEquals(ExternalStartFact.STATUS_START_UNKNOWN, result.get().status());
      assertEquals(0L, models.get("ExternalResultBinding").count(Map.of()).get());
      var fact = onlyFact();
      assertTrue(fact.getLastError().contains("同一提交资源"), fact.getLastError());
      assertEquals("PENDING", cases.findById(fact.getApprovalCaseId()).orElseThrow().getBusinessStatus());
      assertEquals(0L, models.get("FlowEventOutbox").count(Map.of()).get());
    } finally { models.put("ExternalResultBinding", original); }
  }

  private ExternalStartRecoveryService recovery() {
    return transactional(new ExternalStartRecoveryService(facts, mappings, resolver, instances, results, dispatcher,
        manager, providers.getBeanProvider(ApprovalCaseService.class)), ExternalStartRecoveryService.class);
  }

  private ResultOnlyStartExecutor executor() {
    return transactional(new ResultOnlyStartExecutor(results, facts, resolver, dispatcher,
        manager, providers.getBeanProvider(ApprovalCaseService.class)), ResultOnlyStartExecutor.class);
  }

  private ExternalApprovalStartServiceImpl service() {
    var registry = mock(WorkflowEngineAdapterRegistry.class);
    when(registry.integrationModeOf("remote")).thenReturn("RESULT_ONLY");
    if (!providers.containsSingleton("builtIn")) {
      providers.registerSingleton("builtIn", mock(BuiltInFlowInstanceService.class));
    }
    return new ExternalApprovalStartServiceImpl(new RoutedFlowInstanceService(
        providers.getBeanProvider(BuiltInFlowInstanceService.class), mappings, registry, resolver, instances, results,
        dispatcher, manager, "reject", () -> "tenant", mock(ExternalUserIdentityResolver.class), facts,
        mock(ExternalActionExecutionService.class), providers.getBeanProvider(ApprovalCaseService.class), executor()));
  }

  private ResultOnlyStartCommand command() {
    return new ResultOnlyStartCommand("approval", "sales.Order", "42", "actor", Map.of(), "request-key", null, null, null);
  }

  private ExternalStartFact onlyFact() {
    try { return (ExternalStartFact) facts.searchList(Map.of()).getFirst(); }
    catch (com.ouroboros.exception.OuroborosException ex) { throw new AssertionError(ex); }
  }

  private void add(List<DataModelMeta> metadata, String packageName, String name) {
    metadata.add(TypedModelMetaHelper.scanModelMate(packageName).get(name).deepCopy());
  }

  private <T> T transactional(T target, Class<T> type) {
    var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
    factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
    return type.cast(factory.getProxy());
  }

  private void assertSuccess(Try<?> result) { assertTrue(result.isSuccess(), () -> String.valueOf(result.getCause())); }
}
