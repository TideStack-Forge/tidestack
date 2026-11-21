package com.ouroboros.develop.logicflowdebug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.ouroboros.logicflow.compiler.FunctionalFlowCompiler;
import com.ouroboros.logicflow.model.*;
import com.ouroboros.util.Json;
import io.vavr.control.Try;

/** LF-DBG-PERF-001. Standalone warmed JVM; same model with absent, idle and capturing helper. */
public final class DebugPerformanceFixture {
  private static volatile long workResult;

  public static void main(String[] args) throws Exception {
    String mode = args[0];
    var model = new Model();
    var compiled = FunctionalFlowCompiler.INSTANCE.compile(model);
    int runs = 10000;
    long[] elapsed = new long[runs];
    try (var capture = mode.equals("baseline") ? null : new CaptureHarness()) {
      for (int i = 0; i < 10000; i++) compiled.apply(new Object[]{"order-123"}).get();
      System.gc();
      long heapBefore = heap();
      for (int i = 0; i < runs; i++) {
        if (capture != null && mode.equals("active") && i % 100 == 0) capture.next();
        long start = System.nanoTime();
        var value = compiled.apply(new Object[]{"order-123"}).get();
        elapsed[i] = System.nanoTime() - start;
        if (!value.equals("order-123")) throw new AssertionError("Business result changed");
      }
      Map<String, Object> resources = capture == null ? Map.of() : capture.finish(mode.equals("active"));
      System.gc();
      long heapAfter = heap();
      Arrays.sort(elapsed);
      var result = new LinkedHashMap<String, Object>();
      result.put("mode", mode); result.put("runs", runs); result.put("warmupRuns", 10000);
      result.put("p50Nanos", elapsed[runs / 2]); result.put("p95Nanos", elapsed[(int) (runs * .95)]);
      result.put("maxNanos", elapsed[runs - 1]); result.put("retainedHeapDeltaBytes", heapAfter - heapBefore);
      result.put("resources", resources);
      Files.writeString(Path.of(args[1]), Json.tryToJsonString(result).get());
    }
    Thread.sleep(250);
    long senders = Thread.getAllStackTraces().keySet().stream().filter(t -> t.isAlive() && t.getName().equals("logicflow-debug-sender")).count();
    if (senders != 0) throw new AssertionError("Sender leaked after close");
  }

  private static long heap() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }

  private static class CaptureHarness implements AutoCloseable {
    private final com.ouroboros.logicflow.debug.LogicFlowDebugRuntimeSupport support;
    private final com.ouroboros.logicflow.debug.LogicFlowDebugCaptureRuntime runtime;
    private final com.ouroboros.logicflow.diagnostic.FlowDebugTarget target =
        new com.ouroboros.logicflow.diagnostic.FlowDebugTarget("perf", "alice", "runtime", 1, "helper", 1, "incarnation");
    private String id;
    private long maxBytes;
    private int captures;

    CaptureHarness() {
      var bridge = org.mockito.Mockito.mock(com.ouroboros.mq.MQBridge.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
      support = new com.ouroboros.logicflow.debug.LogicFlowDebugRuntimeSupport(bridge, "perf", true);
      runtime = support.runtime();
    }
    void next() {
      if (id != null) drain();
      id = UUID.randomUUID().toString();
      runtime.start(id, target, "performance-flow", 1, 60000); captures++;
    }
    private void drain() {
      var status = runtime.status(id);
      if (status.completedRuns() != 100 || status.rejectedRuns() != 0 || status.activeInvocations() != 0 || !status.losses().isEmpty()) {
        throw new AssertionError("Capture lost executions: " + status);
      }
      maxBytes = Math.max(maxBytes, status.queuedBytes());
      runtime.stop(id, target, 2, status.highestSequence());
      var drained = runtime.status(id);
      if (drained.queuedBytes() != 0 || drained.acknowledgedThrough() != drained.highestSequence()) {
        throw new AssertionError("Acknowledged capture retained packets");
      }
    }
    Map<String, Object> finish(boolean active) {
      if (active) drain();
      return Map.of("captures", captures, "maxBufferedBytes", maxBytes, "maxWindowBytes", 16 * 1024 * 1024,
          "pendingBytesAfterDrain", active ? runtime.status(id).queuedBytes() : 0);
    }
    @Override public void close() { support.close(); }
  }

  private static final class Model implements FlowModel {
    final Node first = new Node("input");
    final Node second = new Node("output");
    final List<FlowConnection> connections = List.of(new Connection(first, second));
    Model() { first.outgoing = connections; second.incoming = connections; }
    @Override public String getName() { return "performance-flow"; }
    @Override public String getRevision() { return "fixture-v1"; }
    @Override public String getDescription() { return "Two synchronous nodes with deterministic CPU work"; }
    @Override public List<FlowNode> getNodes() { return List.of(first, second); }
    @Override public List<FlowConnection> getConnections() { return connections; }
    @Override public Boolean isEnabled() { return true; }
    @Override public Boolean isValid() { return true; }
    @Override public DataTypeEnum getInputDataType() { return DataTypeEnum.ANY; }
    @Override public DataTypeEnum getOutputDataType() { return DataTypeEnum.ANY; }
    @Override public Try<Object> execute(Object... inputs) { return FunctionalFlowCompiler.INSTANCE.compile(this).apply(inputs); }
    @Override public FlowNode getEntryNode() { return first; }
    @Override public FlowNode getExitNode() { return second; }
    @Override public FlowNode getNode(String name) { return name.equals(first.name) ? first : second; }
    @Override public List<com.ouroboros.logicflow.exception.DesignException> getErrors() { return List.of(); }
  }

  private static final class Node implements FlowNode {
    final String name;
    List<FlowConnection> incoming = List.of();
    List<FlowConnection> outgoing = List.of();
    Node(String name) { this.name = name; }
    @Override public String getName() { return name; }
    @Override public String getDescription() { return name; }
    @Override public String getType() { return "CpuFixture"; }
    @Override public List<FlowNodePort> getInputPorts() { return Collections.singletonList(null); }
    @Override public List<FlowNodePort> getOutputPorts() { return Collections.singletonList(null); }
    @Override public Boolean isAsync() { return false; }
    @Override public Boolean isEnabled() { return true; }
    @Override public Boolean isValid() { return true; }
    @Override public Try<List<Object>> execute(List<Object> inputs) {
      long result = 1;
      for (int i = 0; i < 500000; i++) result = result * 1664525 + 1013904223;
      workResult = result;
      return Try.success(inputs);
    }
    @Override public List<FlowConnection> getIncomingConnections() { return incoming; }
    @Override public List<FlowConnection> getOutgoingConnections() { return outgoing; }
    @Override public List<com.ouroboros.logicflow.exception.NodeDesignException> getErrors() { return List.of(); }
  }

  private record Connection(Node source, Node target) implements FlowConnection {
    @Override public String getLabel() { return ""; }
    @Override public String getDescription() { return ""; }
    @Override public String getSourceName() { return source.name; }
    @Override public Integer getSourceOutputPort() { return 0; }
    @Override public String getTargetName() { return target.name; }
    @Override public Integer getTargetInputPort() { return 0; }
    @Override public FlowNode getSourceNode() { return source; }
    @Override public FlowNode getTargetNode() { return target; }
  }
}
