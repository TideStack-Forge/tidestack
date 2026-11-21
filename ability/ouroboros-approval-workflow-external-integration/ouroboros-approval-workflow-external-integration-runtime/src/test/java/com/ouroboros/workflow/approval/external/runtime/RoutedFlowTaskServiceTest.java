package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.BuiltInFlowTaskService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalActionResult;
import com.ouroboros.workflow.approval.external.ExternalActionCommand;
import com.ouroboros.workflow.approval.external.ExternalFormSnapshot;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalTaskPage;
import com.ouroboros.workflow.approval.external.ExternalTaskQuery;
import com.ouroboros.workflow.approval.external.ExternalTaskReader;
import com.ouroboros.workflow.approval.external.ExternalTaskRef;
import com.ouroboros.workflow.approval.external.ExternalTaskSnapshot;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.runtime.assignment.ExternalPendingAssignmentService;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionExecutionService;
import com.ouroboros.workflow.approval.external.runtime.action.ExternalActionRequest;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalUserIdentityResolver;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowTaskService;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;
import com.ouroboros.uischema.schema.SchemaNode;

import io.vavr.control.Try;

/**
 * M10 任务路由测试：doAction 动作映射执行、LINK 模式、外部任务只读语义、待办聚合。
 */
class RoutedFlowTaskServiceTest {

  private BuiltInFlowTaskService internal;
  private WorkflowEngineAdapterRegistry registry;
  private ConnectionAwareAdapterResolver adapterResolver;
  private ExternalTaskBindingService taskBindingService;
  private ExternalInstanceBindingService instanceBindingService;
  private WorkflowEngineMappingService mappingService;
  private ExternalUserIdentityResolver identityResolver;
  private WorkflowScopeResolver scopeResolver;
  private ExternalPendingAssignmentService pendingAssignmentService;
  private ExternalActionExecutionService actionExecutionService;
  private RoutedFlowTaskService routed;

  @BeforeEach
  void setUp() {
    internal = mock(BuiltInFlowTaskService.class);
    registry = mock(WorkflowEngineAdapterRegistry.class);
    adapterResolver = ConnectionAwareAdapterResolverTestSupport.resolver(registry);
    when(registry.capabilitiesOf(anyString())).thenReturn(Set.of(Capability.EXTERNAL_TASK_QUERY));
    // 派生值：能力齐备 → FULL_PROCESS + PROXY；需要 LINK 的用例单独覆盖 executionModeOf。
    when(registry.integrationModeOf(anyString())).thenReturn("FULL_PROCESS");
    when(registry.executionModeOf(anyString())).thenReturn("PROXY");
    taskBindingService = mock(ExternalTaskBindingService.class);
    instanceBindingService = mock(ExternalInstanceBindingService.class);
    mappingService = mock(WorkflowEngineMappingService.class);
    when(mappingService.findByIdForSystem(anyString()))
        .thenReturn(Optional.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));
    identityResolver = mock(ExternalUserIdentityResolver.class);
    when(identityResolver.toPlatformUser(anyString(), anyString(), anyString())).thenReturn(Optional.of("u-100"));
    scopeResolver = mock(WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("default");
    pendingAssignmentService = mock(ExternalPendingAssignmentService.class);
    actionExecutionService = mock(ExternalActionExecutionService.class);
    when(instanceBindingService.findByExternalInstance(anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(instanceBinding("ext-inst-1")));
    when(instanceBindingService.findByLocalId("local-inst-1"))
        .thenReturn(Optional.of(instanceBinding("ext-inst-1")));
    when(taskBindingService.create(any())).thenAnswer(invocation -> {
      ExternalTaskBinding created = invocation.getArgument(0);
      created.setLocalTaskId("local-" + created.getExternalTaskId());
      return Try.success(created);
    });
    TaskObjectProvider provider = mock(TaskObjectProvider.class);
    when(provider.stream()).thenReturn(Stream.of(internal));
    routed = new RoutedFlowTaskService(
        provider, registry, adapterResolver, taskBindingService,
        instanceBindingService, mappingService, identityResolver, scopeResolver,
        pendingAssignmentService, actionExecutionService);
  }

  /** 类型安全的 ObjectProvider 子接口，避免原始类型 mock。 */
  interface TaskObjectProvider extends ObjectProvider<BuiltInFlowTaskService> {
  }

  @Test
  void externalTaskActionRoutesToReliableActionCoordinator() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(actionExecutionService.executeTask(any(), any(), any())).thenReturn(Try.success(null));

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", "ok", null, null);

    assertThat(result.isSuccess()).isTrue();
    org.mockito.ArgumentCaptor<ExternalActionRequest> requestCaptor =
        org.mockito.ArgumentCaptor.forClass(ExternalActionRequest.class);
    verify(actionExecutionService).executeTask(
        eq(task), requestCaptor.capture(), eq(ExternalTaskBinding.STATUS_APPROVED));
    ExternalActionRequest request = requestCaptor.getValue();
    assertThat(request.canonicalAction()).isEqualTo("approve");
    assertThat(request.externalAction()).isEqualTo("APPROVE");
    assertThat(request.externalTaskRef().externalTaskId()).isEqualTo("ext-task-1");
  }

