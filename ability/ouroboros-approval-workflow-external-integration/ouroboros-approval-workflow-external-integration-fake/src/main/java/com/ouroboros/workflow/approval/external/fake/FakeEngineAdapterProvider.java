package com.ouroboros.workflow.approval.external.fake;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.InMemoryExternalEngineCore;

/**
 * 产品级 fake 适配器 Provider（灰度/演示用，SPI 注册，adapterId=fake-external-engine）。
 */
public class FakeEngineAdapterProvider implements ApprovalWorkflowEngineAdapterProvider {

  public static final String ADAPTER_ID = "fake-external-engine";
  public static final String START_FAIL_FIELD = "startFail";
  public static final String AUTO_APPROVE_AFTER_SECONDS_FIELD = "autoApproveAfterSeconds";
  public static final String INITIAL_STATUS_FIELD = "initialStatus";

  @Override
  public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
    return new ApprovalWorkflowEngineAdapterDescriptor(
        ADAPTER_ID, "Fake 外部引擎（演示）", "1.0",
        Set.of("approve", "reject", "transfer", "remind", "cancel"),
        Set.of(Capability.EXTERNAL_FINAL_RESULT, Capability.POLLING),
        Set.of());
  }







  @Override
  public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
    return new FakeExternalEngineAdapter(connection);
  }




}
