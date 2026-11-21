package com.ouroboros.logicflow.diagnostic;

import java.util.List;

import io.vavr.control.Either;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;

public interface FlowRuntimeAnalyzer {
  /** Evaluated before each invocation; inactive observers allocate no execution context. */
  default boolean isEnabled() {
    return true;
  }

  /** Select observers at invocation entry while preserving existing global observers. */
  default boolean isEnabled(FlowModel model) {
    return isEnabled();
  }

  default void onFlowStart(FlowExecutionContext context, FlowModel model, List<Object> inputs) {
    onFlowStart(context.invocationId(), model, inputs);
  }

  default void onFlowFailed(FlowExecutionContext context, FlowModel model, List<Object> inputs,
                            List<Either<Throwable, Object>> nodeResults, Throwable failure) {
    onFlowFailed(context.invocationId(), model, inputs, nodeResults, failure);
  }

  default void onFlowCompleted(FlowExecutionContext context, FlowModel model, List<Object> inputs,
                               List<Either<Throwable, Object>> nodeResults, Object result) {
    onFlowCompleted(context.invocationId(), model, inputs, nodeResults, result);
  }

  default void onNodeStart(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs) {
    onNodeStart(context.invocationId(), model, node, inputs);
  }

  default void onNodeSkipped(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs) {
    onNodeSkipped(context.invocationId(), model, node, inputs);
  }

  default void onNodeFailed(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs, Throwable failure) {
    onNodeFailed(context.invocationId(), model, node, inputs, failure);
  }

  default void onNodeBlocked(FlowExecutionContext context, FlowModel model, FlowNode node, Throwable failure) {
    onNodeBlocked(context.invocationId(), model, node, failure);
  }

  default void onNodeCompleted(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs, List<Object> outputs) {
    onNodeCompleted(context.invocationId(), model, node, inputs, outputs);
  }

  default void onInputTransfer(FlowExecutionContext context, FlowModel model, FlowInputTransfer transfer) {
  }

  default void onFlowStart(String sessionId, FlowModel flowModel, List<Object> inputs) {
  }

  default void onFlowFailed(String sessionId, FlowModel flowModel, List<Object> inputs, List<Either<Throwable, Object>> nodeResults, Throwable throwable) {
  }

  default void onFlowCompleted(String sessionId, FlowModel flowModel, List<Object> flowInputs, List<Either<Throwable, Object>> nodeResults, Object flowResult) {
  }

  default void onNodeStart(String sessionId, FlowModel flowModel, FlowNode flowNode, List<Object> nodeInputs) {
  }

  default void onNodeSkipped(String sessionId, FlowModel flowModel, FlowNode flowNode, List<Object> nodeInputs) {
  }

  default void onNodeFailed(String sessionId, FlowModel flowModel, FlowNode flowNode, List<Object> nodeInputs, Throwable throwable) {
  }

  /** The node did not execute because an upstream dependency failed. */
  default void onNodeBlocked(String sessionId, FlowModel flowModel, FlowNode flowNode, Throwable throwable) {
  }

  default void onNodeCompleted(String sessionId, FlowModel flowModel, FlowNode flowNode, List<Object> nodeInputs, List<Object> nodeResult) {
  }
}
