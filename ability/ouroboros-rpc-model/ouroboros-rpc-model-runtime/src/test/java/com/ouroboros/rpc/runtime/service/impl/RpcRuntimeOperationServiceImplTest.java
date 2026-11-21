package com.ouroboros.rpc.runtime.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.rpc.execution.RpcInvocationExecutor;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;
import com.ouroboros.rpc.runtime.service.RpcRuntimeStatusService;

class RpcRuntimeOperationServiceImplTest {

  @Test
  void repairPolicy_persistsNormalizedPolicyForSingleModel() {
    RpcInvocationExecutor executor = mock(RpcInvocationExecutor.class);
    RpcRuntimeStatusService statusService = mock(RpcRuntimeStatusService.class);
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimePolicyModel normalized = new RpcRuntimePolicyModel();
    normalized.setModelName("openApi");
    normalized.setEnabled(true);
    normalized.setNormalizationWarnings(Collections.singletonList("timeoutOverrideMs 存在非法值，运行时已忽略"));
    RpcRuntimePolicyModel repaired = new RpcRuntimePolicyModel();
    repaired.setModelName("openApi");
    repaired.setEnabled(true);
    when(policyService.getPolicy("openApi")).thenReturn(normalized);
    when(policyService.updatePolicy("openApi", normalized)).thenReturn(repaired);

    RpcRuntimeOperationServiceImpl service = new RpcRuntimeOperationServiceImpl(executor, statusService, policyService);

    RpcRuntimePolicyModel result = service.repairPolicy("openApi");

    assertSame(repaired, result);
    verify(policyService).getPolicy("openApi");
    verify(policyService).updatePolicy("openApi", normalized);
  }
}
