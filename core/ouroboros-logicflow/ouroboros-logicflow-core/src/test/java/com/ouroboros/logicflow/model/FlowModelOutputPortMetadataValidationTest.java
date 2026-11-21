package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelOutputPortMetadataValidationTest {
  private static final String SWITCH_EXCLUSIVE_GROUP_ID = "switch-branches";

  @Test
  public void defaultBranchWithoutExclusiveGroupShouldReportNodeDesignError() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/output-default-without-exclusive-group.json");

    var error = FlowModelAssertions.assertNodeDesignError(flowModel);
    assertTrue(error.getMessage().contains("defaultBranch"));
  }

  @Test
  public void duplicateDefaultBranchesInSameExclusiveGroupShouldReportNodeDesignError() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/output-duplicate-default-branch.json");

    var error = FlowModelAssertions.assertNodeDesignError(flowModel);
    assertTrue(error.getMessage().contains("exclusiveGroupId"));
  }

  @Test
  public void exclusiveGroupMetadataShouldAllowDualSuccessfulTerminalsWhenNodeOptsIn() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/output-metadata-exclusive-dual-success.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void exclusiveGroupMetadataShouldNotCreateMutualExclusionForOrdinaryNodes() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/output-metadata-nonexclusive-dual-success.json");

    assertFalse(flowModel.isValid());
    assertTrue("Expected successful-terminal conflict error but found: " + flowModel.getErrors(),
        flowModel.getErrors().stream()
            .anyMatch(error -> "SuccessfulTerminalConflictDetectedException".equals(error.getClass().getSimpleName())));
  }

  @Test
  public void switchNodeShouldBackfillExclusiveMetadataForLegacyBranches() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/exclusive-return-vs-exit.json");
    var switchNode = flowModel.getNode("routeSwitch");

    assertNotNull("Expected routeSwitch node to exist", switchNode);
    assertTrue(Boolean.TRUE.equals(switchNode.supportsSelectiveOutputMetadata()));
    assertTrue("Expected switch to have at least two output ports", switchNode.getOutputPorts().size() >= 2);

    var firstGroupId = switchNode.getOutputPorts().get(0).getExclusiveGroupId();
    var defaultPort = switchNode.getOutputPorts().get(switchNode.getOutputPorts().size() - 1);

    assertTrue("Expected switch branch ports to backfill a shared exclusive group", firstGroupId != null && !firstGroupId.trim().isEmpty());
    assertEquals("Expected switch branch ports to use the canonical exclusive group id",
        SWITCH_EXCLUSIVE_GROUP_ID, firstGroupId);
    assertTrue("Expected all switch ports to share the same exclusive group",
        switchNode.getOutputPorts().stream().allMatch(port -> firstGroupId.equals(port.getExclusiveGroupId())));
    assertTrue("Expected switch default port to be marked as defaultBranch", Boolean.TRUE.equals(defaultPort.isDefaultBranch()));
  }
}
