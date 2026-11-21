package com.ouroboros.logicflow.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;
import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;

class LogicFlowDebugCaptureRuntimeTest {
  private final AtomicLong clock = new AtomicLong(1000);
  private final FlowDebugTarget target = new FlowDebugTarget("test-app", "test", "container", 1, "helper", 100, "incarnation");
  private final LogicFlowDebugCaptureRuntime runtime = new LogicFlowDebugCaptureRuntime(clock::get, LogicFlowDebugCaptureRuntime.Limits.defaults());
  private final LogicFlowDebugRuntimeAnalyzerTest.StubFlowModel model = new LogicFlowDebugRuntimeAnalyzerTest.StubFlowModel();

  @Test
  void inactiveAndWrongFlowNeverEvaluateSnapshotSuppliers() {
    runtime.record(root("inactive"), model, null, "flow.started", () -> { throw new AssertionError("must not sample"); });
    runtime.start("capture", target, "different-flow", 1, 30000);
    runtime.record(root("wrong-flow"), model, null, "flow.started", () -> { throw new AssertionError("must not sample"); });
    assertEquals(0, runtime.status("capture").highestSequence());
  }

  @Test
  void startIsIdempotentAndAnOldRenewCannotExtendTheLease() {
    var initial = runtime.start("capture", target, "order-flow", 1, 30000);
    clock.addAndGet(Duration.ofSeconds(10).toNanos());
    assertEquals(20000, runtime.start("capture", target, "order-flow", 1, 30000).leaseRemainingMillis());
    assertEquals(20000, runtime.renew("capture", target, 1, 30000, 0).leaseRemainingMillis());
    assertEquals(30000, runtime.renew("capture", target, 2, 30000, 0).leaseRemainingMillis());
    assertThrows(IllegalStateException.class, () -> runtime.start("other", target, "order-flow", 1, 30000));
    assertThrows(IllegalStateException.class, () -> runtime.renew("wrong", target, 3, 30000, 0));
    assertEquals(initial.target(), runtime.status("capture").target());
  }

  @Test
  void twentyConcurrentExecutionsKeepIndependentRunsAndMonotonicCaptureSequences() {
    runtime.start("capture", target, "order-flow", 1, 30000);
    var work = new ArrayList<CompletableFuture<Void>>();
    for (int i = 0; i < 20; i++) {
      String id = "run-" + i;
      work.add(CompletableFuture.runAsync(() -> {
        runtime.record(root(id), model, null, "flow.started", Map::of);
        runtime.record(root(id), model, null, "flow.completed", Map::of);
      }));
    }
    CompletableFuture.allOf(work.toArray(CompletableFuture<?>[]::new)).join();
    var events = runtime.read("capture", target, 0, 100);
    assertEquals(40, events.size());
    assertEquals(20, events.stream().map(event -> event.getRunId()).distinct().count());
    for (int i = 0; i < events.size(); i++) {
      assertEquals(i + 1, events.get(i).getCaptureSeq());
    }
    assertEquals(20, runtime.status("capture").completedRuns());
  }

  @Test
  void childCompletionAndErrorScopeRetainTheRootExecution() {
    runtime.start("capture", target, "order-flow", 1, 30000);
    runtime.record(root("root"), model, null, "flow.started", Map::of);
    var child = new FlowExecutionContext("child", "root", "caller", "main");
    runtime.record(child, model, null, "flow.started", Map::of);
    runtime.record(child, model, null, "flow.completed", Map::of);
    runtime.record(root("root").withScope("error"), model, null, "node.completed", Map::of);
    runtime.record(root("root"), model, null, "flow.completed", Map::of);
    var events = runtime.read("capture", target, 0, 100);
    assertEquals(5, events.size());
    assertTrue(events.stream().allMatch(event -> "root".equals(event.getRunId())));
    assertEquals("caller", events.get(1).getCallerNodeName());
    assertEquals("error", events.get(3).getScope());
    assertEquals(1, runtime.status("capture").completedRuns());
  }

  @Test
  void packetsRetryUntilApplicationAckAndTheAckReleasesMemory() {
    runtime.start("capture", target, "order-flow", 1, 30000);
    runtime.record(root("run"), model, null, "flow.started", Map::of);
    assertEquals(1, runtime.duePackets().size());
    assertTrue(runtime.duePackets().isEmpty());
    clock.addAndGet(Duration.ofMillis(500).toNanos());
    assertEquals(1, runtime.duePackets().size());
    assertTrue(runtime.status("capture").queuedBytes() > 0);
    assertThrows(IllegalArgumentException.class, () -> runtime.acknowledge("capture", target, 2));
    runtime.acknowledge("capture", target, 1);
    assertEquals(0, runtime.status("capture").queuedBytes());
    assertTrue(runtime.duePackets().isEmpty());
  }

