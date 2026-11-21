package com.ouroboros.rpc.execution.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.exception.RPCException;
import com.ouroboros.rpc.execution.RpcInvocationContext;
import com.ouroboros.rpc.execution.RpcInvocationExecutor;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.execution.RpcProtocolInvoker;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;
import com.ouroboros.rpc.runtime.service.RpcRuntimeStatusService;

class RuntimeGovernanceRpcInvocationInterceptorTest {

  @BeforeEach
  void setUp() {
    RPCModelDefinitionCenter.refresh();
  }

  @Test
  void invoke_appliesRuntimeOverridesBeforeProtocolInvocation() {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy(true, false, false, 520L, 3, null));
    AtomicLong timeoutSeen = new AtomicLong(-1L);
    AtomicLong retrySeen = new AtomicLong(-1L);

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new RpcProtocolInvoker() {
          @Override
          public boolean supports(RPCModelDefinition definition) {
            return definition != null && "MockType".equalsIgnoreCase(definition.getType());
          }

          @Override
          public RpcInvocationResult invoke(RpcInvocationContext context) {
            timeoutSeen.set(context.getOption(Number.class, "timeoutOverrideMs").map(Number::longValue).orElse(-1L));
            retrySeen.set(context.getOption(Number.class, "retryOverride").map(Number::longValue).orElse(-1L));
            return RpcInvocationResult.success("ok", 12L);
          }
        }),
        Collections.singletonList(new RuntimePolicyRpcInvocationInterceptor(policyService))
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest("duplicateMock", Collections.emptyMap()));

    assertTrue(result.isSuccess());
    assertEquals(520L, timeoutSeen.get());
    assertEquals(3L, retrySeen.get());
  }

  @Test
  void invoke_rejectsDisabledModelBeforeTransport() {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimeStatusService statusService = mock(RpcRuntimeStatusService.class);
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy(false, false, false, null, null, null));
    AtomicBoolean invoked = new AtomicBoolean(false);

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new FlagProtocolInvoker(invoked)),
        Arrays.asList(
            new RuntimePolicyRpcInvocationInterceptor(policyService),
            new AvailabilityRpcInvocationInterceptor(statusService)
        )
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest("duplicateMock", Collections.emptyMap()));

    assertTrue(result.isFailure());
    assertTrue(result.getError() instanceof RPCException);
    assertEquals("RPC模型已被禁用: duplicateMock", result.getError().getMessage());
    assertFalse(invoked.get());
    verify(statusService, never()).getStatus(anyString());
  }

  @Test
  void invoke_rejectsUnavailableStatusWhenAvailabilityGuardEnabled() {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimeStatusService statusService = mock(RpcRuntimeStatusService.class);
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy(true, false, true, null, null, null));
    RpcRuntimeStatusModel status = new RpcRuntimeStatusModel();
    status.setModelName("duplicateMock");
    status.setAvailability("UNAVAILABLE");
    when(statusService.getStatus("duplicateMock")).thenReturn(status);
    AtomicBoolean invoked = new AtomicBoolean(false);

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new FlagProtocolInvoker(invoked)),
        Arrays.asList(
            new RuntimePolicyRpcInvocationInterceptor(policyService),
            new AvailabilityRpcInvocationInterceptor(statusService)
        )
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest("duplicateMock", Collections.emptyMap()));

    assertTrue(result.isFailure());
    assertEquals("RPC模型当前不可用: duplicateMock", result.getError().getMessage());
    assertFalse(invoked.get());
  }

  @Test
  void invoke_allowsHealthCheckToBypassAvailabilityGuard() {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimeStatusService statusService = mock(RpcRuntimeStatusService.class);
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy(false, false, true, null, null, "UNAVAILABLE"));
    AtomicBoolean invoked = new AtomicBoolean(false);

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new FlagProtocolInvoker(invoked)),
        Arrays.asList(
            new RuntimePolicyRpcInvocationInterceptor(policyService),
            new AvailabilityRpcInvocationInterceptor(statusService)
        )
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest(
        "duplicateMock",
        Collections.emptyMap(),
        true,
        "health-check",
        Collections.emptyMap()
    ));

    assertTrue(result.isSuccess());
    assertTrue(invoked.get());
  }

  @Test
  void invoke_updatesStatusForSuccessfulExternalCalls() {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimeStatusService statusService = mock(RpcRuntimeStatusService.class);
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy(true, true, true, null, null, null));
    RpcRuntimeStatusModel existing = new RpcRuntimeStatusModel();
    existing.setModelName("duplicateMock");
    existing.setAvailability("UNAVAILABLE");
    existing.setSuccessCount(2L);
    existing.setFailureCount(1L);
    existing.setConsecutiveFailureCount(1L);
    existing.setAvgLatencyMs(10L);
    when(statusService.getStatus("duplicateMock")).thenReturn(existing);

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new RpcProtocolInvoker() {
          @Override
          public boolean supports(RPCModelDefinition definition) {
            return definition != null && "MockType".equalsIgnoreCase(definition.getType());
          }

          @Override
          public RpcInvocationResult invoke(RpcInvocationContext context) {
            return RpcInvocationResult.success(Collections.singletonMap("status", "ok"), 40L);
          }
        }),
        Arrays.asList(
            new RuntimePolicyRpcInvocationInterceptor(policyService),
            new MetricsRpcInvocationInterceptor(statusService)
        )
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest("duplicateMock", Collections.emptyMap()));

    assertTrue(result.isSuccess());
    ArgumentCaptor<RpcRuntimeStatusModel> captor = ArgumentCaptor.forClass(RpcRuntimeStatusModel.class);
    verify(statusService).saveStatus(eq("duplicateMock"), captor.capture());
    RpcRuntimeStatusModel saved = captor.getValue();
    assertEquals(Long.valueOf(3L), saved.getSuccessCount());
    assertEquals(Long.valueOf(1L), saved.getFailureCount());
    assertEquals(Long.valueOf(0L), saved.getConsecutiveFailureCount());
    assertEquals("HEALTHY", saved.getAvailability());
    assertEquals(Long.valueOf(18L), saved.getAvgLatencyMs());
    assertNotNull(saved.getLastInvokeAt());
    assertNotNull(saved.getLastSuccessAt());
    assertNull(saved.getLastFailureReason());
  }

  @Test
  void invoke_updatesStatusForFailedExternalCalls() {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimeStatusService statusService = mock(RpcRuntimeStatusService.class);
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy(true, false, true, null, null, null));
    RpcRuntimeStatusModel existing = new RpcRuntimeStatusModel();
    existing.setModelName("duplicateMock");
    existing.setAvailability("HEALTHY");
    existing.setSuccessCount(1L);
    existing.setFailureCount(0L);
    existing.setConsecutiveFailureCount(0L);
    existing.setAvgLatencyMs(8L);
    when(statusService.getStatus("duplicateMock")).thenReturn(existing);

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new RpcProtocolInvoker() {
          @Override
          public boolean supports(RPCModelDefinition definition) {
            return definition != null && "MockType".equalsIgnoreCase(definition.getType());
          }

          @Override
          public RpcInvocationResult invoke(RpcInvocationContext context) {
            return RpcInvocationResult.failure(new RPCException("boom"), 22L);
          }
        }),
        Arrays.asList(
            new RuntimePolicyRpcInvocationInterceptor(policyService),
            new MetricsRpcInvocationInterceptor(statusService)
        )
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest("duplicateMock", Collections.emptyMap()));

    assertTrue(result.isFailure());
    ArgumentCaptor<RpcRuntimeStatusModel> captor = ArgumentCaptor.forClass(RpcRuntimeStatusModel.class);
    verify(statusService).saveStatus(eq("duplicateMock"), captor.capture());
    RpcRuntimeStatusModel saved = captor.getValue();
    assertEquals(Long.valueOf(1L), saved.getSuccessCount());
    assertEquals(Long.valueOf(1L), saved.getFailureCount());
    assertEquals(Long.valueOf(1L), saved.getConsecutiveFailureCount());
    assertEquals("UNAVAILABLE", saved.getAvailability());
    assertEquals("boom", saved.getLastFailureReason());
    assertEquals(Long.valueOf(15L), saved.getAvgLatencyMs());
    assertNotNull(saved.getLastFailureAt());
  }

  @Test
  void invoke_skipsStatusUpdatesForInternalCalls() {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimeStatusService statusService = mock(RpcRuntimeStatusService.class);
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy(true, true, true, null, null, null));

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new RpcProtocolInvoker() {
          @Override
          public boolean supports(RPCModelDefinition definition) {
            return definition != null && "MockType".equalsIgnoreCase(definition.getType());
          }

          @Override
          public RpcInvocationResult invoke(RpcInvocationContext context) {
            return RpcInvocationResult.success(Collections.singletonMap("status", "ok"), 12L);
          }
        }),
        Arrays.asList(
            new RuntimePolicyRpcInvocationInterceptor(policyService),
            new MetricsRpcInvocationInterceptor(statusService)
        )
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest(
        "duplicateMock",
        Collections.emptyMap(),
        true,
        "token-fetch",
        Collections.emptyMap()
    ));

    assertTrue(result.isSuccess());
    verify(statusService, never()).getStatus(anyString());
    verify(statusService, never()).saveStatus(anyString(), any(RpcRuntimeStatusModel.class));
  }

  @Test
  void invoke_skipsStatusUpdatesForManualTestCalls() {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimeStatusService statusService = mock(RpcRuntimeStatusService.class);
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy(true, true, true, null, null, null));

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new RpcProtocolInvoker() {
          @Override
          public boolean supports(RPCModelDefinition definition) {
            return definition != null && "MockType".equalsIgnoreCase(definition.getType());
          }

          @Override
          public RpcInvocationResult invoke(RpcInvocationContext context) {
            return RpcInvocationResult.success(Collections.singletonMap("status", "ok"), 12L);
          }
        }),
        Arrays.asList(
            new RuntimePolicyRpcInvocationInterceptor(policyService),
            new MetricsRpcInvocationInterceptor(statusService)
        )
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest(
        "duplicateMock",
        Collections.emptyMap(),
        false,
        "manual-test",
        Collections.emptyMap()
    ));

    assertTrue(result.isSuccess());
    verify(statusService, never()).getStatus(anyString());
    verify(statusService, never()).saveStatus(anyString(), any(RpcRuntimeStatusModel.class));
  }

  private RpcRuntimePolicyModel policy(
      boolean enabled,
      boolean metricsEnabled,
      boolean availabilityEnabled,
      Long timeoutOverrideMs,
      Integer retryOverride,
      String forcedAvailabilityStatus
  ) {
    RpcRuntimePolicyModel policy = new RpcRuntimePolicyModel();
    policy.setModelName("duplicateMock");
    policy.setEnabled(enabled);
    policy.setMetricsEnabled(metricsEnabled);
    policy.setAvailabilityEnabled(availabilityEnabled);
    policy.setTimeoutOverrideMs(timeoutOverrideMs);
    policy.setRetryOverride(retryOverride);
    policy.setForcedAvailabilityStatus(forcedAvailabilityStatus);
    return policy;
  }

  private static class FlagProtocolInvoker implements RpcProtocolInvoker {
    private final AtomicBoolean invoked;

    private FlagProtocolInvoker(AtomicBoolean invoked) {
      this.invoked = invoked;
    }

    @Override
    public boolean supports(RPCModelDefinition definition) {
      return definition != null && "MockType".equalsIgnoreCase(definition.getType());
    }

    @Override
    public RpcInvocationResult invoke(RpcInvocationContext context) {
      invoked.set(true);
      return RpcInvocationResult.success(Collections.singletonMap("status", "ok"), 5L);
    }
  }
}
