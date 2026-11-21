package com.ouroboros.logicflow.model;

import static java.util.Collections.singletonMap;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelReturnExecutionTest {
  @Test
  public void returnTerminalFixtureShouldReturnItsPayload() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("result/return-terminal-result.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, "input", "input");
  }

  @Test
  public void legacyExplicitExitFixtureShouldStillReturnExitValue() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("result/linear-exit-result.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, singletonMap("done", true), "input");
  }
}
