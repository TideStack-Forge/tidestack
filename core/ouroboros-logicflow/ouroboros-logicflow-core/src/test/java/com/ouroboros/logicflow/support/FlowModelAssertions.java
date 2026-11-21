package com.ouroboros.logicflow.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.stream.Collectors;

import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.exception.ProblematicNodesDetectedException;
import com.ouroboros.logicflow.model.DefaultFlowModel;

public final class FlowModelAssertions {
  private FlowModelAssertions() {
  }

  public static void assertValid(DefaultFlowModel flowModel) {
    assertTrue("Expected model to be valid but found errors: " + describeErrors(flowModel), flowModel.isValid());
  }

  public static <T extends DesignException> T assertInvalid(DefaultFlowModel flowModel, Class<T> errorType) {
    assertFalse("Expected model to be invalid", flowModel.isValid());
    var error = getError(flowModel, errorType);
    assertNotNull("Expected error type " + errorType.getSimpleName() + " but found: " + describeErrors(flowModel), error);
    return error;
  }

  public static <T extends DesignException> T getError(DefaultFlowModel flowModel, Class<T> errorType) {
    return flowModel.getErrors().stream()
        .filter(errorType::isInstance)
        .map(errorType::cast)
        .findFirst()
        .orElse(null);
  }

  public static void assertExecuteSuccess(DefaultFlowModel flowModel, Object expected, Object... inputs) {
    var result = flowModel.execute(inputs);
    assertTrue("Expected execute() to succeed", result.isSuccess());
    assertEquals(expected, result.get());
  }

  public static Throwable assertExecuteFailure(DefaultFlowModel flowModel, Object... inputs) {
    var result = flowModel.execute(inputs);
    assertTrue("Expected execute() to fail", result.isFailure());
    var cause = result.getCause();
    assertNotNull("Expected execute() failure to expose a cause", cause);
    return cause;
  }

  public static FlowRuntimeException assertFlowRuntimeFailure(Throwable failure, String nodeName) {
    var current = failure;
    while (current != null && !(current instanceof FlowRuntimeException)) {
      current = current.getCause();
    }

    assertNotNull("Expected a FlowRuntimeException in the failure chain", current);
    var runtimeFailure = (FlowRuntimeException) current;

    if (nodeName != null) {
      assertNotNull("Expected FlowRuntimeException to expose its node", runtimeFailure.getNode());
      assertEquals(nodeName, runtimeFailure.getNode().getName());
    }

    return runtimeFailure;
  }

  public static NodeDesignException assertNodeDesignError(DefaultFlowModel flowModel) {
    assertFalse("Expected model to be invalid", flowModel.isValid());
    var directError = getError(flowModel, NodeDesignException.class);
    if (directError != null) {
      return directError;
    }
    var problematicNodes = getError(flowModel, ProblematicNodesDetectedException.class);
    assertNotNull("Expected node-design related errors but found: " + describeErrors(flowModel), problematicNodes);
    assertFalse("Expected problematic nodes list to be non-empty", problematicNodes.getNodeDesignExceptionList().isEmpty());
    return problematicNodes.getNodeDesignExceptionList().get(0);
  }

  private static String describeErrors(DefaultFlowModel flowModel) {
    return flowModel.getErrors().stream()
        .map(error -> {
          if (!(error instanceof ProblematicNodesDetectedException problematicNodes)) {
            return error.getClass().getSimpleName() + ":" + error.getMessage();
          }
          var nested = problematicNodes.getNodeDesignExceptionList().stream()
              .map(nodeError -> nodeError.getClass().getSimpleName() + ":" + nodeError.getMessage())
              .collect(Collectors.joining(","));
          return error.getClass().getSimpleName() + ":" + error.getMessage() + "[" + nested + "]";
        })
        .collect(Collectors.joining("; "));
  }
}
