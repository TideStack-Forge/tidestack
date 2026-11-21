package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;
import com.ouroboros.logicflow.support.TestFlowRuntimeAnalyzer;

public class FlowModelExecutionInputContractTest {
  @Test
  public void insufficientInputCountShouldFailBeforeNormalExecution() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("input/linear-input-count-required.json");

    FlowModelAssertions.assertValid(flowModel);
    var failure = FlowModelAssertions.assertExecuteFailure(flowModel);

    assertTrue(failure.getMessage().contains("输入参数数量不正确"));
  }

  @Test
  public void nonEntryRootShouldReceiveTheSamePositionalInputsAsTheEntry() {
    var flowModel = externalRootFlow();

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, Map.of("first", 1, "second", 2, "overlap", "second"),
        Map.of("first", 1, "overlap", "first"), Map.of("second", 2, "overlap", "second"));
  }

  @Test
  public void inputCountMustCoverAllExternalRootsBeforeAnyNodeRuns() {
    var flowModel = externalRootFlow();
    FlowModelAssertions.assertValid(flowModel);
    TestFlowRuntimeAnalyzer.reset();

    var failure = FlowModelAssertions.assertExecuteFailure(flowModel, Map.of("first", 1));

    assertTrue(failure.getMessage().contains("输入参数数量不正确"));
    FlowModelAssertions.assertFlowRuntimeFailure(failure, "sideRoot");
    assertEquals(0, TestFlowRuntimeAnalyzer.getNodeStartCount("entry"));
    assertEquals(0, TestFlowRuntimeAnalyzer.getNodeStartCount("sideRoot"));
  }

  @Test
  public void errorScopeRootsShouldReceiveErrorContextInsteadOfBusinessInputs() {
    var model = FlowModelFactory.build(FlowModelMeta.builder()
        .name("error-root-inputs")
        .inputDataType("string")
        .outputDataType("map")
        .entryNode("failure")
        .exitNode("failure")
        .errorEntryNode("errorEntry")
        .node(FlowNodeMeta.builder().name("failure").type("script")
            .extraProp("language", "groovy").extraProp("script", "throw new RuntimeException('boom')")
            .inputPort("business", "string")
            .outputPort(FlowNodePortMeta.builder().name("result").dataType("map").build()).build())
        .node(FlowNodeMeta.builder().name("errorEntry").type("const")
            .extraProp("dataType", "map").extraProp("value", Map.of("recovered", true)).build())
        .node(FlowNodeMeta.builder().name("errorSide").type("mutex").inputPort("error", "map").build())
        .node(FlowNodeMeta.builder().name("errorMerge").type("merge")
            .inputPort("entry", "map").inputPort("side", "map").build())
        .node(FlowNodeMeta.builder().name("errorReturn").type("return").inputPort("result", "map").build())
        .connection(connection("errorEntry", "errorMerge", 0))
        .connection(connection("errorSide", "errorMerge", 1))
        .connection(connection("errorMerge", "errorReturn", 0))
        .build(), NodeReferenceCatalog.empty());

    FlowModelAssertions.assertValid(model);
    var result = model.execute("business-input").get();
    assertTrue(result instanceof Map<?, ?>);
    var context = (Map<?, ?>) result;
    assertEquals(true, context.get("recovered"));
    assertEquals("failure", context.get("nodeName"));
    assertEquals(List.of("business-input"), context.get("flowInputs"));
  }

  private static DefaultFlowModel externalRootFlow() {
    return FlowModelFactory.build(FlowModelMeta.builder()
        .name("external-root-inputs")
        .inputDataType("map")
        .outputDataType("map")
        .entryNode("entry")
        .exitNode("joined")
        .node(FlowNodeMeta.builder().name("entry").type("mutex").inputPort("first", "map").build())
        .node(FlowNodeMeta.builder().name("sideRoot").type("merge")
            .inputPort("first", "map").inputPort("second", "map").build())
        .node(FlowNodeMeta.builder().name("joined").type("merge")
            .inputPort("entry", "map").inputPort("side", "map").build())
        .connection(connection("entry", "joined", 0))
        .connection(connection("sideRoot", "joined", 1))
        .build(), NodeReferenceCatalog.empty());
  }

  private static FlowConnectionMeta connection(String source, String target, int port) {
    return FlowConnectionMeta.builder().sourceNode(source).sourceOutputPort(0)
        .targetNode(target).targetInputPort(port).build();
  }
}
