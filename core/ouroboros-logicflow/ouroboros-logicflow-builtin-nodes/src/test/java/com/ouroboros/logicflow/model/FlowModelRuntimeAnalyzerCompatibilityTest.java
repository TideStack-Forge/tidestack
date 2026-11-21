package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.ouroboros.logicflow.results.FlowOutcome;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;
import com.ouroboros.logicflow.support.TestFlowRuntimeAnalyzer;

public class FlowModelRuntimeAnalyzerCompatibilityTest {
  @Before
  public void setUp() {
    TestFlowRuntimeAnalyzer.reset();
  }

  @Test
  public void analyzerShouldReceiveUnwrappedReturnValueOnFlowCompleted() throws InterruptedException {
    var flowModel = FlowModelFixtures.loadExecutionFixture("result/return-terminal-result.json");

    FlowModelAssertions.assertValid(flowModel);
    var result = flowModel.execute("input").get();

    assertEquals("input", result);

    var analyzerResult = TestFlowRuntimeAnalyzer.awaitFlowResult();

    assertEquals("input", analyzerResult);
    assertFalse("Flow analyzer should receive the public result value, not the internal FlowOutcome envelope",
        analyzerResult instanceof FlowOutcome);
  }

  @Test
  public void analyzerShouldReceiveRecoveredValueWhenErrorFlowReturns() throws InterruptedException {
    var flowModel = FlowModelFixtures.loadExecutionFixture("error-flow/main-failure-recovers.json");

    FlowModelAssertions.assertValid(flowModel);
    assertEquals("failingScript", flowModel.execute("input").get());
    assertEquals("failingScript", TestFlowRuntimeAnalyzer.awaitFlowResult());
  }

  @Test
  public void analyzerShouldReceiveFinalFailureWhenErrorFlowFails() throws InterruptedException {
    var flowModel = FlowModelFixtures.loadExecutionFixture("error-flow/main-failure-catch-failure.json");

    FlowModelAssertions.assertValid(flowModel);
    FlowModelAssertions.assertExecuteFailure(flowModel, "input");

    var failure = TestFlowRuntimeAnalyzer.awaitFlowFailure();
    var runtimeFailure = FlowModelAssertions.assertFlowRuntimeFailure(failure, "errorHandler");
    assertTrue(runtimeFailure.getCause().getMessage().contains("catch-boom"));
  }
}
