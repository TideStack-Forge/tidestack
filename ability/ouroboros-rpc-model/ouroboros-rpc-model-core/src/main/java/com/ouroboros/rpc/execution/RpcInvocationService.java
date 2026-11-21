package com.ouroboros.rpc.execution;

/** Invocation entry point for published models and isolated call-scoped resources. */
public interface RpcInvocationService {
  RpcInvocationResult invoke(RpcInvocationRequest request);

  RpcInvocationResult invoke(RpcInvocationRequest request, RpcExecutionResources resources);
}
