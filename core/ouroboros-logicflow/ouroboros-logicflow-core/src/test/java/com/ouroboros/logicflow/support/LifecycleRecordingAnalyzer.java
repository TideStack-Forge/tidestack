package com.ouroboros.logicflow.support;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

import io.vavr.control.Either;

import com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer;
import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;
import com.ouroboros.logicflow.diagnostic.FlowInputTransfer;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;

public class LifecycleRecordingAnalyzer implements FlowRuntimeAnalyzer {
  private static volatile Consumer<String> observer = event -> { };
  private static volatile Consumer<FlowExecutionContext> invocations = context -> { };
  private static volatile Consumer<FlowInputTransfer> transfers = transfer -> { };
  private static volatile BooleanSupplier enabled = () -> true;
  private static volatile java.util.function.Predicate<FlowModel> flowEnabled = model -> true;

  public static void flowsWhen(java.util.function.Predicate<FlowModel> condition) { flowEnabled = condition; }
  @Override public boolean isEnabled(FlowModel model) { return isEnabled() && flowEnabled.test(model); }

  public static void enabledWhen(BooleanSupplier condition) {
    enabled = condition;
  }

  @Override
  public boolean isEnabled() {
    return enabled.getAsBoolean();
  }

  public static void observe(Consumer<String> listener) {
    observer = listener;
  }

  public static void observeInvocations(Consumer<FlowExecutionContext> listener) {
    invocations = listener;
  }

  public static void observeTransfers(Consumer<FlowInputTransfer> listener) {
    transfers = listener;
  }

  @Override
  public void onFlowStart(FlowExecutionContext context, FlowModel model, List<Object> inputs) {
    invocations.accept(context);
  }

  @Override
  public void onInputTransfer(FlowExecutionContext context, FlowModel model, FlowInputTransfer transfer) {
    transfers.accept(transfer);
  }

  @Override
  public void onNodeBlocked(String sessionId, FlowModel model, FlowNode node, Throwable failure) {
    observer.accept(node.getName() + ":blocked");
  }

  @Override
  public void onNodeStart(String sessionId, FlowModel model, FlowNode node, List<Object> inputs) {
    observer.accept(node.getName() + ":started:" + inputs);
  }

  @Override
  public void onNodeSkipped(String sessionId, FlowModel model, FlowNode node, List<Object> inputs) {
    observer.accept(node.getName() + ":skipped");
  }

  @Override
  public void onNodeCompleted(String sessionId, FlowModel model, FlowNode node, List<Object> inputs, List<Object> outputs) {
    observer.accept(node.getName() + ":completed:" + inputs);
  }

  @Override
  public void onNodeFailed(String sessionId, FlowModel model, FlowNode node, List<Object> inputs, Throwable failure) {
    observer.accept(node.getName() + ":failed");
  }

  @Override
  public void onFlowCompleted(String sessionId, FlowModel model, List<Object> inputs,
                              List<Either<Throwable, Object>> results, Object result) {
    observer.accept("flow:completed");
  }
}
