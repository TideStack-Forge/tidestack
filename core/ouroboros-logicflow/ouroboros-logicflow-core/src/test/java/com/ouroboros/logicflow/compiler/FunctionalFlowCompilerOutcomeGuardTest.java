package com.ouroboros.logicflow.compiler;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

import com.ouroboros.logicflow.results.FlowOutcome;

public class FunctionalFlowCompilerOutcomeGuardTest {
  @Test
  public void selectSingleOutcomeShouldReturnTheOnlySuccessfulOutcome() {
    var outcome = FlowOutcome.returned("returnNode", "value");

    assertSame(outcome, FunctionalFlowCompiler.selectSingleOutcome(singletonList(outcome)));
  }

  @Test
  public void selectSingleOutcomeShouldRejectMultipleSuccessfulOutcomes() {
    var outcomes = Arrays.asList(
        FlowOutcome.exit("exitNode", "a"),
        FlowOutcome.returned("returnNode", "b"));

    try {
      FunctionalFlowCompiler.selectSingleOutcome(outcomes);
      fail("Expected multiple successful outcomes to be rejected");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("多个成功终点"));
    }
  }
}
