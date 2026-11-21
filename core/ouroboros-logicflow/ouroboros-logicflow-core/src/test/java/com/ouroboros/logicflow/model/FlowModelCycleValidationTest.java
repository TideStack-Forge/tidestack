package com.ouroboros.logicflow.model;

import org.junit.Test;

import com.ouroboros.logicflow.exception.CyclicDetectedException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelCycleValidationTest {
  @Test
  public void cyclicFixtureShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/cycle-basic.json");

    FlowModelAssertions.assertInvalid(flowModel, CyclicDetectedException.class);
  }
}
