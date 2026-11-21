package com.ouroboros.logicflow.model;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelValidationSmokeTest {
  @Test
  public void minimalLinearFixtureShouldBeValid() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/minimal-linear.json");

    FlowModelAssertions.assertValid(flowModel);
  }
}
