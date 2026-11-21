package com.ouroboros.logicflow.model;

import java.util.Collections;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelExecutionResultSelectionTest {
  @Test
  public void linearFixtureShouldReturnExplicitExitResult() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("result/linear-exit-result.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, Collections.singletonMap("done", true), "input");
  }
}
