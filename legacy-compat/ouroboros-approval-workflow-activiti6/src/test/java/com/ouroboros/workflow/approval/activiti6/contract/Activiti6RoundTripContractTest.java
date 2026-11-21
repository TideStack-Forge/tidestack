package com.ouroboros.workflow.approval.activiti6.contract;

import com.ouroboros.workflow.approval.activiti6.testkit.Activiti6EngineCapabilitySet;
import com.ouroboros.workflow.approval.activiti6.testkit.Activiti6EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import com.ouroboros.workflow.approval.testkit.adapter.EngineTestAdapter;
import com.ouroboros.workflow.approval.testkit.contract.AbstractRoundTripContractTest;

class Activiti6RoundTripContractTest extends AbstractRoundTripContractTest {
  private final Activiti6EngineTestAdapter adapter = new Activiti6EngineTestAdapter();

  @Override
  protected EngineTestAdapter adapter() {
    return adapter;
  }

  @Override
  protected EngineCapability capabilities() {
    return Activiti6EngineCapabilitySet.DEFAULT;
  }
}
