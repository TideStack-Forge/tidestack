package com.ouroboros.workflow.approval.external.fake;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.runtime.AbstractExternalEngineAdapterContractTest;

/**
 * M16 产品级 fake 适配器契约测试：复用适配器契约基座全部断言。
 */
class FakeExternalEngineAdapterContractTest extends AbstractExternalEngineAdapterContractTest {

  @Override
  protected ApprovalWorkflowEngineAdapterProvider provider() {
    return new FakeEngineAdapterProvider();
  }
}
