package com.ouroboros.rpc.execution.interceptor;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.rpc.execution.RpcInvocationContext;
import com.ouroboros.rpc.execution.RpcInvocationInterceptor;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;

@Component
@Order(0)
public class RuntimePolicyRpcInvocationInterceptor implements RpcInvocationInterceptor {
  public static final String ATTRIBUTE_POLICY = "rpcRuntimePolicy";

  private final RpcRuntimePolicyService policyService;

  public RuntimePolicyRpcInvocationInterceptor(RpcRuntimePolicyService policyService) {
    this.policyService = policyService;
  }

  @Override
  public RpcInvocationResult intercept(RpcInvocationContext context, Chain chain) {
    if (context.isDevelopmentTest()) {
      return chain.proceed(context);
    }
    RpcRuntimePolicyModel policy = policyService.getPolicy(context.getModelName());
    context.setAttribute(ATTRIBUTE_POLICY, policy);
    if (policy != null) {
      if (policy.getTimeoutOverrideMs() != null) {
        context.setOption("timeoutOverrideMs", policy.getTimeoutOverrideMs());
      }
      if (policy.getRetryOverride() != null) {
        context.setOption("retryOverride", policy.getRetryOverride());
      }
    }
    return chain.proceed(context);
  }

  static RpcRuntimePolicyModel getPolicy(RpcInvocationContext context) {
    return context == null
        ? null
        : context.getAttribute(RpcRuntimePolicyModel.class, ATTRIBUTE_POLICY).orElse(null);
  }
}
