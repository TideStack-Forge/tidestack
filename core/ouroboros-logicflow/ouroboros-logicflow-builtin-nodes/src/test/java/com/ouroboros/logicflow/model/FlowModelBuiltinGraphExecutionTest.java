package com.ouroboros.logicflow.model;

import java.util.LinkedHashMap;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelBuiltinGraphExecutionTest {
  @Test
  public void zeroInputSourceFeedingReachableMainPathShouldMergeWithBuiltinNodes() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/zero-input-upstream-source-participates-in-main-flow.json");

    FlowModelAssertions.assertValid(flowModel);
    var expected = new LinkedHashMap<String, Object>();
    expected.put("main", "entry");
    expected.put("side", "input");
    FlowModelAssertions.assertExecuteSuccess(flowModel, expected);
  }
}
