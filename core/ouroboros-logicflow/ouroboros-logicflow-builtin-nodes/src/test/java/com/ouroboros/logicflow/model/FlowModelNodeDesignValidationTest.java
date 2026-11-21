package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelNodeDesignValidationTest {
  @Test
  public void missingFlowNameCallFlowFixtureShouldReportNodeDesignError() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/node-design-missing-flow-name.json");

    var error = FlowModelAssertions.assertNodeDesignError(flowModel);

    assertEquals("callFlow", error.getNode().getType());
    assertTrue(error.getMessage().contains("流程名未配置"));
  }

  @Test
  public void selfCallFlowFixtureShouldReportSelfReferenceError() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/node-design-self-call-flow.json");

    var error = FlowModelAssertions.assertNodeDesignError(flowModel);

    assertEquals("callFlow", error.getNode().getType());
    assertTrue(error.getMessage().contains("不能引用当前流程自身"));
    assertTrue(error.getMessage().contains("node-design-self-call-flow"));
  }

  @Test
  public void missingTargetCallFlowFixtureShouldReportMissingOrDisabledTargetError() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/node-design-missing-target-flow.json");

    var error = FlowModelAssertions.assertNodeDesignError(flowModel);

    assertEquals("callFlow", error.getNode().getType());
    assertTrue(error.getMessage().contains("引用的流程不存在或未启用"));
    assertTrue(error.getMessage().contains("missing-child-flow"));
  }
}
