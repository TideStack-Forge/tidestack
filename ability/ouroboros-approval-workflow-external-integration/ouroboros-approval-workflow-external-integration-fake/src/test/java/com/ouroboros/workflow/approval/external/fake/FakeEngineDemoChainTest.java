package com.ouroboros.workflow.approval.external.fake;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolverTestSupport;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventInbox;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventInboxService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventProcessor;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalApprovalStartServiceImpl;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFact;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartCommand;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartResult;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;

import io.vavr.control.Try;

/**
 * M16-T3 灰度链路契约锚点（不依赖数据库/网络）：fake 适配器
 * 发起 → 绑定 → 回调入箱 → 处理器终态闭环 → 统一结果事件。
 *
 * <p>作为 M19 前端联调的契约锚点：前端对 fake-external-engine 的
 * "配置→激活→发起→simulate-callback→跟踪" 必须与此链路的语义一致。
 */
class FakeEngineDemoChainTest {

  /** 结果回流模式的产品级 fake provider 变体（descriptor 只声明 RESULT_ONLY）。 */
  static final class ResultOnlyFakeProvider
      implements com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider {

    private final FakeEngineAdapterProvider delegate = new FakeEngineAdapterProvider();

    @Override
    public String provider() {
      return delegate.provider();
    }

    @Override
    public com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      var original = delegate.descriptor();
      return new com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor(
          original.adapterId(), original.displayName(), original.version(), original.canonicalActions(),
          original.extraCapabilities(), original.disabledActions());
    }

