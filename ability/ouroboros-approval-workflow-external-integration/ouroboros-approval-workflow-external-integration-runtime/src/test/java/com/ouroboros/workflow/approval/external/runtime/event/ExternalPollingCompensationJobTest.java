package com.ouroboros.workflow.approval.external.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;

import io.vavr.control.Try;

/**
 * M9 轮询补偿测试：增量查询 → 合成信封 → 同一 inbox 管道；同步状态游标更新。
 */
class ExternalPollingCompensationJobTest {

  private WorkflowEngineAdapterRegistry registry;
  private WorkflowEngineMappingService mappingService;
  private ExternalInstanceBindingService instanceBindingService;
  private ExternalSyncStateService syncStateService;
  private ExternalEventIngestService ingestService;
  private ExternalPollingCompensationJob job;
  private PollingReaderAdapter adapter;
  private ExternalSyncState claimedState;

  @BeforeEach
  void setUp() {
    registry = new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    adapter = new PollingReaderAdapter();
    registry.initialize(List.of(new ProviderFor(adapter)));
    mappingService = mock(WorkflowEngineMappingService.class);
    instanceBindingService = mock(ExternalInstanceBindingService.class);
    syncStateService = mock(ExternalSyncStateService.class);
    ingestService = mock(ExternalEventIngestService.class);
    when(ingestService.ingest(any())).thenReturn(Try.success(null));
    when(syncStateService.beginRun(any(), any(), any(), any())).thenReturn(true);
    when(syncStateService.recordPage(any(), any(), any())).thenReturn(true);
    when(syncStateService.completeRun(any(), any(), any())).thenReturn(true);
    when(syncStateService.release(any(), any())).thenReturn(true);
    job = new ExternalPollingCompensationJob(
        registry, ConnectionAwareAdapterResolverTestSupport.resolver(registry),
        mappingService, instanceBindingService, syncStateService, ingestService);

    claimedState = state();
    when(syncStateService.claim(eq("poll-adapter"), eq("conn-1"), any(String.class), eq(300)))
        .thenReturn(Optional.of(new ExternalSyncStateService.Claim(
            claimedState, "sync-token")));
    when(instanceBindingService.searchRunningPage(null, 200)).thenReturn(List.of());
  }

  @Test
  void pollSynthesizesEnvelopesIntoInboxPipeline() {
    when(mappingService.findActiveAllForSystem()).thenReturn(List.of(mapping()));
    adapter.page = new ExternalInstancePage(List.of(
        new ExternalInstanceSnapshot("ext-1", "FINISHED", "APPROVED", "approved", null, Map.of()),
        new ExternalInstanceSnapshot("ext-2", "RUNNING", null, null, null, Map.of())), null);

    job.run();

    ArgumentCaptor<ExternalCallbackEnvelope> captor = ArgumentCaptor.forClass(ExternalCallbackEnvelope.class);
    verify(ingestService, times(2)).ingest(captor.capture());
    assertThat(captor.getAllValues()).anySatisfy(e -> {
      assertThat(e.resultCode()).isEqualTo("APPROVED");
      assertThat(e.eventType()).isEqualTo("FINISHED");
    });
    verify(syncStateService).completeRun(
        eq("poll-adapter:conn-1"), eq("sync-token"), any(LocalDateTime.class));
  }

  @Test
  void pollSkipsAdapterWithoutPollingCapability() {
    registry.initialize(List.of(new NoPollingProvider()));
    when(mappingService.findActiveAllForSystem()).thenReturn(List.of(mapping()));

    job.run();

    verify(ingestService, never()).ingest(any());
  }

  @Test
  void pollRejectsSnapshotsWithoutStatus() {
    when(mappingService.findActiveAllForSystem()).thenReturn(List.of(mapping()));
    adapter.page = new ExternalInstancePage(List.of(
        new ExternalInstanceSnapshot("ext-1", null, null, null, null, Map.of())), null);

    job.run();

    verify(ingestService, never()).ingest(any());
    verify(syncStateService, never()).recordPage(any(), any(), any());
    verify(syncStateService, never()).completeRun(any(), any(), any());
  }

  @Test
  void pollDoesNotAdvanceCursorWhenAnyPageEventFailsToIngest() {
    when(mappingService.findActiveAllForSystem()).thenReturn(List.of(mapping()));
    adapter.page = new ExternalInstancePage(List.of(
        new ExternalInstanceSnapshot("ext-1", "FINISHED", "APPROVED", "approved", null, Map.of()),
        new ExternalInstanceSnapshot("ext-2", "RUNNING", null, null, null, Map.of())), "cursor-2");
    when(ingestService.ingest(any()))
        .thenReturn(Try.success(null))
        .thenReturn(Try.failure(new IllegalStateException("inbox down")));

    job.run();

    verify(ingestService, times(2)).ingest(any());
    verify(syncStateService, never()).recordPage(any(), any(), any());
    verify(syncStateService, never()).completeRun(any(), any(), any());
  }

