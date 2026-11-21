package com.ouroboros.logicflow.model;

import org.junit.Test;

import com.ouroboros.logicflow.exception.InvalidLeafNodeDetectedException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelLeafValidationTest {
  @Test
  public void reachableMainScopeLeafWithOutputsButNoOutgoingShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/non-terminal-leaf-main-scope.json");

    FlowModelAssertions.assertInvalid(flowModel, InvalidLeafNodeDetectedException.class);
  }

  @Test
  public void reachableErrorScopeLeafWithOutputsButNoOutgoingShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/non-terminal-leaf-error-scope.json");

    FlowModelAssertions.assertInvalid(flowModel, InvalidLeafNodeDetectedException.class);
  }

  @Test
  public void returnTerminalLeafShouldRemainValid() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/return-only-linear.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void explicitExitLeafShouldRemainValid() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/explicit-exit-leaf.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void zeroOutputSinkLeafShouldRemainValid() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/zero-output-sink-leaf.json");

    FlowModelAssertions.assertValid(flowModel);
  }
}
