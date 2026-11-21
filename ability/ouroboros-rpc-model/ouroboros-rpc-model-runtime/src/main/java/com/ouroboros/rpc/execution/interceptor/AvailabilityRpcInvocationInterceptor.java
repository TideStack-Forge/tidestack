package com.ouroboros.rpc.execution.interceptor;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.rpc.exception.RPCException;
import com.ouroboros.rpc.execution.RpcInvocationContext;
import com.ouroboros.rpc.execution.RpcInvocationInterceptor;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimeStatusService;

@Component
@Order(100)
public class AvailabilityRpcInvocationInterceptor implements RpcInvocationInterceptor {
  private static final String AVAILABILITY_HEALTHY = "HEALTHY";
  private static final String AVAILABILITY_UNAVAILABLE = "UNAVAILABLE";
  private static final String OPTION_BYPASS_POLICY_GUARDS = "bypassPolicyGuards";

  private final RpcRuntimeStatusService statusService;

  public AvailabilityRpcInvocationInterceptor(RpcRuntimeStatusService statusService) {
    this.statusService = statusService;
  }

  @Override
  public RpcInvocationResult intercept(RpcInvocationContext context, Chain chain) {
    if (context.isDevelopmentTest()) {
      return chain.proceed(context);
    }
    RpcRuntimePolicyModel policy = RuntimePolicyRpcInvocationInterceptor.getPolicy(context);
    if (policy == null || shouldBypassGuard(context)) {
      return chain.proceed(context);
    }

    String modelName = context.getModelName();
    if (Boolean.FALSE.equals(policy.getEnabled())) {
      return RpcInvocationResult.failure(new RPCException("RPC模型已被禁用: " + modelName));
    }

    if (AVAILABILITY_UNAVAILABLE.equalsIgnoreCase(policy.getForcedAvailabilityStatus())) {
      return RpcInvocationResult.failure(new RPCException("RPC模型当前不可用: " + modelName));
    }

    if (!Boolean.TRUE.equals(policy.getAvailabilityEnabled())
        || AVAILABILITY_HEALTHY.equalsIgnoreCase(policy.getForcedAvailabilityStatus())) {
      return chain.proceed(context);
    }

    RpcRuntimeStatusModel status = statusService.getStatus(modelName);
    if (status != null && AVAILABILITY_UNAVAILABLE.equalsIgnoreCase(status.getAvailability())) {
      return RpcInvocationResult.failure(new RPCException("RPC模型当前不可用: " + modelName));
    }

    return chain.proceed(context);
  }

  private boolean shouldBypassGuard(RpcInvocationContext context) {
    if (context == null) {
      return false;
    }
    if (context.getOption(Boolean.class, OPTION_BYPASS_POLICY_GUARDS).orElse(false)) {
      return true;
    }
    return context.isInternalCall() && "health-check".equalsIgnoreCase(context.getPurpose());
  }
}
