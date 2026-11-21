package com.ouroboros.logicflow.model;

import java.util.Collections;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelExecutionBranchSkippingTest {
  @Test
  public void unselectedSwitchBranchShouldBeSkippedInsteadOfExecutingFailureNode() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("branch/switch-default-skips-unselected-branch.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, "safe-result", Collections.singletonMap("route", "default"));
  }
}
