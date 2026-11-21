package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.exception.InputPortConflictDetectedException;
import com.ouroboros.logicflow.exception.InputOutputDataTypeMismatchedException;
import com.ouroboros.logicflow.exception.InvalidConnectionDetectedException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelConnectionValidationTest {
  @Test
  public void invalidConnectionTargetPortOutOfRangeShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/invalid-connection-target-port-out-of-range.json");

    assertFalse("Expected model to be invalid but it was valid", flowModel.isValid());
    assertTrue("Expected invalid-connection error but found: " + flowModel.getErrors(),
        flowModel.getErrors().stream()
            .anyMatch(error -> error != null
                && error.getMessage() != null
                && error.getMessage().contains("连线端口")));
  }

  @Test
  public void invalidConnectionShouldNotCascadeIntoInputPortConflicts() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/invalid-connection-should-not-cascade-input-conflict.json");

    FlowModelAssertions.assertInvalid(flowModel, InvalidConnectionDetectedException.class);
    assertTrue("Expected input-port conflict check to ignore invalid connections but found: " + flowModel.getErrors(),
        FlowModelAssertions.getError(flowModel, InputPortConflictDetectedException.class) == null);
    assertTrue("Expected type compatibility check to ignore invalid connections but found: " + flowModel.getErrors(),
        FlowModelAssertions.getError(flowModel, InputOutputDataTypeMismatchedException.class) == null);
  }
}
