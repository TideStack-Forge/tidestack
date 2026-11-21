package com.ouroboros.rpc.execution.interceptor;

import java.time.LocalDateTime;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.rpc.execution.RpcInvocationContext;
import com.ouroboros.rpc.execution.RpcInvocationInterceptor;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimeStatusService;

@Component
@Order(300)
public class MetricsRpcInvocationInterceptor implements RpcInvocationInterceptor {
  private static final String AVAILABILITY_HEALTHY = "HEALTHY";
  private static final String AVAILABILITY_UNAVAILABLE = "UNAVAILABLE";
  private static final String PURPOSE_MANUAL_TEST = "manual-test";

  private final RpcRuntimeStatusService statusService;

  public MetricsRpcInvocationInterceptor(RpcRuntimeStatusService statusService) {
    this.statusService = statusService;
  }

  @Override
  public RpcInvocationResult intercept(RpcInvocationContext context, Chain chain) {
    if (context.isDevelopmentTest()) {
      return chain.proceed(context);
    }
    RpcInvocationResult result = chain.proceed(context);
    RpcRuntimePolicyModel policy = RuntimePolicyRpcInvocationInterceptor.getPolicy(context);
    if (policy == null || context == null || shouldSkipStatusMutation(context)) {
      return result;
    }

    if (!Boolean.TRUE.equals(policy.getMetricsEnabled()) && !Boolean.TRUE.equals(policy.getAvailabilityEnabled())) {
      return result;
    }

    RpcRuntimeStatusModel status = statusService.getStatus(context.getModelName());
    if (status == null) {
      status = new RpcRuntimeStatusModel();
      status.setModelName(context.getModelName());
      status.setAvailability(AVAILABILITY_HEALTHY);
      status.setConsecutiveFailureCount(0L);
      status.setSuccessCount(0L);
      status.setFailureCount(0L);
      status.setAvgLatencyMs(0L);
    }

    long successCount = defaultLong(status.getSuccessCount());
    long failureCount = defaultLong(status.getFailureCount());
    long totalBefore = successCount + failureCount;
    long durationMs = result == null ? 0L : Math.max(result.getDurationMs(), 0L);

    status.setLastInvokeAt(LocalDateTime.now());
    status.setAvgLatencyMs(calculateAvgLatency(defaultLong(status.getAvgLatencyMs()), totalBefore, durationMs));

    if (result != null && result.isSuccess()) {
      status.setSuccessCount(successCount + 1L);
      status.setConsecutiveFailureCount(0L);
      status.setLastSuccessAt(status.getLastInvokeAt());
      status.setLastFailureReason(null);
      if (Boolean.TRUE.equals(policy.getAvailabilityEnabled())) {
        status.setAvailability(AVAILABILITY_HEALTHY);
      }
    } else {
      status.setFailureCount(failureCount + 1L);
      status.setConsecutiveFailureCount(defaultLong(status.getConsecutiveFailureCount()) + 1L);
      status.setLastFailureAt(status.getLastInvokeAt());
      status.setLastFailureReason(result == null || result.getError() == null ? null : result.getError().getMessage());
      if (Boolean.TRUE.equals(policy.getAvailabilityEnabled())) {
        status.setAvailability(AVAILABILITY_UNAVAILABLE);
      }
    }

    statusService.saveStatus(context.getModelName(), status);
    return result;
  }

  private long defaultLong(Long value) {
    return value == null ? 0L : value;
  }

  private boolean shouldSkipStatusMutation(RpcInvocationContext context) {
    return context.isInternalCall() || PURPOSE_MANUAL_TEST.equalsIgnoreCase(context.getPurpose());
  }

  private long calculateAvgLatency(long currentAvg, long totalBefore, long durationMs) {
    long totalAfter = totalBefore + 1L;
    if (totalAfter <= 0L) {
      return durationMs;
    }
    return Math.round(((double) currentAvg * totalBefore + durationMs) / totalAfter);
  }
}
