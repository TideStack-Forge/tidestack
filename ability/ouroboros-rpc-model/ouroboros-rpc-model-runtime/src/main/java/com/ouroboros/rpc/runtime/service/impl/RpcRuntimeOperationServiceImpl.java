package com.ouroboros.rpc.runtime.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;

import org.springframework.stereotype.Service;

import com.ouroboros.rpc.execution.RpcInvocationExecutor;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimeOperationService;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;
import com.ouroboros.rpc.runtime.service.RpcRuntimeStatusService;

@Service
public class RpcRuntimeOperationServiceImpl implements RpcRuntimeOperationService {
  private static final String AVAILABILITY_UNAVAILABLE = "UNAVAILABLE";

  private final RpcInvocationExecutor rpcInvocationExecutor;
  private final RpcRuntimeStatusService rpcRuntimeStatusService;
  private final RpcRuntimePolicyService rpcRuntimePolicyService;

  public RpcRuntimeOperationServiceImpl(
      RpcInvocationExecutor rpcInvocationExecutor,
      RpcRuntimeStatusService rpcRuntimeStatusService,
      RpcRuntimePolicyService rpcRuntimePolicyService
  ) {
    this.rpcInvocationExecutor = rpcInvocationExecutor;
    this.rpcRuntimeStatusService = rpcRuntimeStatusService;
    this.rpcRuntimePolicyService = rpcRuntimePolicyService;
  }

  @Override
  public RpcInvocationResult testInvoke(RpcInvocationRequest request) {
    return rpcInvocationExecutor.invoke(request);
  }

  @Override
  public RpcInvocationResult healthCheck(String modelName) {
    RpcInvocationResult result = rpcInvocationExecutor.invoke(
        new RpcInvocationRequest(modelName, Collections.emptyMap(), true, "health-check", Collections.emptyMap())
    );

    LocalDateTime now = LocalDateTime.now();
    RpcRuntimeStatusModel status = rpcRuntimeStatusService.getStatus(modelName);
    status.setLastHealthCheckAt(now);
    status.setLastHealthCheckSuccess(result.isSuccess());
    status.setAvailability(result.isSuccess() ? RpcRuntimeStatusServiceImpl.AVAILABILITY_HEALTHY : AVAILABILITY_UNAVAILABLE);
    if (result.isSuccess()) {
      status.setLastSuccessAt(now);
      status.setLastFailureReason(null);
    } else {
      status.setLastFailureAt(now);
      status.setLastFailureReason(result.getError() == null ? null : result.getError().getMessage());
    }
    rpcRuntimeStatusService.saveStatus(modelName, status);
    return result;
  }

  @Override
  public RpcRuntimeStatusModel resetStatus(String modelName) {
    return rpcRuntimeStatusService.resetStatus(modelName);
  }

  @Override
  public RpcRuntimePolicyModel repairPolicy(String modelName) {
    RpcRuntimePolicyModel normalized = rpcRuntimePolicyService.getPolicy(modelName);
    return rpcRuntimePolicyService.updatePolicy(modelName, normalized);
  }
}
