package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelSuccessfulTerminalPresenceValidationTest {
  @Test
  public void returnOnlyFixtureShouldBeValidWithoutExplicitExitNode() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/return-only-linear.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void fixtureWithoutExitOrReturnShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/no-success-terminal.json");

    assertFalse("Expected fixture without success terminal to be invalid", flowModel.isValid());
    assertTrue("Expected missing-success-terminal validation error but found: " + flowModel.getErrors(),
        flowModel.getErrors().stream().anyMatch(error -> "MissingSuccessfulTerminalException".equals(error.getClass().getSimpleName())));
  }
}
