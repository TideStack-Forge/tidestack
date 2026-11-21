package com.ouroboros.rpc.execution;

/** Managed modules contribute dependencies to both runtime and development-test invocations. */
@FunctionalInterface
public interface RpcExecutionResourcesFactory {
  RpcExecutionResources create(RpcInvocationRequest request, RpcExecutionResources parent);
}
