package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelSuccessfulTerminalExclusivityValidationTest {
  @Test
  public void exclusiveReturnAndExitBranchesShouldBeValid() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/exclusive-return-vs-exit.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void serialSuccessfulTerminalsShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/dual-success-same-path.json");

    assertConflict(flowModel);
  }

  @Test
  public void ambiguousDualSuccessTopologyShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/dual-success-ambiguous.json");

    assertConflict(flowModel);
  }

  private void assertConflict(DefaultFlowModel flowModel) {
    assertFalse("Expected dual-success fixture to be invalid", flowModel.isValid());
    assertTrue("Expected successful-terminal conflict error but found: " + flowModel.getErrors(),
        flowModel.getErrors().stream().anyMatch(error -> "SuccessfulTerminalConflictDetectedException".equals(error.getClass().getSimpleName())));
  }
}
