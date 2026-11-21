package com.ouroboros.rpc.runtime.service;

import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;

public interface RpcRuntimeOperationService {

  RpcInvocationResult testInvoke(RpcInvocationRequest request);

  RpcInvocationResult healthCheck(String modelName);

  RpcRuntimeStatusModel resetStatus(String modelName);

  RpcRuntimePolicyModel repairPolicy(String modelName);
}
