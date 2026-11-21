package com.ouroboros.workflow.approval.external.runtime;

import com.ouroboros.workflow.approval.FlowStartCommand;
import com.ouroboros.workflow.approval.FlowStartResult;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalApprovalStartServiceImpl;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartExecutor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.BuiltInFlowInstanceService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalActionCommand;
import com.ouroboros.workflow.approval.external.ExternalActionResult;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.ExternalRecordPage;
import com.ouroboros.workflow.approval.external.ExternalRecordReader;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionExecutionService;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionRequest;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalUserIdentityResolver;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowInstanceService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFact;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

import io.vavr.control.Try;

/**
 * M5/M10 路由门面测试：同一发起入口按映射选择三种执行模式，外部模式不回退到内置。
 */
class RoutedFlowInstanceServiceTest {

  private final Map<String, ExternalStartFact> journal = new java.util.LinkedHashMap<>();
  private BuiltInFlowInstanceService internal;
  private WorkflowEngineMappingService mappingService;
  private WorkflowEngineAdapterRegistry registry;
  private ConnectionAwareAdapterResolver adapterResolver;
  private ExternalInstanceBindingService instanceBindingService;
  private ExternalResultBindingService resultBindingService;
  private ExternalUserIdentityResolver identityResolver;
  private InstanceEventDispatcher eventDispatcher;
  private ExternalStartFactService startFactService;
  private ExternalActionExecutionService actionExecutionService;
  private com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver scopeResolver;
  private RoutedFlowInstanceService routed;

