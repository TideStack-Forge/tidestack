package com.ouroboros.logicflow.manage.observation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;
import com.ouroboros.logicflow.manage.observation.FlowObservation.*;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.util.Json;

class FlowObservationCollectorTest {
  final MutableClock clock = new MutableClock();
  final AtomicLong nanos = new AtomicLong();
  final FlowObservationCollector collector = new FlowObservationCollector("boot", clock, nanos::get);
  final FlowModel flow = model("A");

  static FlowModel model(String name) {
    var value = mock(FlowModel.class);
    when(value.getName()).thenReturn(name); when(value.getRevision()).thenReturn("model-v1"); return value;
  }
  Policy policy(boolean diagnostic) { return new Policy(true, diagnostic, clock.millis() + 300_000, "capture", "operator", "incident", clock.millis()); }
  void enable(boolean diagnostic) { collector.accept(new Policies(1, Map.of("a", policy(diagnostic))), "policy-v1"); }
  FlowExecutionContext context(String id) { return new FlowExecutionContext(id, null, null, "main"); }
  void finish(String id) { nanos.addAndGet(10_000_000); collector.onFlowCompleted(context(id), flow, List.of("secret"), List.of(), "secret-result"); }

  @Test void defaultOffAndMonitoringDoNotCaptureBusinessDataOrOtherFlows() {
    assertFalse(collector.isEnabled(flow));
    collector.onFlowStart(context("off"), flow, List.of("secret")); finish("off");
    assertTrue(collector.windows().isEmpty());
    enable(false);
    assertTrue(collector.isEnabled(flow)); assertFalse(collector.isEnabled(model("B")));
    collector.onFlowStart(context("on"), flow, List.of("secret")); finish("on");
    assertEquals(1, collector.windows().getFirst().completed());
    assertTrue(collector.pending().isEmpty());
    assertFalse(Json.toJsonString(collector.windows()).contains("secret"));
  }

  @Test void diagnosticRecordsTerminalKindsAndOnlyExceptionType() {
    enable(true);
    var context = context("run");
    var node = mock(FlowNode.class); when(node.getName()).thenReturn("work"); when(node.getType()).thenReturn("script");
    collector.onFlowStart(context, flow, List.of("private-input"));
    collector.onNodeStart(context, flow, node, List.of("private-input"));
    nanos.addAndGet(8_000_000);
    collector.onNodeFailed(context, flow, node, List.of("private-input"), new IllegalStateException("secret-message"));
    collector.onNodeSkipped(context, flow, node, List.of("private"));
    collector.onNodeBlocked(new FlowExecutionContext(context.invocationId(), null, null, "error"), flow, node, new IllegalArgumentException("secret"));
    collector.onFlowFailed(context, flow, List.of("private"), List.of(), new IllegalStateException("secret-message"));
    var run = collector.pending().getFirst();
    assertEquals(List.of("failed", "skipped", "blocked"), run.nodes().stream().map(Span::result).toList());
    assertEquals("IllegalStateException", run.exceptionType());
    assertEquals(8, run.nodes().getFirst().durationMillis());
    assertEquals(1, collector.windows().getFirst().failed());
    assertFalse(Json.toJsonString(run).contains("secret")); assertFalse(Json.toJsonString(run).contains("private"));
    collector.acknowledgeRun(run.id()); assertTrue(collector.pending().isEmpty());
  }

  @Test void diagnosticLeaseAndBudgetDoNotDisableBasicMonitoring() {
    enable(true);
    for (int i = 0; i < 101; i++) { collector.onFlowStart(context("r" + i), flow, List.of()); finish("r" + i); }
    assertEquals(100, collector.pending().size());
    assertEquals(101, collector.windows().getFirst().completed());
    assertEquals("capacity", collector.status(null).diagnostics().get("a"));
    collector.unavailable(); enable(true);
    collector.onFlowStart(context("recovered"), flow, List.of()); finish("recovered");
    assertEquals(100, collector.pending().size());
    assertEquals("capacity", collector.status(null).diagnostics().get("a"));
    clock.advance(300_001);
    collector.onFlowStart(context("expired"), flow, List.of()); finish("expired");
    assertEquals(100, collector.pending().size());
    assertEquals("expired", collector.status(null).diagnostics().get("a"));
    assertEquals(2, collector.status(null).rejected());
  }