  @Test
  void externalTaskActionRejectsNonAssigneeBeforeReliableExecution() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(actionExecutionService.executeTask(any(), any(), any())).thenReturn(Try.success(null));

    Try<Void> result = routed.doAction("t-1", "approve", "user-2", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED);
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
  }

  @Test
  void externalAuthorizationUsesCurrentExternalAssigneeAfterReassignment() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(registry.capabilitiesOf("fake-adapter"))
        .thenReturn(Set.of(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_AUTHORIZATION));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.getTask(any())).thenReturn(Try.success(new ExternalTaskSnapshot(
        "ext-task-1", "node-1", "ext-user", "RUNNING", null, Map.of())));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader),
        Set.of(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_AUTHORIZATION))));
    when(actionExecutionService.executeTask(any(), any(), any())).thenReturn(Try.success(null));

    Try<Void> result = routed.doAction("t-1", "approve", "u-100", null, null, null);

    assertThat(result.isSuccess()).isTrue();
    verify(actionExecutionService).executeTask(any(), any(), eq(ExternalTaskBinding.STATUS_APPROVED));
  }

  @Test
  void externalAuthorizationRejectsPreviousAssigneeAfterReassignment() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(registry.capabilitiesOf("fake-adapter"))
        .thenReturn(Set.of(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_AUTHORIZATION));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.getTask(any())).thenReturn(Try.success(new ExternalTaskSnapshot(
        "ext-task-1", "node-1", "ext-user", "RUNNING", null, Map.of())));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader),
        Set.of(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_AUTHORIZATION))));

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED);
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
  }

  @Test
  void externalAuthorizationRejectsTerminalSnapshotBeforeReliableExecution() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(registry.capabilitiesOf("fake-adapter"))
        .thenReturn(Set.of(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_AUTHORIZATION));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.getTask(any())).thenReturn(Try.success(new ExternalTaskSnapshot(
        "ext-task-1", "node-1", "ext-user", "APPROVED", null, Map.of())));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader),
        Set.of(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_AUTHORIZATION))));

    Try<Void> result = routed.doAction("t-1", "approve", "u-100", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
  }

  @Test
  void externalAuthorizationFailsClosedWhenSnapshotReadFails() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(registry.capabilitiesOf("fake-adapter"))
        .thenReturn(Set.of(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_AUTHORIZATION));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.getTask(any())).thenReturn(Try.failure(new IllegalStateException("unavailable")));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader),
        Set.of(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_AUTHORIZATION))));

    Try<Void> result = routed.doAction("t-1", "approve", "u-100", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE);
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
  }

  @Test
  void externalTaskActionRejectsScopeMismatchBeforeReliableExecution() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    when(scopeResolver.resolve()).thenReturn("other-scope");
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(actionExecutionService.executeTask(any(), any(), any())).thenReturn(Try.success(null));

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED);
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
  }

  @Test
  void externalTaskActionRejectsTerminalBindingBeforeReliableExecution() {
    ExternalTaskBinding task = taskBinding("t-1");
    task.setStatus(ExternalTaskBinding.STATUS_APPROVED);
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(actionExecutionService.executeTask(any(), any(), any())).thenReturn(Try.success(null));

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
  }

  @Test
  void ouroborosAuthorityBindingAlwaysUsesBuiltInTaskService() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(internal.doAction("t-1", "approve", "user-1", "ok", null, null))
        .thenReturn(Try.success(null));

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", "ok", null, null);

    assertThat(result.isSuccess()).isTrue();
    verify(internal).doAction("t-1", "approve", "user-1", "ok", null, null);
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
    verify(registry, never()).getAdapter(anyString());
  }

  @Test
  void bindingMappingVersionMismatchFailsClosed() {
    ExternalTaskBinding task = taskBinding("t-1");
    task.setMappingVersion(2);
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    when(mappingService.findByIdForSystem("m-1"))
        .thenReturn(Optional.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("映射版本不匹配");
    verify(internal, never()).doAction(anyString(), anyString(), anyString(), any(), any(), any());
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
  }

  @Test
  void unmappedActionFailsWithoutCallingAdapter() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    FakeActionAdapter adapter = new FakeActionAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(adapter)));

    Try<Void> result = routed.doAction("t-1", "transfer", "user-1", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_NOT_MAPPED);
    assertThat(adapter.commands).isEmpty();
    verify(actionExecutionService, never()).executeTask(any(), any(), any());
  }

  @Test
  void linkModeRequiresExternalInteraction() {
    ExternalTaskBinding task = taskBinding("t-1");
    task.setExternalUrl("http://external/task/1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    // 交互执行模式由能力派生：无动作执行端口的适配器 → LINK，必须要求跳转外部处理。
    when(registry.executionModeOf("fake-adapter")).thenReturn("LINK");

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_INTERACTION_REQUIRED);
  }

  @Test
  void missingExecutorCapabilityFails() {
    ExternalTaskBinding task = taskBinding("t-1");
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(task));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));
    when(actionExecutionService.executeTask(any(), any(), any())).thenReturn(Try.failure(
        new com.ouroboros.exception.BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED + "] 不支持动作端口")));

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", null, null, null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
    verify(actionExecutionService).executeTask(any(), any(), any());
  }

  @Test
  void nonExternalTaskDelegatesToInternal() {
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.empty());
    when(internal.doAction("t-1", "approve", "user-1", null, null, null)).thenReturn(Try.success(null));

    Try<Void> result = routed.doAction("t-1", "approve", "user-1", null, null, null);

    assertThat(result.isSuccess()).isTrue();
    verify(internal).doAction("t-1", "approve", "user-1", null, null, null);
  }

  @Test
  void externalTaskReassignIsRejected() {
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(taskBinding("t-1")));

    assertThatThrownBy(() -> routed.setTaskAssignee("t-1", "user-2"))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
    verify(internal, never()).setTaskAssignee(any(), any());
  }

  @Test
  void externalTaskRawReadFailsWhenReaderCapabilityMissing() {
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(taskBinding("t-1")));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new ApprovalWorkflowEngineAdapter() {
          @Override
          public Try<ExternalHealth> health() {
            return Try.success(new ExternalHealth(true, "ok"));
          }
        })));

    assertThatThrownBy(() -> routed.getTaskFormData("t-1"))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
  }

  @Test
  void externalTaskRawReadFailureFailsFast() {
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(taskBinding("t-1")));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.getTask(any())).thenReturn(Try.failure(new IllegalStateException("reader down")));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader), Set.of(Capability.EXTERNAL_TASK_QUERY))));

    assertThatThrownBy(() -> routed.getTaskFormData("t-1"))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE);
  }

  @Test
  void getTaskListMergesInternalAndExternalTasks() {
    FlowTaskDetail internalDetail = new FlowTaskDetail();
    internalDetail.setId("in-1");
    internalDetail.setFlowKey("flow-a");
    when(internal.getTaskList("user-1", null, 0, 100)).thenReturn(List.of(internalDetail));
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    FakeReaderAdapter adapter = new FakeReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        adapter, Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "ext-task-9")).thenReturn(Optional.empty());

    List<FlowTaskDetail> merged = routed.getTaskList("user-1", 0, 100);

    assertThat(merged).hasSize(2);
    FlowTaskDetail external = merged.stream()
        .filter(detail -> "local-ext-task-9".equals(detail.getId()))
        .findFirst().orElseThrow();
    assertThat(external.getFlowKey()).isEqualTo("flow-a");
    assertThat(external.getInstanceId()).isEqualTo("local-inst-1");
    assertThat(external.getAssignee()).isEqualTo("u-100");
    assertThat(external.getStartTime()).isNull();
    verify(taskBindingService).create(argThat(binding ->
        "ext-task-9".equals(binding.getExternalTaskId())
            && "local-inst-1".equals(binding.getLocalInstanceId())
            && "u-100".equals(binding.getAssignee())));
    verify(pendingAssignmentService, never()).recordMissingMapping(any(), any(), anyString());
  }

  @Test
  void getTaskListRefreshesStaleAssigneeOnExistingBinding() {
    when(mappingService.findActiveByScope("default"))
        .thenReturn(List.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new FakeReaderAdapter(), Set.of(Capability.EXTERNAL_TASK_QUERY))));
    ExternalTaskBinding existing = taskBinding("local-ext-task-9");
    existing.setAssignee("u-old");
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "ext-task-9"))
        .thenReturn(Optional.of(existing));
    when(taskBindingService.syncAssignee("local-ext-task-9", "u-100")).thenReturn(Try.success(true));

    List<FlowTaskDetail> merged = routed.getTaskList("user-1", 0, 100);

    verify(taskBindingService).syncAssignee("local-ext-task-9", "u-100");
    verify(taskBindingService, never()).create(any());
    assertThat(merged).extracting(FlowTaskDetail::getId).containsExactly("local-ext-task-9");
    assertThat(merged.get(0).getAssignee()).isEqualTo("u-100");
  }

  @Test
  void getTaskListSkipsAssigneeSyncWhenBindingMatchesSnapshot() {
    when(mappingService.findActiveByScope("default"))
        .thenReturn(List.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new FakeReaderAdapter(), Set.of(Capability.EXTERNAL_TASK_QUERY))));
    ExternalTaskBinding existing = taskBinding("local-ext-task-9");
    existing.setAssignee("u-100");
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "ext-task-9"))
        .thenReturn(Optional.of(existing));

    routed.getTaskList("user-1", 0, 100);

    verify(taskBindingService, never()).syncAssignee(any(), any());
  }

  @Test
  void getTaskListFailsClosedWhenAssigneeSyncFails() {
    when(mappingService.findActiveByScope("default"))
        .thenReturn(List.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new FakeReaderAdapter(), Set.of(Capability.EXTERNAL_TASK_QUERY))));
    ExternalTaskBinding existing = taskBinding("local-ext-task-9");
    existing.setAssignee("u-old");
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "ext-task-9"))
        .thenReturn(Optional.of(existing));
    when(taskBindingService.syncAssignee("local-ext-task-9", "u-100"))
        .thenReturn(Try.failure(new IllegalStateException("写库失败")));

    assertThatThrownBy(() -> routed.getTaskList("user-1", 0, 100))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED)
        .hasMessageContaining("同步外部任务处理人失败");
  }

  @Test
  void getTaskListQueuesUnmappedExternalAssigneeAndSkipsTask() {
    when(internal.getTaskList("user-1", null, 0, 100)).thenReturn(List.of());
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    FakeReaderAdapter adapter = new FakeReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        adapter, Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "ext-task-9")).thenReturn(Optional.empty());
    when(identityResolver.toPlatformUser("ext-user", "fake-adapter", "conn-1"))
        .thenReturn(Optional.empty());

    List<FlowTaskDetail> merged = routed.getTaskList("user-1", 0, 100);

    assertThat(merged).isEmpty();
    verify(pendingAssignmentService).recordMissingMapping(
        eq(mapping),
        argThat(snapshot -> "ext-task-9".equals(snapshot.externalTaskId())
            && "ext-user".equals(snapshot.assignee())),
        eq(EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING));
    verify(taskBindingService, never()).create(any());
  }

  @Test
  void getTaskListSkipsTerminalDiscoveredTaskWithoutCreatingBinding() {
    when(internal.getTaskList("user-1", null, 0, 100)).thenReturn(List.of());
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.queryTasks(any(ExternalTaskQuery.class))).thenReturn(Try.success(new ExternalTaskPage(
        List.of(new ExternalTaskSnapshot("ext-task-terminal", "node-1", "ext-user", "APPROVED",
            null, Map.of("externalInstanceId", "ext-inst-1"))), false, null)));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader), Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(taskBindingService.findByExternalTask(any(), any(), any())).thenReturn(Optional.empty());

    List<FlowTaskDetail> merged = routed.getTaskList("user-1", 0, 100);

    assertThat(merged).isEmpty();
    verify(taskBindingService, never()).create(any());
  }

  @Test
  void getTaskListSkipsDiscoveredTaskWhenInstanceBindingRouteDiffers() {
    when(internal.getTaskList("user-1", null, 0, 100)).thenReturn(List.of());
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    FakeReaderAdapter adapter = new FakeReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        adapter, Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "ext-task-9"))
        .thenReturn(Optional.empty());
    com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding instance =
        instanceBinding("ext-inst-1");
    instance.setMappingVersion(2);
    when(instanceBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-inst-1"))
        .thenReturn(Optional.of(instance));

    List<FlowTaskDetail> merged = routed.getTaskList("user-1", 0, 100);

    assertThat(merged).isEmpty();
    verify(taskBindingService, never()).create(any());
  }

  @Test
  void getTaskListContinuesFetchingWhenSourceReturnsNextCursor() {
    when(internal.getTaskList("user-1", null, 0, 100)).thenReturn(List.of());
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.queryTasks(new ExternalTaskQuery("user-1", "conn-1", null, 100)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(snapshot("ext-task-1")), true, "c1")));
    when(reader.queryTasks(new ExternalTaskQuery("user-1", "conn-1", "c1", 100)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(snapshot("ext-task-2")), false, null)));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader), Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(taskBindingService.findByExternalTask(any(), any(), any())).thenReturn(Optional.empty());
    when(identityResolver.toPlatformUser(any(), any(), any())).thenReturn(Optional.of("u-100"));

    List<FlowTaskDetail> merged = routed.getTaskList("user-1", 0, 100);

    assertThat(merged).hasSize(2);
    assertThat(merged).extracting(FlowTaskDetail::getId)
        .contains("local-ext-task-1", "local-ext-task-2");
    verify(reader).queryTasks(new ExternalTaskQuery("user-1", "conn-1", null, 100));
    verify(reader).queryTasks(new ExternalTaskQuery("user-1", "conn-1", "c1", 100));
  }

  @Test
  void getTaskListStopsAfterSinglePageWhenHasMoreWithoutCursor() {
    when(internal.getTaskList("user-1", null, 0, 100)).thenReturn(List.of());
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.queryTasks(any(ExternalTaskQuery.class)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(snapshot("ext-task-1")), true, null)));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader), Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(taskBindingService.findByExternalTask(any(), any(), any())).thenReturn(Optional.empty());
    when(identityResolver.toPlatformUser(any(), any(), any())).thenReturn(Optional.of("u-100"));

    List<FlowTaskDetail> merged = routed.getTaskList("user-1", 0, 100);

    // 无 nextCursor 的 hasMore（无游标能力，L456 截断语义）：单页即止
    assertThat(merged).hasSize(1);
    verify(taskBindingService).create(any());
    verify(reader, org.mockito.Mockito.times(1)).queryTasks(any(ExternalTaskQuery.class));
  }

  @Test
  void getTaskListMergesThreeSourcesIncludingEmptyPageSource() {
    when(internal.getTaskList("user-1", null, 0, 100)).thenReturn(List.of(internalDetail("in-1")));
    WorkflowEngineMapping mappingA = fullProcessMapping("m-a", "flow-a", "fake-adapter");
    WorkflowEngineMapping mappingB = fullProcessMapping("m-b", "flow-b", "empty-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mappingA, mappingB));
    ExternalTaskReader readerA = mock(ExternalTaskReader.class);
    when(readerA.queryTasks(new ExternalTaskQuery("user-1", "conn-1", null, 100)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(snapshot("ext-task-1")), true, "c1")));
    when(readerA.queryTasks(new ExternalTaskQuery("user-1", "conn-1", "c1", 100)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(snapshot("ext-task-2")), false, null)));
    ExternalTaskReader emptyReader = mock(ExternalTaskReader.class);
    when(emptyReader.queryTasks(any(ExternalTaskQuery.class)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(), false, null)));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(readerA), Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(registry.getAdapter("empty-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(emptyReader), Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(taskBindingService.findByExternalTask(any(), any(), any())).thenReturn(Optional.empty());
    when(identityResolver.toPlatformUser(any(), any(), any())).thenReturn(Optional.of("u-100"));
    com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding instance =
        instanceBinding("ext-inst-1");
    instance.setMappingId("m-a");
    when(instanceBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-inst-1"))
        .thenReturn(Optional.of(instance));

    List<FlowTaskDetail> merged = routed.getTaskList("user-1", 0, 100);

    // 三源合并：内置 1 + 外部源 A 两页 2 条（续拉不遗漏）+ 外部源 B 空页不干扰
    assertThat(merged).hasSize(3);
    assertThat(merged).extracting(FlowTaskDetail::getId)
        .contains("in-1", "local-ext-task-1", "local-ext-task-2");
    verify(readerA, org.mockito.Mockito.times(2)).queryTasks(any(ExternalTaskQuery.class));
    verify(emptyReader, org.mockito.Mockito.times(1)).queryTasks(any(ExternalTaskQuery.class));
  }

  private FlowTaskDetail internalDetail(String id) {
    FlowTaskDetail detail = new FlowTaskDetail();
    detail.setId(id);
    detail.setFlowKey("flow-a");
    return detail;
  }

  private ExternalTaskSnapshot snapshot(String externalTaskId) {
    return new ExternalTaskSnapshot(externalTaskId, "node-1", "ext-user", "RUNNING",
        "http://external/task/" + externalTaskId, Map.of("externalInstanceId", "ext-inst-1"));
  }

  @Test
  void getTaskFormFailsWhenInstanceBindingMissing() {
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(taskBinding("t-1")));
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));
    when(instanceBindingService.findByLocalId("local-inst-1")).thenReturn(Optional.empty());
    FakeReaderAdapter adapter = new FakeReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(adapter)));

    Try<SchemaNode> result = routed.getTaskForm("t-1");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_BINDING_MISSING);
  }

  @Test
  void getTaskFormUsesResolvedExternalInstanceId() {
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(taskBinding("t-1")));
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));
    when(instanceBindingService.findByLocalId("local-inst-1")).thenReturn(Optional.of(instanceBinding("ext-9")));
    FakeReaderAdapter adapter = new FakeReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(adapter)));

    Try<SchemaNode> result = routed.getTaskForm("t-1");

    assertThat(result.isSuccess()).isTrue();
    assertThat(adapter.lastTaskRef).isNotNull();
    assertThat(adapter.lastTaskRef.externalTaskId()).isEqualTo("ext-task-1");
    assertThat(adapter.lastTaskRef.externalInstanceId()).isEqualTo("ext-9");
  }

  @Test
  void remindFailsWhenInstanceBindingMissing() {
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(taskBinding("t-1")));
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));
    when(instanceBindingService.findByLocalId("local-inst-1")).thenReturn(Optional.empty());
    FakeActionAdapter adapter = new FakeActionAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(adapter)));

    Try<Void> result = routed.remind("t-1", "user-1");

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_BINDING_MISSING);
    assertThat(adapter.commands).isEmpty();
  }

  @Test
  void remindUsesResolvedExternalInstanceId() {
    when(taskBindingService.findByLocalId("t-1")).thenReturn(Optional.of(taskBinding("t-1")));
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));
    when(instanceBindingService.findByLocalId("local-inst-1")).thenReturn(Optional.of(instanceBinding("ext-9")));
    when(actionExecutionService.executeTask(any(), any(), any())).thenReturn(Try.success(null));

    Try<Void> result = routed.remind("t-1", "user-1");

    assertThat(result.isSuccess()).isTrue();
    org.mockito.ArgumentCaptor<ExternalActionRequest> requestCaptor =
        org.mockito.ArgumentCaptor.forClass(ExternalActionRequest.class);
    verify(actionExecutionService).executeTask(any(), requestCaptor.capture(), any());
    ExternalActionRequest request = requestCaptor.getValue();
    assertThat(request.canonicalAction()).isEqualTo("remind");
    assertThat(request.externalAction()).isEqualTo("remind");
    assertThat(request.externalInstanceRef().externalInstanceId()).isEqualTo("ext-9");
    assertThat(request.externalTaskRef().externalInstanceId()).isEqualTo("ext-9");
  }

  private com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding instanceBinding(String externalInstanceId) {
    com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding binding =
        new com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding();
    binding.setLocalInstanceId("local-inst-1");
    binding.setExternalInstanceId(externalInstanceId);
    binding.setScope("default");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    return binding;
  }

  @Test
  void getTaskCountAddsExternalAggregation() {
    when(internal.getTaskCount("user-1")).thenReturn(3L);
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    FakeReaderAdapter adapter = new FakeReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        adapter, Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(taskBindingService.findByExternalTask(any(), any(), any())).thenReturn(Optional.empty());

    Long count = routed.getTaskCount("user-1");

    assertThat(count).isEqualTo(4L);
    verify(taskBindingService, never()).create(any());
    verify(pendingAssignmentService, never()).recordMissingMapping(any(), any(), anyString());
  }

  @Test
  void getTaskCountExcludesUnmappedExternalAssigneeWithoutWritingPendingQueue() {
    when(internal.getTaskCount("user-1")).thenReturn(3L);
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    FakeReaderAdapter adapter = new FakeReaderAdapter();
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        adapter, Set.of(Capability.EXTERNAL_TASK_QUERY))));
    when(identityResolver.toPlatformUser("ext-user", "fake-adapter", "conn-1"))
        .thenReturn(Optional.empty());

    Long count = routed.getTaskCount("user-1");

    assertThat(count).isEqualTo(3L);
    verify(taskBindingService, never()).create(any());
    verify(pendingAssignmentService, never()).recordMissingMapping(any(), any(), anyString());
  }

  @Test
  void getTaskCountUsesCursorUntilExternalSourceIsExhausted() {
    when(internal.getTaskCount("user-1")).thenReturn(3L);
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.queryTasks(new ExternalTaskQuery("user-1", "conn-1", null, 1000)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(snapshot("ext-task-1"), snapshot("ext-task-2")), true, "c1")));
    when(reader.queryTasks(new ExternalTaskQuery("user-1", "conn-1", "c1", 1000)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(snapshot("ext-task-3")), false, null)));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader), Set.of(Capability.EXTERNAL_TASK_QUERY))));

    Long count = routed.getTaskCount("user-1");

    assertThat(count).isEqualTo(6L);
    verify(reader).queryTasks(new ExternalTaskQuery("user-1", "conn-1", null, 1000));
    verify(reader).queryTasks(new ExternalTaskQuery("user-1", "conn-1", "c1", 1000));
    verify(taskBindingService, never()).create(any());
  }

  @Test
  void getTaskCountFailsWhenExternalSourceHasMoreWithoutCursor() {
    when(internal.getTaskCount("user-1")).thenReturn(3L);
    WorkflowEngineMapping mapping = fullProcessMapping("m-1", "flow-a", "fake-adapter");
    when(mappingService.findActiveByScope("default")).thenReturn(List.of(mapping));
    ExternalTaskReader reader = mock(ExternalTaskReader.class);
    when(reader.queryTasks(new ExternalTaskQuery("user-1", "conn-1", null, 1000)))
        .thenReturn(Try.success(new ExternalTaskPage(List.of(snapshot("ext-task-1")), true, null)));
    when(registry.getAdapter("fake-adapter")).thenReturn(Optional.of(registered(
        new DelegatingReaderAdapter(reader), Set.of(Capability.EXTERNAL_TASK_QUERY))));

    assertThatThrownBy(() -> routed.getTaskCount("user-1"))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
    verify(taskBindingService, never()).create(any());
  }

  @Test
  void getAllActiveTaskListIncludesExternalBindings() {
    FlowTaskDetail internalDetail = new FlowTaskDetail();
    internalDetail.setId("in-1");
    when(internal.getAllActiveTaskList()).thenReturn(List.of(internalDetail));
    ExternalTaskBinding binding = new ExternalTaskBinding();
    binding.setLocalTaskId("lt-1");
    binding.setLocalInstanceId("local-inst-1");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAssignee("u-100");
    binding.setStatus(ExternalTaskBinding.STATUS_PROCESSING);
    when(taskBindingService.findActive("default")).thenReturn(List.of(binding));
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(fullProcessMapping("m-1", "flow-a", "fake-adapter")));

    List<FlowTaskDetail> merged = routed.getAllActiveTaskList();

    assertThat(merged).hasSize(2);
    FlowTaskDetail external = merged.stream()
        .filter(detail -> "lt-1".equals(detail.getId()))
        .findFirst().orElseThrow();
    assertThat(external.getInstanceId()).isEqualTo("local-inst-1");
    assertThat(external.getAssignee()).isEqualTo("u-100");
    assertThat(external.getFlowKey()).isEqualTo("flow-a");
  }

  private ExternalTaskBinding taskBinding(String localTaskId) {
    ExternalTaskBinding binding = new ExternalTaskBinding();
    binding.setLocalTaskId(localTaskId);
    binding.setLocalInstanceId("local-inst-1");
    binding.setScope("default");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setExternalTaskId("ext-task-1");
    binding.setAssignee("user-1");
    binding.setStatus(ExternalTaskBinding.STATUS_CREATED);
    return binding;
  }

  private WorkflowEngineMapping fullProcessMapping(String id, String flowKey, String adapterId) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId(id);
    mapping.setScope("default");
    mapping.setFlowKey(flowKey);
    mapping.setVersion(1);
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    mapping.setAdapterId(adapterId);
    mapping.setConnectionId("conn-1");
    mapping.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    return mapping;
  }

  private RegisteredAdapter registered(ApprovalWorkflowEngineAdapter adapter) {
    return registered(adapter, Set.of());
  }

  private RegisteredAdapter registered(
      ApprovalWorkflowEngineAdapter adapter, Set<Capability> capabilities) {
    return ConnectionAwareAdapterResolverTestSupport.registered(
        new ApprovalWorkflowEngineAdapterDescriptor("fake-adapter", "测试适配器", "1.0", Set.of("approve", "reject"), Set.of(), Set.of()),
        adapter, capabilities);
  }

  /** 测试适配器：记录动作执行命令。 */
  static final class FakeActionAdapter implements ApprovalWorkflowEngineAdapter, ExternalWorkflowActionExecutor {
    final List<ExternalActionCommand> commands = new java.util.ArrayList<>();

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalActionResult> execute(ExternalActionCommand command) {
      commands.add(command);
      return Try.success(new ExternalActionResult(true, "APPROVED", "approved", null, null));
    }
  }

  /** 测试适配器：ExternalTaskReader 委托给注入的 reader（游标多页场景）。 */
  static final class DelegatingReaderAdapter implements ApprovalWorkflowEngineAdapter, ExternalTaskReader {
    private final ExternalTaskReader delegate;

    DelegatingReaderAdapter(ExternalTaskReader delegate) {
      this.delegate = delegate;
    }

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalTaskPage> queryTasks(ExternalTaskQuery query) {
      return delegate.queryTasks(query);
    }

    @Override
    public Try<ExternalTaskSnapshot> getTask(ExternalTaskRef task) {
      return delegate.getTask(task);
    }

    @Override
    public Try<ExternalFormSnapshot> getTaskForm(ExternalTaskRef task) {
      return delegate.getTaskForm(task);
    }
  }

  @Test
  void unconfiguredScopeFallsBackToInternalTasks() {
    when(scopeResolver.resolve())
        .thenThrow(new WorkflowScopeUnconfiguredException("未装配"));
    when(internal.getTaskList(anyString(), any(), anyInt(), anyInt()))
        .thenReturn(List.of(taskDetail("t-internal")));
    when(internal.getTaskCount("u-1")).thenReturn(1L);
    when(internal.getAllActiveTaskList()).thenReturn(List.of(taskDetail("t-internal")));

    assertThat(routed.getTaskList("u-1")).extracting(FlowTaskDetail::getId)
        .containsExactly("t-internal");
    assertThat(routed.getTaskCount("u-1")).isEqualTo(1L);
    assertThat(routed.getAllActiveTaskList()).extracting(FlowTaskDetail::getId)
        .containsExactly("t-internal");
    assertThat(routed.getTaskList("u-1", "flow-a")).extracting(FlowTaskDetail::getId)
        .containsExactly("t-internal");
    verify(mappingService, never()).findActiveByScope(anyString());
    verify(mappingService, never()).findByScopeAndFlowKey(anyString(), anyString());
    verify(taskBindingService, never()).findActive(anyString());
  }

  private static FlowTaskDetail taskDetail(String id) {
    FlowTaskDetail detail = new FlowTaskDetail();
    detail.setId(id);
    return detail;
  }

  /** 测试适配器：返回固定待办快照，并记录最近一次任务引用。 */
  static final class FakeReaderAdapter implements ApprovalWorkflowEngineAdapter, ExternalTaskReader {
    ExternalTaskRef lastTaskRef;

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalTaskPage> queryTasks(ExternalTaskQuery query) {
      return Try.success(new ExternalTaskPage(List.of(
          new ExternalTaskSnapshot("ext-task-9", "node-1", "ext-user", "RUNNING",
              "http://external/task/9", Map.of("externalInstanceId", "ext-inst-1"))), false, null));
    }

    @Override
    public Try<ExternalTaskSnapshot> getTask(ExternalTaskRef task) {
      lastTaskRef = task;
      return Try.failure(new IllegalStateException("not used"));
    }

    @Override
    public Try<ExternalFormSnapshot> getTaskForm(ExternalTaskRef task) {
      lastTaskRef = task;
      return Try.success(new ExternalFormSnapshot(Map.of("amount", 100)));
    }
  }
}
