package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;
import com.ouroboros.logicflow.support.TestFlowRuntimeAnalyzer;

public class FlowModelErrorFlowExecutionTest {
  @Before
  public void setUp() {
    TestFlowRuntimeAnalyzer.reset();
  }

  @Test
  public void mainFlowFailureShouldRecoverWhenErrorFlowReturns() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("error-flow/main-failure-recovers.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, "failingScript", "input");
  }

  @Test
  public void mainFlowFailureShouldStillFailWhenErrorFlowThrows() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("error-flow/main-failure-catch-failure.json");

    FlowModelAssertions.assertValid(flowModel);
    var failure = FlowModelAssertions.assertExecuteFailure(flowModel, "input");
    var runtimeFailure = FlowModelAssertions.assertFlowRuntimeFailure(failure, "errorHandler");

    assertTrue(runtimeFailure.getCause().getMessage().contains("catch-boom"));
  }

  @Test
  public void mainFlowSuccessShouldIgnoreErrorFlowScope() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("error-flow/main-success-ignores-error-flow.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteSuccess(flowModel, "input", "input");
  }

  @Test
  public void legacyRuntimeFailureShouldRemainFailedWithoutErrorEntry() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("runtime/script-node-runtime-failure.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertFlowRuntimeFailure(
        FlowModelAssertions.assertExecuteFailure(flowModel, "input"),
        "failingScript");
  }

  @Test
  public void errorFlowShouldReceiveStandardizedBusinessFailureContext() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("error-flow/main-business-failure-context.json");

    FlowModelAssertions.assertValid(flowModel);
    var result = flowModel.execute("input").get();
    assertTrue("Expected business failure context to be returned as a map", result instanceof Map);

    var context = (Map<?, ?>) result;
    assertEquals("TestErrorCodeException", context.get("exceptionType"));
    assertEquals("biz-boom", context.get("exceptionMessage"));
    assertEquals(4321, context.get("exceptionCode"));
    assertEquals("main-business-failure-context", context.get("flowName"));
    assertEquals("failingScript", context.get("nodeName"));
    assertEquals(Collections.singletonList("input"), context.get("nodeInputs"));
    assertEquals(Collections.singletonList("input"), context.get("flowInputs"));
  }

  @Test
  public void errorFlowShouldFailClosedWhenNoSuccessfulTerminalIsReached() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("error-flow/main-failure-no-recovery-terminal.json");

    FlowModelAssertions.assertValid(flowModel);
    var failure = FlowModelAssertions.assertExecuteFailure(flowModel, "input");

    assertTrue("Expected failure to explain missing successful terminal but found: " + failure.getMessage(),
        failure.getMessage().contains("成功终点"));
  }

  @Test
  public void errorFlowFailureShouldNotReenterErrorEntryNode() {
    var flowModel = FlowModelFixtures.loadExecutionFixture("error-flow/main-failure-catch-failure.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteFailure(flowModel, "input");

    assertEquals(1, TestFlowRuntimeAnalyzer.getNodeStartCount("errorEntry"));
    assertEquals(1, TestFlowRuntimeAnalyzer.getNodeStartCount("errorHandler"));
    assertEquals(1, TestFlowRuntimeAnalyzer.getNodeFailureCount("errorHandler"));
  }
}