  @Test void closedOrExpiredDiagnosticsHaveBoundedDrainAndHistoricalSamplesRemain() {
    enable(true); collector.onFlowStart(context("old"), flow, List.of());
    collector.accept(new Policies(1, Map.of()), "off");
    collector.onFlowStart(context("new"), flow, List.of());
    clock.advance(30_001); collector.expire();
    assertEquals(0, collector.status(null).active());
    assertEquals("incomplete", collector.pending().getFirst().result());
    assertEquals(1, collector.windows().getFirst().incomplete());
    finish("old"); assertEquals(0, collector.windows().getFirst().completed());
    enable(true); collector.onFlowStart(context("lease"), flow, List.of());
    clock.advance(300_001); collector.accept(new Policies(1, Map.of("a", new Policy(true, true, 300_000, "capture", "op", "incident", 0))), "same");
    clock.advance(30_001); collector.expire();
    assertEquals(2, collector.pending().size());
  }

  @Test void parentOnlyLinksToDetailedInvocationAndQueuesAreBounded() {
    enable(false); collector.onFlowStart(context("parent"), flow, List.of());
    enable(true);
    var child = new FlowExecutionContext("child", "parent", "call", "main");
    collector.onFlowStart(child, flow, List.of()); finish("child");
    assertNull(collector.pending().getFirst().parentInvocationId());
    collector.onFlowStart(context("detailed-parent"), flow, List.of());
    var nested = new FlowExecutionContext("nested", "detailed-parent", "call", "main");
    collector.onFlowStart(nested, flow, List.of()); finish("nested");
    assertEquals("detailed-parent", collector.pending().getLast().parentInvocationId());
    var node = mock(FlowNode.class); when(node.getName()).thenReturn("node"); when(node.getType()).thenReturn("type");
    for (int i = 0; i < 300; i++) { collector.onNodeSkipped(context("detailed-parent"), flow, node, List.of()); }
    finish("detailed-parent");
    assertEquals(256, collector.pending().getLast().nodes().size()); assertTrue(collector.pending().getLast().truncated());
    for (int batch = 0; batch < 2; batch++) {
      collector.accept(new Policies(1, Map.of("a", new Policy(true, true, clock.millis() + 60_000, "batch" + batch, "op", "incident", 0))), "v" + batch);
      for (int i = 0; i < 100; i++) { String id = batch + "-" + i; collector.onFlowStart(context(id), flow, List.of()); finish(id); }
    }
    assertEquals(128, collector.pending().size()); assertTrue(collector.status(null).dropped() > 0);
  }

  @Test void activeAndWindowCapacityFailuresRemainVisibleAndPersistenceRetriesAreIdempotent() {
    enable(false);
    for (int i = 0; i < 1025; i++) { collector.onFlowStart(context("active" + i), flow, List.of()); }
    assertEquals(1024, collector.status(null).active()); assertEquals(1, collector.status(null).rejected());
    collector.unavailable(); clock.advance(30_001); collector.expire();
    assertEquals(0, collector.status(null).active());
    var snapshot = collector.windows().getFirst();
    assertEquals(1024, snapshot.incomplete());
    assertEquals(snapshot, collector.windows().getFirst());
    clock.advance(180_000); collector.acknowledgeWindow(snapshot); assertTrue(collector.windows().isEmpty());
    enable(false);
    for (int i = 0; i < 513; i++) { clock.advance(60_000); collector.onFlowStart(context("w" + i), flow, List.of()); finish("w" + i); }
    assertEquals(512, collector.windows().size()); assertEquals(1, collector.status(null).dropped());
  }

  static final class MutableClock extends Clock {
    long millis = 1_000_000;
    void advance(long amount) { millis += amount; }
    @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    @Override public long millis() { return millis; }
  }
}
