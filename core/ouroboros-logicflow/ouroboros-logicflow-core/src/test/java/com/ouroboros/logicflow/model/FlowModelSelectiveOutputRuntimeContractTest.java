package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelSelectiveOutputRuntimeContractTest {
  @Test
  public void selectiveNodeShouldFailIfItSatisfiesMultiplePortsInSameExclusiveGroup() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("runtime/broken-selective-node-violates-exclusive-group.json");

    FlowModelAssertions.assertValid(flowModel);
    var failure = FlowModelAssertions.assertExecuteFailure(flowModel, "input");

    assertTrue("Expected selective-output runtime contract failure but found: " + failure.getMessage(),
        failure.getMessage().contains("exclusiveGroupId"));
  }
}
