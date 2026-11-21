package com.ouroboros.workflow.approval.external.runtime.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryResult;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStartRecovery;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;

import io.vavr.control.Try;

class ExternalStartRecoveryServiceTest {

  @Test
  void recoveredRemoteInstanceIsAdoptedUsingTheStableRequestIdentity() {
    ExternalStartFactService factService = mock(ExternalStartFactService.class);
    WorkflowEngineMappingService mappingService = mock(WorkflowEngineMappingService.class);
    ConnectionAwareAdapterResolver adapterResolver = mock(ConnectionAwareAdapterResolver.class);
    ExternalResultBindingService resultBindingService = mock(ExternalResultBindingService.class);
    InstanceEventDispatcher eventDispatcher = mock(InstanceEventDispatcher.class);
    ExternalWorkflowStartRecovery recovery = mock(ExternalWorkflowStartRecovery.class);

    WorkflowEngineMapping mapping = mapping();
    when(mappingService.findByIdForSystem("mapping-1")).thenReturn(Optional.of(mapping));
    when(adapterResolver.resolvePort(eq(mapping), eq(ExternalWorkflowStartRecovery.class)))
        .thenReturn(recovery);
    when(recovery.recover(any())).thenReturn(Try.success(ExternalStartRecoveryResult.found(
        new ExternalStartResult("external-1", "https://external/1", null))));

    ExternalStartFact claimedFact = fact("fact-1", "claim-1", ExternalStartFact.PHASE_IN_FLIGHT);
    ExternalStartFact adoptedFact = fact("fact-1", "claim-2", ExternalStartFact.PHASE_REPLIED);
    adoptedFact.setExternalInstanceId("external-1");
    adoptedFact.setExternalUrl("https://external/1");
    when(factService.adoptLateResponse(
        "fact-1", "request-fingerprint", "external-1", "https://external/1", 300))
        .thenReturn(Optional.of(new ExternalStartFactService.RecoveryClaim(adoptedFact, "claim-2")));
    when(resultBindingService.create(any())).thenAnswer(invocation -> Try.success(invocation.getArgument(0)));
    when(factService.markStarted("fact-1", "claim-2", "tracking-1")).thenReturn(true);

    ExternalStartRecoveryService service = new ExternalStartRecoveryService(
        factService, mappingService, adapterResolver, mock(
            com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService.class),
        resultBindingService, eventDispatcher, null);

    Try<String> result = service.recover(new ExternalStartFactService.RecoveryClaim(claimedFact, "claim-1"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo("tracking-1");
    var query = org.mockito.ArgumentCaptor.forClass(
        com.ouroboros.workflow.approval.external.ExternalStartRecoveryQuery.class);
    verify(recovery).recover(query.capture());
    assertThat(query.getValue().requestFingerprint()).isEqualTo("request-fingerprint");
    assertThat(query.getValue().idempotencyKey()).isEqualTo("idempotency-1");
    verify(factService).adoptLateResponse(
        "fact-1", "request-fingerprint", "external-1", "https://external/1", 300);
    verify(factService).markStarted("fact-1", "claim-2", "tracking-1");
  }

  @Test
  void adapterWithoutRecoveryPortRemainsUnknownAndDoesNotStartAgain() {
    ExternalStartFactService factService = mock(ExternalStartFactService.class);
    WorkflowEngineMappingService mappingService = mock(WorkflowEngineMappingService.class);
    ConnectionAwareAdapterResolver adapterResolver = mock(ConnectionAwareAdapterResolver.class);
    WorkflowEngineMapping mapping = mapping();
    when(mappingService.findByIdForSystem("mapping-1")).thenReturn(Optional.of(mapping));
    when(adapterResolver.resolvePort(eq(mapping), eq(ExternalWorkflowStartRecovery.class)))
        .thenThrow(new BusinessException("端口不存在"));

    ExternalStartFact claimedFact = fact("fact-1", "claim-1", ExternalStartFact.PHASE_IN_FLIGHT);
    when(factService.markUnknown(eq("fact-1"), eq("claim-1"), any(), any(), any())).thenReturn(true);

    ExternalStartRecoveryService service = new ExternalStartRecoveryService(
        factService, mappingService, adapterResolver, mock(
            com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService.class),
        mock(ExternalResultBindingService.class), mock(InstanceEventDispatcher.class), null);

    Try<String> result = service.recover(new ExternalStartFactService.RecoveryClaim(claimedFact, "claim-1"));

    assertThat(result.isFailure()).isTrue();
    verify(factService).markUnknown(eq("fact-1"), eq("claim-1"), any(), any(),
        org.mockito.ArgumentMatchers.contains("未声明可靠的外部启动结果恢复能力"));
    verify(adapterResolver, never()).resolvePort(eq(mapping), eq(ExternalWorkflowStarter.class));
    verify(factService, never()).markNotStartedForRecovery(any(), any());
  }

  @Test
  void confirmedRemoteAbsenceResetsTheSameFactBeforeRetryingStart() {
    ExternalStartFactService factService = mock(ExternalStartFactService.class);
    WorkflowEngineMappingService mappingService = mock(WorkflowEngineMappingService.class);
    ConnectionAwareAdapterResolver adapterResolver = mock(ConnectionAwareAdapterResolver.class);
    ExternalWorkflowStartRecovery recovery = mock(ExternalWorkflowStartRecovery.class);
    ExternalWorkflowStarter starter = mock(ExternalWorkflowStarter.class);
    WorkflowEngineMapping mapping = mapping();
    when(mappingService.findByIdForSystem("mapping-1")).thenReturn(Optional.of(mapping));
    when(adapterResolver.resolvePort(eq(mapping), eq(ExternalWorkflowStartRecovery.class)))
        .thenReturn(recovery);
    when(adapterResolver.resolvePort(eq(mapping), eq(ExternalWorkflowStarter.class)))
        .thenReturn(starter);
    when(recovery.recover(any())).thenReturn(Try.success(ExternalStartRecoveryResult.notFound("未发现远端实例")));
    when(factService.markNotStartedForRecovery("fact-1", "claim-1")).thenReturn(true);
    when(factService.markInFlight("fact-1", "claim-1")).thenReturn(true);
    when(starter.start(any())).thenReturn(Try.failure(
        ExternalOperationFailure.definitelyNotApplied("测试重发失败", null)));
    when(factService.markFailed(eq("fact-1"), eq("claim-1"), any())).thenReturn(true);

    ExternalStartFact claimedFact = fact("fact-1", "claim-1", ExternalStartFact.PHASE_IN_FLIGHT);
    ExternalStartRecoveryService service = new ExternalStartRecoveryService(
        factService, mappingService, adapterResolver, mock(
            com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService.class),
        mock(ExternalResultBindingService.class), mock(InstanceEventDispatcher.class), null);

    Try<String> result = service.recover(new ExternalStartFactService.RecoveryClaim(claimedFact, "claim-1"));

    assertThat(result.isFailure()).isTrue();
    verify(factService).markNotStartedForRecovery("fact-1", "claim-1");
    verify(starter).start(any());
    verify(factService).markFailed(eq("fact-1"), eq("claim-1"), any());
  }

  @Test
  void lateRemoteSuccessAfterRecoveryLeaseLossIsAdoptedBeforeBindingPersistence() {
    ExternalStartFactService factService = mock(ExternalStartFactService.class);
    WorkflowEngineMappingService mappingService = mock(WorkflowEngineMappingService.class);
    ConnectionAwareAdapterResolver adapterResolver = mock(ConnectionAwareAdapterResolver.class);
    ExternalResultBindingService resultBindingService = mock(ExternalResultBindingService.class);
    ExternalResultBinding existingBinding = null;
    InstanceEventDispatcher eventDispatcher = mock(InstanceEventDispatcher.class);
    ExternalWorkflowStarter starter = mock(ExternalWorkflowStarter.class);

    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("mapping-1");
    mapping.setVersion(1);
    mapping.setAdapterId("adapter-1");
    mapping.setConnectionId("connection-1");
    mapping.setExternalDefinitionId("definition-1");
    when(mappingService.findByIdForSystem("mapping-1")).thenReturn(Optional.of(mapping));
    when(adapterResolver.resolvePort(eq(mapping), eq(ExternalWorkflowStarter.class))).thenReturn(starter);
    when(starter.start(any())).thenReturn(Try.success(
        new ExternalStartResult("external-1", "https://external/1", null)));

    ExternalStartFact claimedFact = fact("fact-1", "old-claim", ExternalStartFact.PHASE_NOT_STARTED);
    ExternalStartFact adoptedFact = fact("fact-1", "new-claim", ExternalStartFact.PHASE_REPLIED);
    adoptedFact.setExternalInstanceId("external-1");
    adoptedFact.setExternalUrl("https://external/1");
    adoptedFact.setApprovalCaseId("case-1");
    adoptedFact.setSubmissionRound(2);
    ExternalStartFactService.RecoveryClaim claim =
        new ExternalStartFactService.RecoveryClaim(claimedFact, "old-claim");
    when(factService.markInFlight("fact-1", "old-claim")).thenReturn(true);
    when(factService.markReplied("fact-1", "old-claim", "external-1", "https://external/1"))
        .thenReturn(false);
    when(factService.adoptLateResponse(
        "fact-1", "request-fingerprint", "external-1", "https://external/1", 300))
        .thenReturn(Optional.of(new ExternalStartFactService.RecoveryClaim(adoptedFact, "new-claim")));
    when(resultBindingService.findByExternalInstance("adapter-1", "connection-1", "external-1"))
        .thenReturn(Optional.ofNullable(existingBinding));
    when(resultBindingService.create(any())).thenAnswer(invocation -> Try.success(invocation.getArgument(0)));
    when(factService.markStarted("fact-1", "new-claim", "tracking-1")).thenReturn(true);
    ApprovalCaseService caseService = mock(ApprovalCaseService.class);

    ExternalStartRecoveryService service = new ExternalStartRecoveryService(
        factService, mappingService, adapterResolver, mock(
            com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService.class),
        resultBindingService, eventDispatcher, null, providerOf(caseService));

    Try<String> result = service.recover(claim);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get()).isEqualTo("tracking-1");
    verify(factService).adoptLateResponse(
        "fact-1", "request-fingerprint", "external-1", "https://external/1", 300);
    verify(factService).markStarted("fact-1", "new-claim", "tracking-1");
    verify(caseService).bindExecution("case-1", 2, "tracking-1");
    verify(factService, never()).markUnknown(any(), any(), any(), any(), any());
  }

  private WorkflowEngineMapping mapping() {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("mapping-1");
    mapping.setVersion(1);
    mapping.setAdapterId("adapter-1");
    mapping.setConnectionId("connection-1");
    mapping.setExternalDefinitionId("definition-1");
    return mapping;
  }

  private ExternalStartFact fact(String id, String claimToken, String phase) {
    ExternalStartFact fact = new ExternalStartFact();
    fact.setId(id);
    fact.setScope("default");
    fact.setFlowKey("flow-a");
    fact.setBusinessRecordType("supplier");
    fact.setBusinessRecordId("sup-1");
    fact.setIdempotencyKey("idempotency-1");
    fact.setRequestFingerprint("request-fingerprint");
    fact.setMappingId("mapping-1");
    fact.setMappingVersion(1);
    fact.setAdapterId("adapter-1");
    fact.setConnectionId("connection-1");
    fact.setIntegrationMode(ExternalStartFact.MODE_RESULT_ONLY);
    fact.setTrackingId("tracking-1");
    fact.setActorUserId("user-1");
    fact.setRequestJson("{\"originatorUserId\":\"user-1\",\"idempotencyKey\":\"idempotency-1\"}");
    fact.setStatus(ExternalStartFact.STATUS_STARTING);
    fact.setRemoteCallPhase(phase);
    fact.setClaimToken(claimToken);
    return fact;
  }

  private <T> org.springframework.beans.factory.ObjectProvider<T> providerOf(T value) {
    return new org.springframework.beans.factory.ObjectProvider<T>() {
      @Override
      public T getObject() {
        return value;
      }

      @Override
      public java.util.stream.Stream<T> stream() {
        return java.util.stream.Stream.of(value);
      }
    };
  }
}
