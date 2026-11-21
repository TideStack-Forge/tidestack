package com.ouroboros.logicflow.model;

import org.junit.Test;

import com.ouroboros.logicflow.exception.UnconnectedInputPortException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelInputConnectivityValidationTest {
  @Test
  public void fixtureWithUnconnectedRequiredInputShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/unconnected-input.json");

    FlowModelAssertions.assertInvalid(flowModel, UnconnectedInputPortException.class);
  }

  @Test
  public void disabledNodesWithUnconnectedRequiredInputsShouldBeIgnored() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/disabled-unconnected-input-ignored.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void nonEntryRootWithEffectiveInputPortsShouldAcceptFlowInputs() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/non-entry-root-with-input-port.json");

    FlowModelAssertions.assertValid(flowModel);
  }
}