  @BeforeEach
  void setUp() {
    internal = mock(BuiltInFlowInstanceService.class);
    mappingService = mock(WorkflowEngineMappingService.class);
    when(mappingService.findByIdForSystem(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Optional.of(mapping()));
    registry = mock(WorkflowEngineAdapterRegistry.class);
    // 接入模式由注册表派生；mock 注册表必须给出派生值，否则一律回落到 RESULT_ONLY。
    when(registry.integrationModeOf(org.mockito.ArgumentMatchers.anyString())).thenReturn("FULL_PROCESS");
    adapterResolver = ConnectionAwareAdapterResolverTestSupport.resolver(registry);
    instanceBindingService = mock(ExternalInstanceBindingService.class);
    routed = newRouted(RoutedFlowInstanceService.UNMAPPED_POLICY_INTERNAL);
  }

  /** 类型安全的 ObjectProvider 子接口，避免原始类型 mock。 */
  interface FlowInstanceObjectProvider extends ObjectProvider<BuiltInFlowInstanceService> {
  }

  private RoutedFlowInstanceService newRouted(String unmappedPolicy) {
    return newRouted(unmappedPolicy, null);
  }

  private RoutedFlowInstanceService newRouted(
      String unmappedPolicy, ApprovalCaseService approvalCaseService) {
    FlowInstanceObjectProvider provider = mock(FlowInstanceObjectProvider.class);
    when(provider.stream()).thenReturn(Stream.of(internal));
    eventDispatcher = mock(InstanceEventDispatcher.class);
    resultBindingService = mock(ExternalResultBindingService.class);
    identityResolver = mock(ExternalUserIdentityResolver.class);
    scopeResolver = mock(com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("default");
    startFactService = mock(ExternalStartFactService.class);
    actionExecutionService = mock(ExternalActionExecutionService.class);
    when(startFactService.reserve(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
          ExternalStartFact fact = new ExternalStartFact();
          fact.setId("start-fact-1");
          fact.setTrackingId(inv.getArgument(7));
          fact.setClaimToken("claim-1");
          fact.setStatus(ExternalStartFact.STATUS_STARTING);
          fact.setScope(inv.getArgument(0)); fact.setFlowKey("flow-a"); fact.setIntegrationMode(inv.getArgument(6));
          fact.setBusinessRecordType(inv.getArgument(2)); fact.setBusinessRecordId(inv.getArgument(3));
          fact.setActorUserId(inv.getArgument(8)); fact.setCallbackFlow(inv.getArgument(9));
          journal.put(fact.getId(), fact);
          return Try.success(new ExternalStartFactService.Reservation(fact, true));
        });
    when(startFactService.findById(any())).thenAnswer(inv -> Optional.ofNullable(journal.get(inv.getArgument(0))));
    when(startFactService.markInFlight(any(), any())).thenReturn(true);
    when(startFactService.markReplied(any(), any(), any(), any())).thenReturn(true);
    when(startFactService.markStarted(any(), any(), any())).thenReturn(true);
    ObjectProvider<ApprovalCaseService> caseProvider = approvalCaseService == null
        ? null : providerOf(approvalCaseService);
    var resultStarts = new ResultOnlyStartExecutor(
        resultBindingService, startFactService, adapterResolver, eventDispatcher, null,
        caseProvider);
    return new RoutedFlowInstanceService(
        provider, mappingService, registry, adapterResolver, instanceBindingService,
        resultBindingService, eventDispatcher,
        null, unmappedPolicy, scopeResolver, identityResolver, startFactService, actionExecutionService,
        caseProvider, resultStarts);
  }

  private <T> ObjectProvider<T> providerOf(T value) {
    return new ObjectProvider<T>() {
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
  void primaryUnifiedEntryAndResultExecutorWireWithoutCircularDependencies() {
    try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      var beans = context.getBeanFactory();
      beans.registerSingleton("builtIn", internal);
      beans.registerSingleton("mappings", mappingService);
      beans.registerSingleton("registry", registry);
      beans.registerSingleton("resolver", adapterResolver);
      beans.registerSingleton("instances", instanceBindingService);
      beans.registerSingleton("results", resultBindingService);
      beans.registerSingleton("dispatcher", eventDispatcher);
      beans.registerSingleton("transactions", mock(org.springframework.transaction.PlatformTransactionManager.class));
      beans.registerSingleton("scope", scopeResolver);
      beans.registerSingleton("identity", identityResolver);
      beans.registerSingleton("facts", startFactService);
      beans.registerSingleton("actions", actionExecutionService);
      beans.registerSingleton("definitions", mock(com.ouroboros.workflow.approval.FlowDefinitionService.class));
      beans.registerSingleton("forms", mock(com.ouroboros.workflow.approval.FlowFormService.class));
      context.register(RoutedFlowInstanceService.class, ResultOnlyStartExecutor.class, ExternalApprovalStartServiceImpl.class);
      context.refresh();
      assertThat(context.getBean(FlowInstanceService.class)).isInstanceOf(RoutedFlowInstanceService.class);
      assertThat(context.getBean(ExternalApprovalStartServiceImpl.class)).isNotNull();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"FULL_PROCESS", "RESULT_ONLY"})
  void externalInitialFormUsesPlatformFormBindingWithoutLocalDeployment(String mode) throws Exception {
    when(registry.integrationModeOf("fake-adapter")).thenReturn(mode);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    var models = mock(com.ouroboros.workflow.approval.manage.service.WorkflowModelService.class);
    var model = new com.ouroboros.workflow.approval.manage.entity.WorkflowModel();
    model.setJson("""
        {"name":"flow-a","title":"订单审批","form":{"formType":"default","formId":"order-form"},
         "nodes":[{"id":"start","name":"发起","type":"StartNode","permission":[{"name":"amount","visible":true,"writable":true}]},
                  {"id":"end","name":"结束","type":"EndNode"}],
         "edges":[{"id":"edge","type":"SEQUENCE","sourceId":"start","targetId":"end"}]}
        """);
    when(models.getByPrimaryKey("flow-a")).thenReturn(model);
    var forms = mock(com.ouroboros.workflow.approval.FlowFormService.class);
    when(forms.getType()).thenReturn("default");
    var schema = new com.ouroboros.uischema.schema.SchemaNode();
    when(forms.getInitialFormSchema(org.mockito.ArgumentMatchers.eq("order-form"), any())).thenReturn(Try.success(schema));
    var definitions = new com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowDefinitionService(
        providerOf(mock(com.ouroboros.workflow.approval.BuiltInFlowDefinitionService.class)), mappingService, scopeResolver,
        providerOf(mock(com.ouroboros.workflow.approval.definition.startrequirements.BuiltInStartRequirementsResolver.class)),
        new com.ouroboros.workflow.approval.external.runtime.startrequirements.ExternalStartRequirementsParser());
    org.springframework.test.util.ReflectionTestUtils.setField(definitions, "workflowModels", models);
    org.springframework.test.util.ReflectionTestUtils.setField(routed, "definitions", definitions);
    org.springframework.test.util.ReflectionTestUtils.setField(routed, "formServices", List.of(forms));

    var result = routed.getInitialForm("flow-a");

    assertThat(result.isSuccess()).as(result::toString).isTrue();
    assertThat(result.get()).isSameAs(schema);
    verify(forms).getInitialFormSchema(org.mockito.ArgumentMatchers.eq("order-form"),
        org.mockito.ArgumentMatchers.argThat(permissions -> permissions.size() == 1
            && "amount".equals(permissions.getFirst().getName()) && permissions.getFirst().isWritable()));
    verify(internal, never()).getInitialForm(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"FULL_PROCESS", "RESULT_ONLY"})
  void externalMappingKeepsPlatformContextLocallyAndForwardsOnlyCustomVariables(String mode) {
    when(registry.integrationModeOf(org.mockito.ArgumentMatchers.anyString())).thenReturn(mode);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));

    FakeResultOnlyEngineAdapter starter = new FakeResultOnlyEngineAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", starter, Set.of(Capability.EXTERNAL_START))));
    when(resultBindingService.create(any())).thenAnswer(call -> Try.success(call.getArgument(0)));
    when(instanceBindingService.create(any())).thenAnswer(call -> Try.success(call.getArgument(0)));
    Try<String> result = routed.start("flow-a", "user-1", Map.of("approve", List.of("user-2")),
        Map.of("businessRecordType", "sales.Order", "businessRecordId", "42", "callbackFlow", "sales.Order.callback",
            "idempotencyKey", "request-key", "region", "east"),
        Map.of("amount", 1200), CompletableFuture.completedFuture(null));

    assertThat(result.isSuccess()).as("同一入口必须支持结果型映射").isTrue();
    assertThat(result.get()).isEqualTo(journal.get("start-fact-1").getTrackingId());
    assertThat(starter.startedCommands()).hasSize(1);
    assertThat(starter.startedCommands().getFirst().targetSelectActioners()).containsEntry("approve", List.of("user-2"));
    assertThat(starter.startedCommands().getFirst().variables()).containsExactlyEntriesOf(Map.of("region", "east"));
    assertThat(starter.startedCommands().getFirst().businessRecord())
        .isEqualTo(new ExternalStartCommand.ExternalBusinessRecord("sales.Order", "42"));
    assertThat(journal.get("start-fact-1").getCallbackFlow()).isEqualTo("sales.Order.callback");
    verify(eventDispatcher).enqueueEvent(any());
    verify(internal, never()).start(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(CompletableFuture.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"FULL_PROCESS", "RESULT_ONLY"})
  void typedStartKeepsUnknownStatusAndNeverPublishesStartedBeforeConfirmation(String mode) {
    when(registry.integrationModeOf("fake-adapter")).thenReturn(mode);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));
    var starter = new FakeResultOnlyEngineAdapter();
    starter.setStartException(new IllegalStateException("remote response lost"));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered("fake-adapter", starter, Set.of(Capability.EXTERNAL_START))));
    when(startFactService.markUnknown(any(), any(), any(), any(), any())).thenAnswer(call -> {
      journal.get(call.getArgument(0)).setStatus(ExternalStartFact.STATUS_START_UNKNOWN);
      return true;
    });
    var started = routed.start(new FlowStartCommand("flow-a", "user-1", Map.of(),
        Map.of("businessRecordType", "sales.Order", "businessRecordId", "42"), Map.of(), null, "request-1"));
    assertThat(started.isSuccess()).isTrue();
    assertThat(started.get().status()).isEqualTo(FlowStartResult.Status.START_UNKNOWN);
    assertThat(started.get().executionId()).isNotBlank();
    assertThat(started.get().startFactId()).isEqualTo("start-fact-1");
    verify(eventDispatcher, never()).enqueueEvent(any());
    verify(internal, never()).start(any(), any(), any(), any(), any(), any(CompletableFuture.class));
  }

  @Test
  void noMappingDelegatesToInternal() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.empty());
    when(internal.start(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(CompletableFuture.class))).thenReturn(Try.success("inst-1"));

    Try<String> result = routed.start("flow-a", "user-1", null, null, null, CompletableFuture.runAsync(() -> {
    }));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo("inst-1");
  }

  @Test
  void unconfiguredScopeWithInternalPolicyDelegatesWithoutMappingLookup() {
    when(scopeResolver.resolve()).thenThrow(
        new com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException("未装配"));
    when(internal.start(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(CompletableFuture.class))).thenReturn(Try.success("inst-1"));

    Try<String> result = routed.start(
        "flow-a", "user-1", null, null, null, CompletableFuture.completedFuture(null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo("inst-1");
    verify(mappingService, never()).findActive(any(), any());
  }

  @Test
  void unconfiguredScopeWithRejectPolicyFailsWithoutInternalDelegation() {
    routed = newRouted(RoutedFlowInstanceService.UNMAPPED_POLICY_REJECT);
    when(scopeResolver.resolve()).thenThrow(
        new com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException("未装配"));

    Try<String> result = routed.start(
        "flow-a", "user-1", null, null, null, CompletableFuture.completedFuture(null));

    assertThat(result.isFailure()).isTrue();
    verify(mappingService, never()).findActive(any(), any());
    verify(internal, never()).start(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(CompletableFuture.class));
  }

  @Test
  void mappingLookupFailureDoesNotDelegateToInternal() {
    when(mappingService.findActive("default", "flow-a")).thenThrow(new BusinessException(
        "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED + "] 查询 ACTIVE 映射失败"));

    Try<String> result = routed.start("flow-a", "user-1", null, null, null, CompletableFuture.runAsync(() -> {
    }));

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED);
    verify(internal, never()).start(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(CompletableFuture.class));
  }

  @Test
  void rejectPolicyBlocksUnmappedStart() {
    routed = newRouted(RoutedFlowInstanceService.UNMAPPED_POLICY_REJECT);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.empty());

    Try<String> result = routed.start("flow-a", "user-1", null, null, null, CompletableFuture.runAsync(() -> {
    }));

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.WORKFLOW_ROUTE_NOT_CONFIGURED);
    verify(internal, never()).start(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(CompletableFuture.class));
  }

  @Test
  void fullProcessMappingStartsExternalInstanceViaAdapter() {
    WorkflowEngineMapping mapping = mapping();
    mapping.setAdapterId("fake-adapter");
    mapping.setConnectionId("conn-1");
    mapping.setExternalDefinitionNamespace("ns-1");
    mapping.setExternalDefinitionId("def-1");
    mapping.setExternalDefinitionVersion("v1");
    mapping.setExternalDefinitionSchemaHash("hash-1");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping));
    FakeStarterAdapter starter = new FakeStarterAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", starter, Set.of(Capability.EXTERNAL_START))));
    when(instanceBindingService.create(any())).thenAnswer(inv -> {
      ExternalInstanceBinding created = inv.getArgument(0);
      return Try.success(created);
    });

    Try<String> result = routed.start("flow-a", "user-1", null, Map.of("businessRecordType", "supplier",
        "businessRecordId", "sup-1"), null, CompletableFuture.runAsync(() -> {
    }));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo(journal.get("start-fact-1").getTrackingId());
    assertThat(starter.commands).hasSize(1);
    ExternalStartCommand command = starter.commands.get(0);
    assertThat(command.originatorUserId()).isEqualTo("user-1");
    assertThat(command.externalDefinitionRef().namespace()).isEqualTo("ns-1");
    assertThat(command.externalDefinitionRef().definitionId()).isEqualTo("def-1");
    org.mockito.ArgumentCaptor<ExternalInstanceBinding> captor =
        org.mockito.ArgumentCaptor.forClass(ExternalInstanceBinding.class);
    verify(instanceBindingService).create(captor.capture());
    ExternalInstanceBinding binding = captor.getValue();
    assertThat(binding.getLocalInstanceId()).isEqualTo(result.get());
    assertThat(binding.getScope()).isEqualTo("default");
    assertThat(binding.getExternalInstanceId()).isEqualTo("ext-1");
    assertThat(binding.getStatus()).isEqualTo(ExternalInstanceBinding.STATUS_RUNNING);
    assertThat(binding.getBusinessRecordId()).isEqualTo("sup-1");
    verify(internal, never()).start(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(CompletableFuture.class));
  }

  @Test
  void fullProcessStartBindsApprovalCaseExecution() {
    WorkflowEngineMapping mapping = mapping();
    mapping.setAdapterId("fake-adapter");
    mapping.setConnectionId("conn-1");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping));
    FakeStarterAdapter starter = new FakeStarterAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", starter, Set.of(Capability.EXTERNAL_START))));
    when(instanceBindingService.create(any())).thenAnswer(inv -> Try.success(inv.getArgument(0)));
    ApprovalCaseService caseService = mock(ApprovalCaseService.class);
    routed = newRouted(RoutedFlowInstanceService.UNMAPPED_POLICY_INTERNAL, caseService);
    when(startFactService.reserve(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> {
          ExternalStartFact fact = new ExternalStartFact();
          fact.setId("start-fact-case");
          fact.setTrackingId(invocation.getArgument(7));
          fact.setApprovalCaseId(invocation.getArgument(11));
          fact.setSubmissionRound(invocation.getArgument(12));
          fact.setClaimToken("claim-case");
          fact.setStatus(ExternalStartFact.STATUS_STARTING);
          journal.put(fact.getId(), fact);
          return Try.success(new ExternalStartFactService.Reservation(fact, true));
        });

    Try<String> result = routed.start("flow-a", "user-1", null,
        Map.of("businessRecordType", "supplier", "businessRecordId", "sup-1",
            "approvalCaseId", "case-1", "submissionRound", 2),
        null, CompletableFuture.completedFuture(null));

    assertThat(result.isSuccess()).isTrue();
    verify(caseService).bindExecution("case-1", 2, result.get());
  }

  @Test
  void fullProcessAdapterThrownExceptionIsClassifiedAsUnknown() {
    WorkflowEngineMapping mapping = mapping();
    mapping.setAdapterId("fake-adapter");
    mapping.setConnectionId("conn-1");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping));
    FakeStarterAdapter starter = new FakeStarterAdapter();
    starter.throwOnStart = true;
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", starter, Set.of(Capability.EXTERNAL_START))));

    Try<String> result = routed.start("flow-a", "user-1", null,
        Map.of("businessRecordType", "supplier", "businessRecordId", "sup-1"),
        null, CompletableFuture.completedFuture(null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo(journal.get("start-fact-1").getTrackingId());
    verify(startFactService).markUnknown(
        any(), any(), any(), any(), org.mockito.ArgumentMatchers.contains("远程副作用未知"));
    verify(instanceBindingService, never()).create(any());
  }

  @Test
  void fullProcessLateRemoteResponseIsAdoptedBeforeBindingIsPersisted() {
    WorkflowEngineMapping mapping = mapping();
    mapping.setAdapterId("fake-adapter");
    mapping.setConnectionId("conn-1");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping));
    FakeStarterAdapter starter = new FakeStarterAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", starter, Set.of(Capability.EXTERNAL_START))));
    ExternalStartFact adopted = new ExternalStartFact();
    adopted.setId("start-fact-1");
    adopted.setTrackingId("late-local-instance");
    adopted.setClaimToken("late-claim");
    adopted.setStatus(ExternalStartFact.STATUS_STARTING);
    when(startFactService.markReplied(any(), any(), any(), any())).thenReturn(false);
    when(startFactService.adoptLateResponse(any(), any(), any(), any(), anyInt()))
        .thenReturn(Optional.of(new ExternalStartFactService.RecoveryClaim(adopted, "late-claim")));
    when(instanceBindingService.create(any())).thenAnswer(inv -> Try.success(inv.getArgument(0)));

    Try<String> result = routed.start("flow-a", "user-1", null,
        Map.of("businessRecordType", "supplier", "businessRecordId", "sup-1"),
        null, CompletableFuture.completedFuture(null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo("late-local-instance");
    org.mockito.ArgumentCaptor<ExternalInstanceBinding> binding =
        org.mockito.ArgumentCaptor.forClass(ExternalInstanceBinding.class);
    verify(instanceBindingService).create(binding.capture());
    assertThat(binding.getValue().getLocalInstanceId()).isEqualTo("late-local-instance");
    verify(startFactService).markStarted("start-fact-1", "late-claim", "late-local-instance");
  }

  @Test
  void distinctFullProcessReservationsUseDistinctRemoteIdempotencyKeys() {
    WorkflowEngineMapping mapping = mapping();
    mapping.setExternalDefinitionId("def-1");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping));
    FakeStarterAdapter starter = new FakeStarterAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", starter, Set.of(Capability.EXTERNAL_START))));
    when(instanceBindingService.create(any())).thenAnswer(inv -> Try.success(inv.getArgument(0)));

    Try<String> first = routed.start("flow-a", "user-1", null,
        Map.of("businessRecordType", "supplier", "businessRecordId", "sup-1"),
        null, CompletableFuture.completedFuture(null));
    Try<String> second = routed.start("flow-a", "user-1", null,
        Map.of("businessRecordType", "supplier", "businessRecordId", "sup-1"),
        null, CompletableFuture.completedFuture(null));

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    assertThat(starter.commands).hasSize(2);
    assertThat(starter.commands.get(0).idempotencyKey())
        .isNotEqualTo(starter.commands.get(1).idempotencyKey());
  }

  @Test
  void startConsultsResolvedScopeForMappingLookup() {
    com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver scopeResolver =
        mock(com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("scope-test");
    FlowInstanceObjectProvider provider = mock(FlowInstanceObjectProvider.class);
    when(provider.stream()).thenReturn(Stream.of(internal));
    eventDispatcher = mock(InstanceEventDispatcher.class);
    RoutedFlowInstanceService scoped = new RoutedFlowInstanceService(
        provider, mappingService, registry, adapterResolver, instanceBindingService,
        mock(ExternalResultBindingService.class), eventDispatcher, null,
        RoutedFlowInstanceService.UNMAPPED_POLICY_INTERNAL, scopeResolver, null,
        mock(ExternalStartFactService.class), mock(ExternalActionExecutionService.class));
    when(mappingService.findActive("scope-test", "flow-a")).thenReturn(Optional.of(mapping()));

    Try<String> result = scoped.start("flow-a", "user-1", null, null, null, CompletableFuture.runAsync(() -> {
    }));

    assertThat(result.isFailure()).isTrue();
    verify(mappingService).findActive("scope-test", "flow-a");
  }

  @Test
  void otherMethodsDelegateToInternal() {
    when(mappingService.findActive("default", "any")).thenReturn(Optional.empty());
    when(internal.cancel("inst-1", "user-1", "comment")).thenReturn(Try.success(null));

    assertThat(routed.cancel("inst-1", "user-1", "comment").isSuccess()).isTrue();
    verify(internal).cancel("inst-1", "user-1", "comment");
  }

  @Test
  void withdrawOnFullProcessInstanceRoutesToReliableActionCoordinator() {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("inst-1");
    binding.setFlowKey("flow-a");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setExternalInstanceId("ext-1");
    binding.setBusinessRecordType("supplier");
    binding.setBusinessRecordId("sup-1");
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    when(instanceBindingService.findByLocalId("inst-1")).thenReturn(Optional.of(binding));
    when(registry.capabilitiesOf("fake-adapter")).thenReturn(Set.of(Capability.EXTERNAL_INSTANCE_ACTION));
    when(actionExecutionService.executeInstance(any(), any(), any(), any(), any())).thenReturn(Try.success(null));

    Try<Void> result = routed.withdraw("inst-1", "user-1", "撤回");

    assertThat(result.isSuccess()).isTrue();
    org.mockito.ArgumentCaptor<ExternalActionRequest> requestCaptor =
        org.mockito.ArgumentCaptor.forClass(ExternalActionRequest.class);
    verify(actionExecutionService).executeInstance(
        org.mockito.ArgumentMatchers.eq(binding), requestCaptor.capture(),
        org.mockito.ArgumentMatchers.eq(ExternalInstanceBinding.STATUS_FINISHED),
        org.mockito.ArgumentMatchers.eq("withdrawn"),
        org.mockito.ArgumentMatchers.eq(FlowInstanceEvent.EventType.CANCELLED));
    assertThat(requestCaptor.getValue().canonicalAction()).isEqualTo("cancel");
    assertThat(requestCaptor.getValue().externalAction()).isEqualTo("cancel");
  }

  @Test
  void ouroborosAuthorityBindingAlwaysUsesBuiltInInstanceService() {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("inst-1");
    binding.setFlowKey("flow-a");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setExternalInstanceId("ext-1");
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    when(instanceBindingService.findByLocalId("inst-1")).thenReturn(Optional.of(binding));
    WorkflowEngineMapping mapping = mapping();
    mapping.setAuthority("OUROBOROS");
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(internal.cancel("inst-1", "user-1", "取消")).thenReturn(Try.success(null));

    Try<Void> result = routed.cancel("inst-1", "user-1", "取消");

    assertThat(result.isSuccess()).isTrue();
    verify(internal).cancel("inst-1", "user-1", "取消");
    verify(actionExecutionService, never()).executeInstance(any(), any(), any(), any(), any());
    verify(registry, never()).capabilitiesOf(any());
  }

  @Test
  void getInstancesMergesExternalBindingRecords() {
    FlowInstanceDetail internalDetail = new FlowInstanceDetail();
    internalDetail.setInstanceId("in-1");
    internalDetail.setFlowKey("flow-a");
    internalDetail.setStatus("finished");
    when(internal.getInstances("flow-a", "finished", 0, 10)).thenReturn(List.of(internalDetail));
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("ext-local-1");
    binding.setFlowKey("flow-a");
    binding.setScope("default");
    binding.setStatus(ExternalInstanceBinding.STATUS_FINISHED);
    binding.setOutcome("approved");
    binding.setCreatedAt(java.time.LocalDateTime.now());
    when(mappingService.findByScopeAndFlowKey("default", "flow-a")).thenReturn(List.of(mapping()));
    when(instanceBindingService.searchByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_FINISHED, 0, 10))
        .thenReturn(List.of(binding));

    List<FlowInstanceDetail> instances = routed.getInstances("flow-a", "finished", 0, 10);

    assertThat(instances).hasSize(2);
    FlowInstanceDetail external = instances.stream()
        .filter(detail -> "ext-local-1".equals(detail.getInstanceId()))
        .findFirst().orElseThrow();
    assertThat(external.getStatus()).isEqualTo("finished");
    assertThat(external.getOutcome()).isEqualTo("approved");
  }

  @Test
  void getInstancesAppliesOffsetAfterMergingInternalAndExternalSources() {
    FlowInstanceDetail newestInternal = instanceDetail("internal-new", "flow-a", "finished", "2026-08-26T10:00:00");
    FlowInstanceDetail olderInternal = instanceDetail("internal-old", "flow-a", "finished", "2026-08-26T09:00:00");
    when(internal.getInstances("flow-a", "finished", 0, 3)).thenReturn(List.of(newestInternal, olderInternal));
    ExternalInstanceBinding newestExternal = binding("external-new", "2026-08-26T11:00:00");
    ExternalInstanceBinding olderExternal = binding("external-old", "2026-08-26T08:00:00");
    when(mappingService.findByScopeAndFlowKey("default", "flow-a")).thenReturn(List.of(mapping()));
    when(instanceBindingService.searchByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_FINISHED, 0, 3))
        .thenReturn(List.of(newestExternal, olderExternal));

    List<FlowInstanceDetail> instances = routed.getInstances("flow-a", "finished", 1, 2);

    assertThat(instances).extracting(FlowInstanceDetail::getInstanceId)
        .containsExactly("internal-new", "internal-old");
    verify(internal).getInstances("flow-a", "finished", 0, 3);
    verify(instanceBindingService).searchByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_FINISHED, 0, 3);
  }

  @Test
  void getInstancesCountSumsExternalBindings() {
    when(internal.getInstancesCount("flow-a", "finished")).thenReturn(2L);
    when(mappingService.findByScopeAndFlowKey("default", "flow-a")).thenReturn(List.of(mapping()));
    when(instanceBindingService.countByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_FINISHED))
        .thenReturn(3L);

    assertThat(routed.getInstancesCount("flow-a", "finished")).isEqualTo(5L);
  }

  @Test
  void getStatisticIncludesExternalOnlyActiveMapping() {
    when(internal.getStatisticCount(null, "deployed")).thenReturn(0L);
    when(internal.getStatistic(null, "deployed", 0, 0)).thenReturn(List.of());
    WorkflowEngineMapping mapping = mapping();
    mapping.setExternalDefinitionId("approval-external-def");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    when(instanceBindingService.countByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_RUNNING))
        .thenReturn(2L);
    when(instanceBindingService.countByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_SUSPENDED))
        .thenReturn(1L);
    when(instanceBindingService.countByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_FINISHED))
        .thenReturn(3L);

    List<FlowInstanceStatistic> statistics = routed.getStatistic(null, "deployed", 0, 10);

    assertThat(statistics).hasSize(1);
    FlowInstanceStatistic statistic = statistics.get(0);
    assertThat(statistic.getFlowKey()).isEqualTo("flow-a");
    assertThat(statistic.getStatus()).isEqualTo("deployed");
    assertThat(statistic.getRunning()).isEqualTo(2L);
    assertThat(statistic.getSuspended()).isEqualTo(1L);
    assertThat(statistic.getFinished()).isEqualTo(3L);
    assertThat(statistic.getTotal()).isEqualTo(6L);
    verify(instanceBindingService, never()).create(any());
  }

  @Test
  void getStatisticMergesExternalCountsIntoInternalRowWithoutWriteSideEffects() {
    FlowInstanceStatistic internalStatistic = statistic("flow-a", 4L, 1L, 0L, 3L);
    when(internal.getStatisticCount(null, "deployed")).thenReturn(1L);
    when(internal.getStatistic(null, "deployed", 0, 1)).thenReturn(List.of(internalStatistic));
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping()));
    when(instanceBindingService.countByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_RUNNING))
        .thenReturn(2L);
    when(instanceBindingService.countByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_SUSPENDED))
        .thenReturn(1L);
    when(instanceBindingService.countByRoute(
        "default", "m-1", 1, "fake-adapter", "conn-1", ExternalInstanceBinding.STATUS_FINISHED))
        .thenReturn(3L);

    List<FlowInstanceStatistic> statistics = routed.getStatistic(null, "deployed", 0, 10);

    assertThat(statistics).hasSize(1);
    assertThat(statistics.get(0).getTotal()).isEqualTo(10L);
    assertThat(statistics.get(0).getRunning()).isEqualTo(3L);
    assertThat(statistics.get(0).getSuspended()).isEqualTo(1L);
    assertThat(statistics.get(0).getFinished()).isEqualTo(6L);
    verify(instanceBindingService, never()).create(any());
    verify(instanceBindingService, never()).updateStatus(any(), any(), any(), any(), any(), any());
  }

  @Test
  void getStatisticCountAddsExternalOnlyRowsNotExternalInstanceTotals() {
    when(internal.getStatisticCount(null, "deployed")).thenReturn(1L);
    when(internal.getStatistic(null, "deployed", 0, 1))
        .thenReturn(List.of(statistic("flow-a", 4L, 1L, 0L, 3L)));
    WorkflowEngineMapping sameFlow = mapping();
    WorkflowEngineMapping externalOnly = mapping();
    externalOnly.setId("m-2");
    externalOnly.setFlowKey("flow-b");
    externalOnly.setConnectionId("conn-2");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(sameFlow, externalOnly));

    Long count = routed.getStatisticCount(null, "deployed");

    assertThat(count).isEqualTo(2L);
    verify(instanceBindingService, never()).countByRoute(any(), any(), any(), any(), any(), any());
    verify(instanceBindingService, never()).create(any());
  }

  @Test
  void resultOnlyTrackingIdRejectsPauseResumeComplete() {
    ExternalResultBinding resultBinding = new ExternalResultBinding();
    resultBinding.setTrackingId("tracking-1");
    resultBinding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    when(resultBindingService.findByTrackingId("tracking-1")).thenReturn(Optional.of(resultBinding));

    assertThat(routed.pause("tracking-1", "user-1", "暂停").isFailure()).isTrue();
    assertThat(routed.pause("tracking-1", "user-1", "暂停").getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
    assertThat(routed.resume("tracking-1", "user-1").isFailure()).isTrue();
    assertThat(routed.complete("tracking-1", "user-1", true, null).isFailure()).isTrue();
    verify(internal, never()).pause(any(), any(), any());
    verify(internal, never()).resume(any(), any());
    verify(internal, never()).complete(any(), any(), any(), any());
  }

  @Test
  void resultOnlyPresentationHasNoLocalTaskOrDiagramAndKeepsExternalLink() {
    ExternalResultBinding binding = resultBindingForView();
    when(resultBindingService.findByTrackingId("tracking-1")).thenReturn(Optional.of(binding));

    var info = routed.getProcessInfo("tracking-1").get();

    assertThat(info.getInstanceId()).isEqualTo("tracking-1");
    assertThat(info.getStatus()).isEqualTo("running");
    assertThat(info.getExternalUrl()).isEqualTo("https://approval.example/1");
    assertThat(info.isProcessable()).isFalse();
    assertThat(info.getProcessableTaskId()).isNull();
    assertThat(info.getTasks()).isEmpty();
    assertThat(info.isDiagramAvailable()).isFalse();
    assertThat(info.isTimelineAvailable()).isFalse();
    assertThat(info.isCancellable()).isFalse();
    verify(internal, never()).getProcessInfo("tracking-1");
  }

  @Test
  void completedResultOnlyPresentationKeepsOutcomeWithoutLocalQueries() {
    ExternalResultBinding binding = resultBindingForView();
    binding.setStatus(ExternalResultBinding.STATUS_FINISHED);
    binding.setOutcome("approved");
    binding.setFinalResultAt(java.time.LocalDateTime.of(2026, 11, 15, 12, 0));
    when(resultBindingService.findByTrackingId("tracking-1")).thenReturn(Optional.of(binding));

    var info = routed.getProcessInfo("tracking-1").get();

    assertThat(info.getStatus()).isEqualTo("finished");
    assertThat(info.getOutcome()).isEqualTo("approved");
    assertThat(info.getEndTime()).isEqualTo(binding.getFinalResultAt());
    assertThat(info.isCancellable()).isFalse();
  }

  @Test
  void resultOnlyRecordIsAttachedThroughCanonicalApprovalCase() {
    var cases = mock(ApprovalCaseService.class);
    routed = newRouted(RoutedFlowInstanceService.UNMAPPED_POLICY_INTERNAL, cases);
    var approvalCase = new com.ouroboros.workflow.approval.lifecycle.ApprovalCase();
    approvalCase.setActiveExecutionId("tracking-1");
    when(cases.findByBusinessRecord("sales.Order", "42", "flow-a"))
        .thenReturn(Optional.of(approvalCase));
    String url = "/sales/order/42/approval";
    when(internal.getProcessInfoByApprovalUrls("flow-a", List.of(url))).thenReturn(Try.success(Map.of()));
    when(resultBindingService.findByTrackingId("tracking-1"))
        .thenReturn(Optional.of(resultBindingForView()));

    var info = routed.getProcessInfoByApprovalUrls("flow-a", List.of(url), "sales.Order").get().get(url);

    assertThat(info.getInstanceId()).isEqualTo("tracking-1");
    assertThat(info.getExternalUrl()).isEqualTo("https://approval.example/1");
    assertThat(info.isProcessable()).isFalse();
  }

  @Test
  void legacyApprovalUrlReadStillDelegatesToBuiltInEngine() {
    var info = new com.ouroboros.workflow.approval.model.FlowProcessInfo();
    info.setInstanceId("local-1");
    String url = "/sales/order/42/approval";
    when(internal.getProcessInfoByApprovalUrls("flow-a", List.of(url)))
        .thenReturn(Try.success(Map.of(url, info)));

    assertThat(routed.getProcessInfoByApprovalUrls("flow-a", List.of(url)).get().get(url))
        .isSameAs(info);
  }

  @Test
  void currentBuiltInCaseReusesTheExistingBulkResult() {
    var cases = mock(ApprovalCaseService.class);
    routed = newRouted(RoutedFlowInstanceService.UNMAPPED_POLICY_INTERNAL, cases);
    var approvalCase = new com.ouroboros.workflow.approval.lifecycle.ApprovalCase();
    approvalCase.setActiveExecutionId("local-1");
    when(cases.findByBusinessRecord("sales.Order", "42", "flow-a"))
        .thenReturn(Optional.of(approvalCase));
    var info = new com.ouroboros.workflow.approval.model.FlowProcessInfo();
    info.setInstanceId("local-1");
    String url = "/sales/order/42/approval";
    when(internal.getProcessInfoByApprovalUrls("flow-a", List.of(url)))
        .thenReturn(Try.success(Map.of(url, info)));

    assertThat(routed.getProcessInfoByApprovalUrls("flow-a", List.of(url), "sales.Order").get().get(url))
        .isSameAs(info);
    verify(internal, never()).getProcessInfo(any());
  }

  @Test
  void resultOnlyTimelineUsesTheRegisteredRecordReaderAndNeverTheLocalEngine() {
    when(resultBindingService.findByTrackingId("tracking-1"))
        .thenReturn(Optional.of(resultBindingForView()));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", new FakeRecordReaderAdapter(), Set.of(Capability.EXTERNAL_RECORD_QUERY))));
    when(registry.capabilitiesOf("fake-adapter")).thenReturn(Set.of(Capability.EXTERNAL_RECORD_QUERY));
    when(identityResolver.toPlatformUser("ext-user", "fake-adapter", "conn-1"))
        .thenReturn(Optional.of("reviewer-1"));

    assertThat(routed.getProcessInfo("tracking-1").get().isTimelineAvailable()).isTrue();
    var timeline = routed.getTimeLine("tracking-1");
    assertThat(timeline).hasSize(1);
    assertThat(timeline.get(0).getTaskInstances().get(0).getAssignee()).isEqualTo("reviewer-1");
    verify(internal, never()).getTimeLine("tracking-1");
  }

  @Test
  void resultOnlyDiagramCannotAccidentallyQueryTheLocalEngine() {
    when(resultBindingService.findByTrackingId("tracking-1"))
        .thenReturn(Optional.of(resultBindingForView()));

    assertThat(routed.getInstanceFlowDefinitionV2("tracking-1").isFailure()).isTrue();
    assertThat(routed.getActiveActivityIds("tracking-1")).isEmpty();
    verify(internal, never()).getInstanceFlowDefinitionV2("tracking-1");
    verify(internal, never()).getActiveActivityIds("tracking-1");
  }

  @Test
  void resultOnlyPresentationRejectsOtherScopes() {
    var binding = resultBindingForView();
    binding.setScope("another-app");
    when(resultBindingService.findByTrackingId("tracking-1")).thenReturn(Optional.of(binding));

    assertThat(routed.getProcessInfo("tracking-1").getCause())
        .hasMessageContaining(EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED);
  }

  @Test
  void resultBindingCannotGrantExternalReadAuthorityToABuiltInMapping() {
    var builtIn = mapping();
    builtIn.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(builtIn));
    when(resultBindingService.findByTrackingId("tracking-1"))
        .thenReturn(Optional.of(resultBindingForView()));

    assertThat(routed.getProcessInfo("tracking-1").getCause())
        .hasMessageContaining(EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> routed.getTimeLine("tracking-1"))
        .hasMessageContaining(EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE);
    verify(registry, never()).getAdapter(any());
    verify(internal, never()).getTimeLine("tracking-1");
  }

  @Test
  void resultOnlyCancelButtonRequiresCapabilityAndRunningState() {
    var binding = resultBindingForView();
    when(resultBindingService.findByTrackingId("tracking-1")).thenReturn(Optional.of(binding));
    when(registry.capabilitiesOf("fake-adapter")).thenReturn(Set.of(Capability.EXTERNAL_INSTANCE_CANCEL));
    assertThat(routed.getProcessInfo("tracking-1").get().isCancellable()).isTrue();
    binding.setStatus(ExternalResultBinding.STATUS_FINISHED);
    assertThat(routed.getProcessInfo("tracking-1").get().isCancellable()).isFalse();
  }

  @Test
  void uncertainStartPresentationDoesNotAdvertiseTasksOrInstanceOperations() {
    var fact = new ExternalStartFact();
    fact.setTrackingId("tracking-pending");
    fact.setStatus(ExternalStartFact.STATUS_START_UNKNOWN);
    when(startFactService.findByTrackingId("default", "tracking-pending")).thenReturn(Optional.of(fact));

    var info = routed.getProcessInfo("tracking-pending").get();
    assertThat(info.getStatus()).isEqualTo("start_unknown");
    assertThat(info.isCancellable()).isFalse();
    assertThat(info.isDiagramAvailable()).isFalse();
    assertThat(info.getTasks()).isEmpty();
  }

  private ExternalResultBinding resultBindingForView() {
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId("tracking-1");
    binding.setScope("default");
    binding.setFlowKey("flow-a");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setExternalInstanceId("ext-1");
    binding.setExternalUrl("https://approval.example/1");
    binding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    return binding;
  }

  @Test
  void timelineAssigneesAreResolvedToPlatformUsers() {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("inst-1");
    binding.setFlowKey("flow-a");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setExternalInstanceId("ext-1");
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    when(instanceBindingService.findByLocalId("inst-1")).thenReturn(Optional.of(binding));
    FakeRecordReaderAdapter reader = new FakeRecordReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", reader, Set.of(Capability.EXTERNAL_RECORD_QUERY))));
    when(identityResolver.toPlatformUser("ext-user", "fake-adapter", "conn-1"))
        .thenReturn(Optional.of("u-100"));

    List<FlowTimeline> timeline = routed.getTimeLine("inst-1");

    assertThat(timeline).hasSize(1);
    assertThat(timeline.get(0).getTaskInstances().get(0).getAssignee()).isEqualTo("u-100");
    assertThat(timeline.get(0).getTaskInstances().get(0).getState()).isEqualTo(TaskState.APPROVED);
  }

  @Test
  void timelineUnresolvableAssigneeFailsFast() {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("inst-1");
    binding.setFlowKey("flow-a");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setExternalInstanceId("ext-1");
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    when(instanceBindingService.findByLocalId("inst-1")).thenReturn(Optional.of(binding));
    FakeRecordReaderAdapter reader = new FakeRecordReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", reader, Set.of(Capability.EXTERNAL_RECORD_QUERY))));
    when(identityResolver.toPlatformUser("ext-user", "fake-adapter", "conn-1"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> routed.getTimeLine("inst-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING);
  }

  @Test
  void externalTimelineQueryFailureFailsFast() {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("inst-1");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setExternalInstanceId("ext-1");
    when(instanceBindingService.findByLocalId("inst-1")).thenReturn(Optional.of(binding));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(
        ConnectionAwareAdapterResolverTestSupport.registered(
            "fake-adapter", new FailingRecordReaderAdapter(), Set.of(Capability.EXTERNAL_RECORD_QUERY))));

    assertThatThrownBy(() -> routed.getTimeLine("inst-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE);
  }

  private WorkflowEngineMapping mapping() {
    WorkflowEngineMapping m = new WorkflowEngineMapping();
    m.setId("m-1");
    m.setScope("default");
    m.setFlowKey("flow-a");
    m.setVersion(1);
    m.setAdapterId("fake-adapter");
    m.setConnectionId("conn-1");
    m.setAuthority("EXTERNAL_ENGINE");
    m.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    return m;
  }

  private FlowInstanceDetail instanceDetail(String instanceId, String flowKey, String status, String startTime) {
    FlowInstanceDetail detail = new FlowInstanceDetail();
    detail.setInstanceId(instanceId);
    detail.setFlowKey(flowKey);
    detail.setStatus(status);
    detail.setStartTime(java.time.LocalDateTime.parse(startTime));
    return detail;
  }

  private ExternalInstanceBinding binding(String localInstanceId, String createdAt) {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId(localInstanceId);
    binding.setFlowKey("flow-a");
    binding.setScope("default");
    binding.setStatus(ExternalInstanceBinding.STATUS_FINISHED);
    binding.setOutcome("approved");
    binding.setCreatedAt(java.time.LocalDateTime.parse(createdAt));
    return binding;
  }

  private FlowInstanceStatistic statistic(
      String flowKey, Long total, Long running, Long suspended, Long finished) {
    FlowInstanceStatistic statistic = new FlowInstanceStatistic();
    statistic.setFlowKey(flowKey);
    statistic.setDisplayName(flowKey);
    statistic.setStatus("deployed");
    statistic.setTotal(total);
    statistic.setRunning(running);
    statistic.setSuspended(suspended);
    statistic.setFinished(finished);
    return statistic;
  }

  /** 测试适配器：记录实例动作命令。 */
  static final class FakeInstanceActionAdapter
      implements ApprovalWorkflowEngineAdapter, ExternalWorkflowActionExecutor {
    final List<ExternalActionCommand> commands = new ArrayList<>();

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalActionResult> execute(ExternalActionCommand command) {
      commands.add(command);
      return Try.success(new ExternalActionResult(true, "CANCELED", "withdrawn", null, null));
    }
  }

  /** 测试适配器：返回固定审批记录（时间线）。 */
  static final class FakeRecordReaderAdapter implements ApprovalWorkflowEngineAdapter, ExternalRecordReader {
    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalRecordPage> queryRecords(ExternalInstanceRef instance) {
      return Try.success(new ExternalRecordPage(List.of(
          new ExternalRecordPage.ExternalRecordEntry("node-1", "ext-user", "approve", "同意", "2026-08-25T00:00:00")),
          false));
    }
  }

  /** 测试适配器：审批记录查询失败。 */
  static final class FailingRecordReaderAdapter implements ApprovalWorkflowEngineAdapter, ExternalRecordReader {
    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalRecordPage> queryRecords(ExternalInstanceRef instance) {
      return Try.failure(new IllegalStateException("record store down"));
    }
  }

  /** 测试适配器：记录启动命令。 */
  static final class FakeStarterAdapter implements ApprovalWorkflowEngineAdapter, ExternalWorkflowStarter {
    final List<ExternalStartCommand> commands = new ArrayList<>();
    boolean throwOnStart;

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalStartResult> start(ExternalStartCommand command) {
      commands.add(command);
      if (throwOnStart) {
        throw new IllegalStateException("socket closed");
      }
      return Try.success(new ExternalStartResult("ext-1", "http://external/1", null));
    }
  }
}
