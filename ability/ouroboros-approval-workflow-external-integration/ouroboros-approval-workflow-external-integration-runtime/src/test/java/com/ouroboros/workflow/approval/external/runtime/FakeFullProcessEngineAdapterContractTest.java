package com.ouroboros.workflow.approval.external.runtime;

import java.util.Map;
import java.util.Set;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.workflow.approval.external.Capability;

/**
 * 全过程 fake 适配器契约测试：验证 M12 fake 满足适配器契约基座（含定义目录与动作执行契约）。
 */
class FakeFullProcessEngineAdapterContractTest extends AbstractExternalEngineAdapterContractTest {

  @Override
  protected ApprovalWorkflowEngineAdapterProvider provider() {
    FakeFullProcessEngineAdapter adapter = new FakeFullProcessEngineAdapter();
    return new ApprovalWorkflowEngineAdapterProvider() {
      @Override
      public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
        return new ApprovalWorkflowEngineAdapterDescriptor("fake-full-process", "全过程测试适配器", "1.0", Set.of("approve", "reject", "transfer", "remind", "cancel"), Set.of(Capability.EXTERNAL_FINAL_RESULT, Capability.POLLING,
                Capability.EXTERNAL_INSTANCE_ACTION, Capability.EXTERNAL_INSTANCE_CANCEL), Set.of());
      }

      @Override
      public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
        return adapter;
      }
    };
  }

  @Override
  protected boolean supportsDefinitionCatalog() {
    return true;
  }

  @Override
  protected boolean supportsActionExecution() {
    return true;
  }
}