    @Override
    public com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter create(
        com.ouroboros.enterprise.ResolvedEnterpriseConnection connection) {
      return delegate.create(connection);
    }
  }

  @Test
  void resultOnlyChainClosesWithUnifiedEvent() {
    WorkflowEngineAdapterRegistry registry =
        new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    // 本用例走结果型启动入口（ExternalApprovalStartServiceImpl 只服务派生模式 RESULT_ONLY），
    // 因此注册一个只声明结果回流模式的 provider 变体；接入模式由端口能力派生，不受 descriptor 自述影响。
    registry.initialize(List.of(new ResultOnlyFakeProvider()));

    WorkflowEngineMappingService mappingService = mock(WorkflowEngineMappingService.class);
    WorkflowEngineMapping mapping = mapping("flow-a");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping));
    when(mappingService.findById("default", "m-1")).thenReturn(Optional.of(mapping));
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mapping));

    ExternalResultBindingService resultBindingService = mock(ExternalResultBindingService.class);
    when(resultBindingService.findByIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
    when(resultBindingService.findRunningByBusinessRecord(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Map<String, ExternalResultBinding> created = new LinkedHashMap<>();
    when(resultBindingService.create(any())).thenAnswer(inv -> {
      ExternalResultBinding binding = inv.getArgument(0);
      created.put(binding.getTrackingId(), binding);
      return Try.success(binding);
    });

    InstanceEventDispatcher startDispatcher = mock(InstanceEventDispatcher.class);
    WorkflowScopeResolver scopeResolver = mock(WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("default");
    ExternalStartFactService startFactService = mock(ExternalStartFactService.class);
    ExternalStartFact startFact = new ExternalStartFact();
    startFact.setId("fact-1");
    startFact.setStatus(ExternalStartFact.STATUS_STARTING);
    startFact.setClaimToken("token-1");
    when(startFactService.reserve(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(inv -> {
          startFact.setTrackingId(inv.getArgument(7));
          startFact.setIntegrationMode(inv.getArgument(6));
          startFact.setFlowKey(mapping.getFlowKey());
          startFact.setActorUserId(inv.getArgument(8));
          startFact.setBusinessRecordType(inv.getArgument(2));
          startFact.setBusinessRecordId(inv.getArgument(3));
          return Try.success(new ExternalStartFactService.Reservation(startFact, true));
        });
    when(startFactService.findById("fact-1")).thenReturn(Optional.of(startFact));
    when(startFactService.markInFlight(any(), any())).thenReturn(true);
    when(startFactService.markReplied(any(), any(), any(), any())).thenReturn(true);
    when(startFactService.markStarted(any(), any(), any())).thenReturn(true);
    ExternalApprovalStartServiceImpl startService = new ExternalApprovalStartServiceImpl(
        mappingService, resultBindingService, startFactService, ConnectionAwareAdapterResolverTestSupport.resolver(registry),
        startDispatcher, scopeResolver, null);
    // registry 在生产由 Spring 注入；派生接入模式需要它。
    org.springframework.test.util.ReflectionTestUtils.setField(startService, "registry", registry);

    ResultOnlyStartResult started = startService.start(new ResultOnlyStartCommand(
        "flow-a", "supplier", "sup-1", "user-1", Map.of("amount", 100), "ik-1",
        null, null, null)).get();

    String trackingId = startFact.getTrackingId();
    assertThat(trackingId).isNotBlank();
    assertThat(started.trackingId()).isEqualTo(trackingId);
    assertThat(started.status()).isEqualTo(ExternalStartFact.STATUS_STARTED);
    assertThat(started.externalInstanceId()).startsWith("fake-");
    assertThat(created.get(trackingId).getStatus()).isEqualTo(ExternalResultBinding.STATUS_RUNNING);

    // ---- 回调入箱 → 处理器终态闭环 ----
    ExternalEventInbox row = inboxRow("evt-1", started.externalInstanceId(), "APPROVED");
    ExternalEventInboxService inboxService = mock(ExternalEventInboxService.class);
    when(inboxService.findByEventId("fake-external-engine", "conn-1", "evt-1"))
        .thenReturn(Optional.of(row));
    when(inboxService.findById(row.getId())).thenReturn(Optional.of(row));
    when(inboxService.claim(row.getId(), "external-event-processor", ExternalEventInboxService.DEFAULT_LEASE_SECONDS))
        .thenAnswer(inv -> {
          row.setStatus(ExternalEventInbox.STATUS_PROCESSING);
          row.setClaimToken("claim-1");
          row.setClaimUntil(LocalDateTime.now().plusSeconds(ExternalEventInboxService.DEFAULT_LEASE_SECONDS));
          return Optional.of(new ExternalEventInboxService.Claim(row, "claim-1"));
        });
    when(inboxService.markProcessed(row.getId(), "claim-1")).thenAnswer(inv -> {
      if (ExternalEventInbox.STATUS_PROCESSING.equals(row.getStatus())
          && "claim-1".equals(row.getClaimToken())) {
        row.setStatus(ExternalEventInbox.STATUS_PROCESSED);
        row.setClaimToken(null);
        row.setClaimUntil(null);
        return true;
      }
      return false;
    });
    when(resultBindingService.findByExternalInstance("fake-external-engine", "conn-1", started.externalInstanceId()))
        .thenReturn(Optional.of(created.get(trackingId)));
    when(resultBindingService.markFinished(any(), any(), any(), any(), any())).thenAnswer(inv -> {
      ExternalResultBinding binding = created.get(trackingId);
      binding.setStatus(ExternalResultBinding.STATUS_FINISHED);
      binding.setOutcome(inv.getArgument(1));
      return Try.success(true);
    });

    InstanceEventDispatcher eventDispatcher = mock(InstanceEventDispatcher.class);
    ExternalEventProcessor processor = new ExternalEventProcessor(
        inboxService, resultBindingService, mock(ExternalInstanceBindingService.class),
        mock(ExternalTaskBindingService.class), mappingService, eventDispatcher,
        mock(TaskEventDispatcher.class),
        mock(com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService.class));

    Try<Void> processed = processor.processByEnvelope(new ExternalCallbackEnvelope(
        "evt-1", "fake-external-engine", "conn-1", started.externalInstanceId(),
        null, null, null, null, "APPROVED", null));

    assertThat(processed.isSuccess()).isTrue();
    assertThat(row.getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    verify(resultBindingService).markFinished(trackingId, "approved", null, null, "evt-1");
    ArgumentCaptor<FlowInstanceEvent> eventCaptor = ArgumentCaptor.forClass(FlowInstanceEvent.class);
    verify(eventDispatcher).dispatchEvent(eventCaptor.capture());
    FlowInstanceEvent event = eventCaptor.getValue();
    assertThat(event.getEventType()).isEqualTo(EventType.APPROVED);
    assertThat(event.getInstanceId()).isEqualTo(trackingId);
    assertThat(event.getBusinessContext())
        .isEqualTo(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
  }

  private org.springframework.beans.factory.ObjectProvider<com.ouroboros.workflow.approval.TrustedFlowTaskActionService> taskServiceProvider() {
    return mock(org.springframework.beans.factory.ObjectProvider.class);
  }

  private WorkflowEngineMapping mapping(String flowKey) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("m-1");
    mapping.setScope("default");
    mapping.setFlowKey(flowKey);
    mapping.setAdapterId(FakeEngineAdapterProvider.ADAPTER_ID);
    mapping.setConnectionId("conn-1");
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    mapping.setExternalDefinitionId("DEMO_PROC");
    mapping.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    mapping.setVersion(1);
    mapping.setResultMappingsJson("""
        [
          {"externalStatus":"RUNNING","systemState":"PROCESSING"},
          {"externalStatus":"APPROVED","systemEvent":"APPROVED","outcome":"approved"},
          {"externalStatus":"REJECTED","systemEvent":"REJECTED","outcome":"rejected"},
          {"externalStatus":"CANCELED","systemEvent":"CANCELLED","outcome":"cancelled"},
          {"externalStatus":"ERROR","outcome":"failed"}
        ]
        """);
    return mapping;
  }

  private ExternalEventInbox inboxRow(String eventId, String externalInstanceId, String resultCode) {
    ExternalEventInbox row = new ExternalEventInbox();
    row.setId("row-" + eventId);
    row.setAdapterId(FakeEngineAdapterProvider.ADAPTER_ID);
    row.setConnectionId("conn-1");
    row.setEventId(eventId);
    row.setExternalInstanceId(externalInstanceId);
    row.setResultCode(resultCode);
    row.setStatus(ExternalEventInbox.STATUS_PENDING);
    row.setRetryCount(0);
    row.setReceivedAt(LocalDateTime.now());
    return row;
  }
}
