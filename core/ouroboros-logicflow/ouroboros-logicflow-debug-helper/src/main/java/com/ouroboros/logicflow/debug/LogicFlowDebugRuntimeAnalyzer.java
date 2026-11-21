package com.ouroboros.logicflow.debug;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.vavr.control.Either;

import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;
import com.ouroboros.logicflow.diagnostic.FlowInputTransfer;
import com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;

/** SPI observer; payload suppliers are evaluated only inside an admitted capture. */
public class LogicFlowDebugRuntimeAnalyzer implements FlowRuntimeAnalyzer {
  private final DebugSnapshotter snapshots = new DebugSnapshotter(DebugSnapshotter.Limits.defaults());
  private final Supplier<LogicFlowDebugCaptureRuntime> runtime;

  public LogicFlowDebugRuntimeAnalyzer() {
    runtime = LogicFlowDebugRuntimeSupport::currentRuntime;
  }

  LogicFlowDebugRuntimeAnalyzer(LogicFlowDebugCaptureRuntime runtime) {
    this.runtime = () -> runtime;
  }

  @Override
  public boolean isEnabled() {
    var current = runtime.get();
    return current != null && current.isRecording();
  }

  @Override
  public void onFlowStart(FlowExecutionContext context, FlowModel model, List<Object> inputs) {
    record(context, model, null, "flow.started", () -> inputs(inputs));
  }

  @Override
  public void onFlowFailed(FlowExecutionContext context, FlowModel model, List<Object> inputs,
                           List<Either<Throwable, Object>> results, Throwable failure) {
    record(context, model, null, "flow.failed", () -> failure(inputs, failure));
  }

  @Override
  public void onFlowCompleted(FlowExecutionContext context, FlowModel model, List<Object> inputs,
                              List<Either<Throwable, Object>> results, Object output) {
    record(context, model, null, "flow.completed", () -> completed(inputs, output));
  }

  @Override
  public void onNodeStart(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs) {
    record(context, model, node, "node.started", () -> inputs(inputs));
  }

  @Override
  public void onNodeSkipped(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs) {
    record(context, model, node, "node.skipped", () -> inputs(inputs));
  }

  @Override
  public void onNodeFailed(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs, Throwable failure) {
    record(context, model, node, "node.failed", () -> failure(inputs, failure));
  }

  @Override
  public void onNodeBlocked(FlowExecutionContext context, FlowModel model, FlowNode node, Throwable failure) {
    record(context, model, node, "node.blocked", () -> failure(null, failure));
  }

  @Override
  public void onNodeCompleted(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs, List<Object> outputs) {
    record(context, model, node, "node.completed", () -> completed(inputs, outputs));
  }

  @Override
  public void onInputTransfer(FlowExecutionContext context, FlowModel model, FlowInputTransfer transfer) {
    record(context, model, null, "input.transfer", () -> Map.of("transfer", transfer));
  }

  private void record(FlowExecutionContext context, FlowModel model, FlowNode node, String kind,
                       Supplier<Map<String, Object>> payload) {
    var current = runtime.get();
    if (current != null) {
      current.record(context, model, node, kind, payload);
    }
  }

  private Map<String, Object> inputs(Object inputs) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("inputSnapshots", snapshots.capture(inputs));
    return payload;
  }

  private Map<String, Object> completed(Object inputs, Object outputs) {
    var payload = inputs(inputs);
    payload.put("outputSnapshots", snapshots.capture(outputs));
    return payload;
  }

  private Map<String, Object> failure(Object inputs, Throwable failure) {
    var payload = inputs(inputs);
    payload.put("exceptionSnapshot", snapshots.captureException(failure));
    return payload;
  }
}
