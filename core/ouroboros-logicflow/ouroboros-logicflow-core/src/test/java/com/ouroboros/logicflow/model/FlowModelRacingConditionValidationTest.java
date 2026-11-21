package com.ouroboros.logicflow.model;

import org.junit.Test;

import com.ouroboros.logicflow.exception.RacingConditionDetectedException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelRacingConditionValidationTest {
  @Test
  public void metadataSelectiveBranchesTargetingDifferentPortsOnSameNodeShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/racing-condition-metadata-exclusive.json");

    FlowModelAssertions.assertInvalid(flowModel, RacingConditionDetectedException.class);
  }

  @Test
  public void decisionBranchesTargetingDifferentPortsOnSameNodeShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/racing-condition-basic.json");

    FlowModelAssertions.assertInvalid(flowModel, RacingConditionDetectedException.class);
  }

  @Test
  public void selectiveBranchesTargetingDifferentPortsOnLeafNodeWithNoOutputPortsShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/racing-condition-leaf-zero-output.json");

    FlowModelAssertions.assertInvalid(flowModel, RacingConditionDetectedException.class);
  }
}
