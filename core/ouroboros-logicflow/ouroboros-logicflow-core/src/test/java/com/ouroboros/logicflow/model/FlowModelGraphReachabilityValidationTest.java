package com.ouroboros.logicflow.model;

import org.junit.Test;

import com.ouroboros.logicflow.exception.MissingSuccessfulTerminalException;
import com.ouroboros.logicflow.exception.UnreachableNodeDetectedException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelGraphReachabilityValidationTest {
  @Test
  public void zeroInputSourceFeedingReachableMainPathShouldBeAccepted() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/zero-input-upstream-source-participates-in-main-flow.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void ordinaryFlowWithUnreachableEnabledNodeShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/unreachable-enabled-node.json");

    FlowModelAssertions.assertInvalid(flowModel, UnreachableNodeDetectedException.class);
  }

  @Test
  public void unreachableExplicitExitShouldNotSatisfySuccessfulTerminalPresence() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/unreachable-exit-terminal.json");

    FlowModelAssertions.assertInvalid(flowModel, MissingSuccessfulTerminalException.class);
  }

  @Test
  public void unreachableReturnNodeShouldNotSatisfySuccessfulTerminalPresence() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/unreachable-return-terminal.json");

    FlowModelAssertions.assertInvalid(flowModel, MissingSuccessfulTerminalException.class);
  }
}
