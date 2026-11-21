package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelExecutionSmokeTest {
  @Test
  public void singleConstFixtureShouldExecuteSuccessfully() {
    var flowModel = FlowModelFixtures.loadSmokeFixture("single-const-exec.json");
    FlowModelAssertions.assertValid(flowModel);

    var result = flowModel.execute();

    assertTrue("Smoke fixture execution should succeed", result.isSuccess());
    assertEquals("hello", result.get());
  }
}
