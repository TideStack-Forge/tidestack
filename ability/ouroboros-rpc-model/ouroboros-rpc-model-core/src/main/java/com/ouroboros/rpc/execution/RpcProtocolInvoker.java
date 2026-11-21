package com.ouroboros.rpc.execution;

import com.ouroboros.rpc.RPCModelDefinition;

public interface RpcProtocolInvoker {

  boolean supports(RPCModelDefinition definition);

  RpcInvocationResult invoke(RpcInvocationContext context);
}
