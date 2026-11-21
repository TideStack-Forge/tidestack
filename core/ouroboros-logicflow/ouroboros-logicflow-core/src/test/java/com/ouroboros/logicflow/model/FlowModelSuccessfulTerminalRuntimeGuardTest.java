package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.compiler.FunctionalFlowCompiler;
import com.ouroboros.logicflow.exception.SuccessfulTerminalConflictDetectedException;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.support.FlowModelFixtures;

public class FlowModelSuccessfulTerminalRuntimeGuardTest {
  @Test
  public void executionShouldFailIfMultipleSuccessTerminalsSlipPastValidation() {
    var flowModel = FlowModelFixtures.loadValidationFixture("invalid/dual-success-ambiguous.json");

    assertFalse("Expected dual-success fixture to be invalid before bypassing validation", flowModel.isValid());
    flowModel.errors.removeIf(SuccessfulTerminalConflictDetectedException.class::isInstance);
    assertTrue("Expected only successful-terminal conflict errors to be removed", flowModel.isValid());

    flowModel.executeFunction = FunctionalFlowCompiler.INSTANCE.compile(flowModel);

    var failure = FlowModelAssertions.assertExecuteFailure(flowModel, "input");

    assertTrue("Expected runtime guard to expose a multiple-success-terminal failure but found: " + failure.getMessage(),
        failure.getMessage().contains("多个成功终点"));
  }
}
