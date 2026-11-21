package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.ouroboros.logicflow.exception.ErrorEntryNotFoundException;
import com.ouroboros.logicflow.exception.InvalidFlowScopeDetectedException;
import com.ouroboros.logicflow.exception.MissingSuccessfulTerminalException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelErrorScopeValidationTest {
  @Test
  public void disjointErrorScopeFixtureShouldBeValid() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/error-entry-disjoint-scopes.json");
    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void missingConfiguredErrorEntryShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/error-entry-missing-node.json");
    FlowModelAssertions.assertInvalid(flowModel, ErrorEntryNotFoundException.class);
  }

  @Test
  public void errorEntrySameAsMainEntryShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/error-entry-same-as-entry.json");
    var error = FlowModelAssertions.assertInvalid(flowModel, InvalidFlowScopeDetectedException.class);
    assertEquals(Arrays.asList("mainEntry"), error.getNodeNames());
  }

  @Test
  public void errorEntrySameAsExplicitExitShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/error-entry-same-as-exit.json");
    var error = FlowModelAssertions.assertInvalid(flowModel, InvalidFlowScopeDetectedException.class);
    assertEquals(Arrays.asList("mainExit"), error.getNodeNames());
  }

  @Test
  public void nodesReachableFromBothScopesShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/error-entry-overlap-scopes.json");
    var error = FlowModelAssertions.assertInvalid(flowModel, InvalidFlowScopeDetectedException.class);
    assertEquals(Arrays.asList("sharedNode", "mainExit", "errorReturn"), error.getNodeNames());
  }

  @Test
  public void enabledNodesOutsideBothScopesShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/error-entry-unreachable-enabled-node.json");
    var error = FlowModelAssertions.assertInvalid(flowModel, InvalidFlowScopeDetectedException.class);
    assertEquals(Arrays.asList("orphanConst"), error.getNodeNames());
  }

  @Test
  public void errorScopeReturnShouldNotSatisfyMainScopeSuccessfulTerminalRequirement() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/error-entry-main-scope-missing-success-terminal.json");
    FlowModelAssertions.assertInvalid(flowModel, MissingSuccessfulTerminalException.class);
  }
}
