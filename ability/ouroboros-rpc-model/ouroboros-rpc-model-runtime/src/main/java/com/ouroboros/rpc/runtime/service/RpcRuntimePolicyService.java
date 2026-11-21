package com.ouroboros.rpc.runtime.service;

import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;

public interface RpcRuntimePolicyService {

  RpcRuntimePolicyModel getPolicy(String modelName);

  RpcRuntimePolicyModel updatePolicy(String modelName, RpcRuntimePolicyModel policy);
}
