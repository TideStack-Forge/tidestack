package com.ouroboros.rpc.execution;

public interface RpcInvocationInterceptor {

  RpcInvocationResult intercept(RpcInvocationContext context, Chain chain);

  interface Chain {
    RpcInvocationResult proceed(RpcInvocationContext context);
  }
}
