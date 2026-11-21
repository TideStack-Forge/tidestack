package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelImplicitEntryRuntimeGuardTest {
  @Test
  public void missingInputRequiredByNonEntryRootShouldFailBeforeExecution() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/non-entry-root-with-input-port.json");

    FlowModelAssertions.assertValid(flowModel);
    var failure = FlowModelAssertions.assertExecuteFailure(flowModel);

    assertTrue(failure.getMessage().contains("输入参数数量不正确"));
    FlowModelAssertions.assertFlowRuntimeFailure(failure, "sideGate");
  }
}