  @Test
  void expiryStopsSamplingAndCannotBeRevivedByDelayedRenewal() {
    runtime.start("capture", target, "order-flow", 1, 1000);
    runtime.record(root("run"), model, null, "flow.started", Map::of);
    clock.addAndGet(Duration.ofMillis(1001).toNanos());
    runtime.record(root("run"), model, null, "node.completed", () -> { throw new AssertionError("lease expired"); });
    var status = runtime.status("capture");
    assertFalse(status.acceptingRuns());
    assertEquals("capture-lease-expired", status.losses().getFirst().reason());
    assertThrows(IllegalStateException.class, () -> runtime.renew("capture", target, 2, 30000, 0));
  }

  @Test
  void fullRetryWindowReportsLossAndStopsAdmittingRuns() {
    var small = new LogicFlowDebugCaptureRuntime(clock::get,
        new LogicFlowDebugCaptureRuntime.Limits(2, 10, 10, 100, 65536, 2, 65536, 65536));
    small.start("capture", target, "order-flow", 1, 30000);
    small.record(root("run"), model, null, "flow.started", Map::of);
    small.record(root("run"), model, null, "node.started", Map::of);
    small.record(root("run"), model, null, "node.completed", Map::of);
    small.record(root("next"), model, null, "flow.started", () -> { throw new AssertionError("must not admit"); });
    var status = small.status("capture");
    assertFalse(status.acceptingRuns());
    assertEquals(3, status.highestSequence());
    assertEquals(1, status.losses().size());
    assertEquals("recording-capacity", status.losses().getFirst().reason());
    assertEquals(2, small.read("capture", target, 0, 100).size());
  }

  @Test
  void finalAdmittedRunClosesAdmissionButDrainsItsNestedExecutions() {
    var small = new LogicFlowDebugCaptureRuntime(clock::get,
        new LogicFlowDebugCaptureRuntime.Limits(2, 1, 10, 100, 65536, 20, 65536, 65536));
    var analyzer = new LogicFlowDebugRuntimeAnalyzer(small);
    assertFalse(analyzer.isEnabled());
    small.start("capture", target, "order-flow", 1, 30000);
    assertTrue(analyzer.isEnabled());
    small.record(root("root"), model, null, "flow.started", Map::of);
    assertFalse(small.status("capture").acceptingRuns());
    assertTrue(analyzer.isEnabled());
    var child = new FlowExecutionContext("child", "root", "caller", "main");
    small.record(child, model, null, "flow.started", Map::of);
    small.record(child, model, null, "flow.completed", Map::of);
    small.record(root("ignored"), model, null, "flow.started", () -> { throw new AssertionError("admission closed"); });
    small.record(root("root"), model, null, "flow.completed", Map::of);
    assertFalse(analyzer.isEnabled());
    assertEquals(1, small.status("capture").completedRuns());
    assertEquals(4, small.read("capture", target, 0, 100).size());
    assertTrue(small.status("capture").losses().isEmpty());
  }

  @Test
  void concurrencyLimitStopsAdmissionAndExistingRunCanComplete() {
    var small = new LogicFlowDebugCaptureRuntime(clock::get,
        new LogicFlowDebugCaptureRuntime.Limits(1, 10, 10, 100, 65536, 20, 65536, 65536));
    small.start("capture", target, "order-flow", 1, 30000);
    small.record(root("first"), model, null, "flow.started", Map::of);
    small.record(root("rejected"), model, null, "flow.started", () -> { throw new AssertionError("concurrency full"); });
    assertFalse(small.status("capture").acceptingRuns());
    assertEquals(1, small.status("capture").rejectedRuns());
    small.record(root("first"), model, null, "flow.completed", Map::of);
    assertEquals(1, small.status("capture").completedRuns());
    assertEquals(0, small.status("capture").activeInvocations());
    assertEquals(2, small.read("capture", target, 0, 100).size());
  }

  private FlowExecutionContext root(String id) {
    return new FlowExecutionContext(id, null, null, "main");
  }
}
