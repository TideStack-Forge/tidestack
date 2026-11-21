package com.ouroboros.rpc.runtime.service;

import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;

public interface RpcRuntimeStatusService {

  RpcRuntimeStatusModel getStatus(String modelName);

  RpcRuntimeStatusModel saveStatus(String modelName, RpcRuntimeStatusModel status);

  RpcRuntimeStatusModel resetStatus(String modelName);
}