  @Test
  void subsequentPageKeepsTheSameWindowUntilCursorIsExhausted() {
    when(mappingService.findActiveAllForSystem()).thenReturn(List.of(mapping()));
    ExternalInstancePage first = new ExternalInstancePage(List.of(
        new ExternalInstanceSnapshot("ext-1", "RUNNING", null, null, null, Map.of())), "cursor-2");
    ExternalInstancePage second = new ExternalInstancePage(List.of(
        new ExternalInstanceSnapshot("ext-2", "FINISHED", "APPROVED", "approved", null, Map.of())), null);
    adapter.page = first;
    when(syncStateService.recordPage(any(), any(), eq("cursor-2"))).thenAnswer(invocation -> {
      claimedState.setCursor("cursor-2");
      return true;
    });
    when(syncStateService.completeRun(any(), any(), any())).thenReturn(true);

    job.run();
    adapter.page = second;
    job.run();

    assertThat(adapter.queries).hasSize(2);
    assertThat(adapter.queries.get(0).changedFrom())
        .isEqualTo(adapter.queries.get(1).changedFrom());
    assertThat(adapter.queries.get(0).changedUntil())
        .isEqualTo(adapter.queries.get(1).changedUntil());
    assertThat(adapter.queries.get(0).cursor()).isNull();
    assertThat(adapter.queries.get(1).cursor()).isEqualTo("cursor-2");
    verify(syncStateService).completeRun(eq("poll-adapter:conn-1"), eq("sync-token"), any());
  }

  @Test
  void retiredMappingReferencedByRunningBindingStillParticipatesInPolling() {
    WorkflowEngineMapping retired = mapping();
    retired.setStatus(WorkflowEngineMapping.STATUS_RETIRED);
    retired.setVersion(2);
    retired.setConnectionId("retired-conn");
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("inst-1");
    binding.setMappingId("m-1");
    binding.setMappingVersion(2);
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    when(mappingService.findActiveAllForSystem()).thenReturn(List.of());
    when(instanceBindingService.searchRunningPage(null, 200)).thenReturn(List.of(binding));
    when(instanceBindingService.searchRunningPage("inst-1", 200)).thenReturn(List.of());
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(retired));
    ExternalSyncState retiredState = state();
    retiredState.setId("poll-adapter:retired-conn");
    retiredState.setConnectionId("retired-conn");
    when(syncStateService.claim(eq("poll-adapter"), eq("retired-conn"), any(String.class), eq(300)))
        .thenReturn(Optional.of(new ExternalSyncStateService.Claim(retiredState, "retired-token")));
    adapter.page = new ExternalInstancePage(List.of(
        new ExternalInstanceSnapshot("ext-1", "FINISHED", "APPROVED", "approved", null, Map.of())), null);

    job.run();

    verify(syncStateService).claim(eq("poll-adapter"), eq("retired-conn"), any(String.class), eq(300));
    verify(ingestService).ingest(any());
  }

  private ExternalSyncState state() {
    ExternalSyncState state = new ExternalSyncState();
    state.setId("poll-adapter:conn-1");
    state.setAdapterId("poll-adapter");
    state.setConnectionId("conn-1");
    state.setLastSyncAt(LocalDateTime.now().minusMinutes(5));
    return state;
  }

  private WorkflowEngineMapping mapping() {
    WorkflowEngineMapping m = new WorkflowEngineMapping();
    m.setId("m-1");
    m.setFlowKey("flow-a");
    m.setAdapterId("poll-adapter");
    m.setConnectionId("conn-1");
    m.setAuthority("EXTERNAL_ENGINE");
    m.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    m.setVersion(1);
    return m;
  }

  static final class ProviderFor implements ApprovalWorkflowEngineAdapterProvider {
    private final PollingReaderAdapter adapter;

    ProviderFor(PollingReaderAdapter adapter) {
      this.adapter = adapter;
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("poll-adapter", "poll", "1.0", Set.of(), Set.of(Capability.POLLING, Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return adapter;
    }
  }

  static final class NoPollingProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("poll-adapter", "poll", "1.0", Set.of(), Set.of(), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new ApprovalWorkflowEngineAdapter() {
        @Override
        public Try<ExternalHealth> health() {
          return Try.success(new ExternalHealth(true, "ok"));
        }
      };
    }
  }

  static final class PollingReaderAdapter implements ApprovalWorkflowEngineAdapter, ExternalInstanceReader {
    ExternalInstancePage page = new ExternalInstancePage(List.of(), null);
    List<ExternalInstanceQuery> queries = new ArrayList<>();

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
      return Try.success(new ExternalInstanceSnapshot(
          instance.externalInstanceId(), "RUNNING", null, null, null, Map.of()));
    }

    @Override
    public Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query) {
      queries.add(query);
      return Try.success(page);
    }
  }
}
