package com.ouroboros.logicflow.model;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelExecutionRuntimeFailureTest {
  @Test
  public void runtimeNodeFailureShouldBeSurfacedAsExecuteFailure() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("runtime/script-node-runtime-failure.json");

    FlowModelAssertions.assertValid(flowModel);
    var failure = FlowModelAssertions.assertExecuteFailure(flowModel, "input");

    FlowModelAssertions.assertFlowRuntimeFailure(failure, "failingScript");
  }
}
