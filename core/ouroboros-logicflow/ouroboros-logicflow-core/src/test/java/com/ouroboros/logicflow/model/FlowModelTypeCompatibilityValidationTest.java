package com.ouroboros.logicflow.model;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import com.ouroboros.logicflow.exception.InputOutputDataTypeMismatchedException;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelTypeCompatibilityValidationTest {
  @Test
  public void incompatibleConnectedPortTypesShouldBeRejected() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/type-mismatch-basic.json");

    FlowModelAssertions.assertInvalid(flowModel, InputOutputDataTypeMismatchedException.class);
  }

  @Test
  public void nonEntryRootShouldValidateAgainstFlowInputType() {
    var flowModel = FlowModelFactory.build(FlowModelMeta.builder()
        .name("non-entry-root-input-type-mismatch")
        .inputDataType("string")
        .entryNode("entry")
        .exitNode("joined")
        .node(FlowNodeMeta.builder().name("entry").type("const")
            .extraProp("dataType", "map").extraProp("value", Map.of()).build())
        .node(FlowNodeMeta.builder().name("side").type("mutex").inputPort("input", "map").build())
        .node(FlowNodeMeta.builder().name("joined").type("merge")
            .inputPort("entry", "map").inputPort("side", "map").build())
        .connection(FlowConnectionMeta.builder().sourceNode("entry").sourceOutputPort(0)
            .targetNode("joined").targetInputPort(0).build())
        .connection(FlowConnectionMeta.builder().sourceNode("side").sourceOutputPort(0)
            .targetNode("joined").targetInputPort(1).build())
        .build(), NodeReferenceCatalog.empty());

    FlowModelAssertions.assertInvalid(flowModel, InputOutputDataTypeMismatchedException.class);
  }

  @Test
  public void disabledExplicitExitNodeShouldBeIgnoredByTypeCompatibilityValidation() {
    var flowModel = FlowModelFixtures.loadValidationFixture("valid/disabled-exit-type-mismatch-ignored.json");

    FlowModelAssertions.assertValid(flowModel);
  }

  @Test
  public void returnOnlyTerminalShouldValidateAgainstItsReturnPayloadType() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/return-only-type-mismatch.json");

    FlowModelAssertions.assertInvalid(flowModel, InputOutputDataTypeMismatchedException.class);
  }

  @Test
  public void errorEntryNodeShouldValidateAgainstStandardizedErrorContextType() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/error-entry-type-mismatch.json");

    FlowModelAssertions.assertInvalid(flowModel, InputOutputDataTypeMismatchedException.class);
  }

  @Test
  public void errorEntryNodeShouldRespectItsActualRuntimeInputPortContract() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/error-entry-actual-multi-input-node.json");

    FlowModelAssertions.assertInvalid(flowModel, InputOutputDataTypeMismatchedException.class);
  }

  @Test
  public void explicitExitNodeShouldValidateAgainstItsActualReturnedOutputSlot() {
    var flowModel = FlowModelFactory.build(FlowModelMeta.builder()
        .name("multi-port-exit-type-mismatch")
        .entryNode("exitNode")
        .exitNode("exitNode")
        .outputDataType("integer")
        .node(FlowNodeMeta.builder()
            .name("exitNode")
            .type("metadataParallelTestNode")
            .outputPorts(Arrays.asList(
                FlowNodePortMeta.builder().name("left").dataType("string").build(),
                FlowNodePortMeta.builder().name("right").dataType("integer").build()))
            .build())
        .build(), NodeReferenceCatalog.empty());

    FlowModelAssertions.assertInvalid(flowModel, InputOutputDataTypeMismatchedException.class);
  }
}
