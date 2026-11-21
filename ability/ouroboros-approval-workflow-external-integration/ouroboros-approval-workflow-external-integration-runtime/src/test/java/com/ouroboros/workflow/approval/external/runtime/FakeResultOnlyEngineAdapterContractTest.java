package com.ouroboros.workflow.approval.external.runtime;

import java.util.Map;
import java.util.Set;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.workflow.approval.external.Capability;

/**
 * 结果型 fake 适配器契约测试：验证 M5 fake 满足适配器契约基座。
 */
class FakeResultOnlyEngineAdapterContractTest extends AbstractExternalEngineAdapterContractTest {

  @Override
  protected ApprovalWorkflowEngineAdapterProvider provider() {
    FakeResultOnlyEngineAdapter adapter = new FakeResultOnlyEngineAdapter();
    return new ApprovalWorkflowEngineAdapterProvider() {
      @Override
      public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
        return new ApprovalWorkflowEngineAdapterDescriptor("fake-result-only", "结果型测试适配器", "1.0", Set.of("approve", "reject"), Set.of(Capability.POLLING, Capability.EXTERNAL_FINAL_RESULT), Set.of());
      }

      @Override
      public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
        return adapter;
      }
    };
  }
}
