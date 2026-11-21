package com.ouroboros.logicflow.manage.observation;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import io.vavr.control.Either;

import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;
import com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer;
import com.ouroboros.logicflow.manage.observation.FlowObservation.*;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.logicflow.model.FlowNode;

/** Process-local bounded collection. All callbacks touch memory only. */
public final class FlowObservationCollector implements FlowRuntimeAnalyzer {
  private static final int MAX_ACTIVE = 1024;
  private static final int MAX_WINDOWS = 512;
  private static final int MAX_PENDING_RUNS = 128;
  private static final int MAX_SPANS = 256;
  private static final int MAX_ACTIVE_SPANS = 8192;
  private final Object lock = new Object();
  private final String bootId;
  private final Clock clock;
  private final LongSupplier nanoTime;
  private final Map<String, Invocation> active = new HashMap<>();
  private final Map<String, MutableWindow> windows = new LinkedHashMap<>();
  private final ArrayDeque<Run> pending = new ArrayDeque<>();
  private final Map<String, Integer> captureCounts = new HashMap<>();
  private volatile Policies policies = new Policies(1, Map.of());
  private volatile String revision = "unobserved";
  private int activeSpans;
  private long rejected;
  private long dropped;

  public FlowObservationCollector(String bootId) { this(bootId, Clock.systemUTC(), System::nanoTime); }
  public FlowObservationCollector(String bootId, Clock clock, LongSupplier nanoTime) {
    this.bootId = bootId; this.clock = clock; this.nanoTime = nanoTime;
  }

  public void accept(Policies next, String version) {
    synchronized (lock) {
      policies = next;
      revision = version;
      captureCounts.keySet().retainAll(next.flows().values().stream().map(Policy::captureId).toList());
      long now = clock.millis();
      active.values().forEach(invocation -> {
        var policy = policy(invocation.name);
        if (!policy.monitorEnabled() || invocation.detailed && (!policy.diagnosticAt(now) || !policy.captureId().equals(invocation.captureId))) {
          invocation.drainUntil = Math.min(invocation.drainUntil, now + 30_000);
        }
      });
    }
  }

  public void unavailable() {
    synchronized (lock) {
      policies = new Policies(1, Map.of());
      revision = "unavailable";
      long drainUntil = clock.millis() + 30_000;
      active.values().forEach(invocation -> invocation.drainUntil = Math.min(invocation.drainUntil, drainUntil));
    }
  }
  private Policy policy(String name) { return policies.flows().getOrDefault(name, Policy.off()); }
  @Override public boolean isEnabled() { return !policies.flows().isEmpty(); }
  @Override public boolean isEnabled(FlowModel model) { return policy(key(model)).monitorEnabled(); }

  @Override
  public void onFlowStart(FlowExecutionContext context, FlowModel model, List<Object> inputs) {
    synchronized (lock) {
      String name = key(model);
      var policy = policy(name);
      if (!policy.monitorEnabled()) { return; }
      if (active.size() >= MAX_ACTIVE) { rejected++; return; }
      long now = clock.millis();
      boolean detailed = policy.diagnosticAt(now) && captureCounts.getOrDefault(policy.captureId(), 0) < 100;
      if (detailed) { captureCounts.merge(policy.captureId(), 1, Integer::sum); }
      if (policy.diagnosticAt(now) && !detailed) { rejected++; }
      String windowId = FlowModelDefinitions.hash(bootId + "|" + name + "|" + model.getRevision() + "|" + revision + "|" + now / 60_000);
      if (!windows.containsKey(windowId) && windows.size() >= MAX_WINDOWS) { dropped++; return; }
      windows.computeIfAbsent(windowId, id -> new MutableWindow(id, name, model.getRevision(), revision, now / 60_000 * 60_000));
      active.put(context.invocationId(), new Invocation(context, name, model.getRevision(), revision, windowId,
          now, nanoTime.getAsLong(), detailed, policy.captureId(), policy.diagnosticExpiresAt()));
    }
  }

  @Override public void onNodeStart(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs) {
    synchronized (lock) {
      var invocation = active.get(context.invocationId());
      if (invocation == null || !invocation.detailed) { return; }
      String identity = context.scope() + ":" + node.getName();
      if (activeSpans >= MAX_ACTIVE_SPANS || invocation.nodes.size() + invocation.startedNodes.size() >= MAX_SPANS) { invocation.truncated = true; return; }
      if (!invocation.startedNodes.containsKey(identity)) { activeSpans++; }
      invocation.startedNodes.put(identity, nanoTime.getAsLong());
    }
  }
  @Override public void onNodeCompleted(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs, List<Object> outputs) {
    terminal(context, node, "completed", null);
  }
  @Override public void onNodeFailed(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs, Throwable failure) {
    terminal(context, node, "failed", failure);
  }
  @Override public void onNodeSkipped(FlowExecutionContext context, FlowModel model, FlowNode node, List<Object> inputs) {
    terminal(context, node, "skipped", null);
  }
  @Override public void onNodeBlocked(FlowExecutionContext context, FlowModel model, FlowNode node, Throwable failure) {
    terminal(context, node, "blocked", failure);
  }
  private void terminal(FlowExecutionContext context, FlowNode node, String result, Throwable failure) {
    synchronized (lock) {
      var invocation = active.get(context.invocationId());
      if (invocation == null || !invocation.detailed) { return; }
      Long start = invocation.startedNodes.remove(context.scope() + ":" + node.getName());
      if (invocation.nodes.size() >= MAX_SPANS || start == null && activeSpans >= MAX_ACTIVE_SPANS) {
        if (start != null) { activeSpans--; }
        invocation.truncated = true; return;
      }
      if (start == null) { activeSpans++; }
      invocation.nodes.add(new Span(clip(node.getName()), clip(node.getType()), clip(context.scope()),
          invocation.startedAt + (start == null ? 0 : TimeUnit.NANOSECONDS.toMillis(start - invocation.nanoStart)),
          start == null ? 0 : elapsed(start), result, exceptionType(failure)));
    }
  }
  @Override public void onFlowCompleted(FlowExecutionContext context, FlowModel model, List<Object> inputs,
      List<Either<Throwable, Object>> results, Object result) { complete(context.invocationId(), "completed", null); }
  @Override public void onFlowFailed(FlowExecutionContext context, FlowModel model, List<Object> inputs,
      List<Either<Throwable, Object>> results, Throwable failure) { complete(context.invocationId(), "failed", failure); }

  private void complete(String id, String result, Throwable failure) {
    synchronized (lock) {
      var invocation = active.remove(id);
      if (invocation == null) { return; }
      if (expired(invocation, clock.millis())) { result = "incomplete"; failure = null; }
      activeSpans -= invocation.nodes.size() + invocation.startedNodes.size();
      double duration = elapsed(invocation.nanoStart);
      var window = windows.get(invocation.windowId);
      window.updatedAt = clock.millis();
      if (result.equals("incomplete")) { window.incomplete++; }
      else {
        window.completed++;
        if (result.equals("failed")) { window.failed++; }
        int bucket = duration <= 1 ? 0 : Math.min(30, (int) Math.ceil(Math.log(duration) / Math.log(2)));
        window.histogram[bucket]++;
      }
      if (!invocation.detailed) { return; }
      if (pending.size() >= MAX_PENDING_RUNS) { dropped++; return; }
      // A parent captured only by a different observer does not become a fabricated local trace.
      var parentInvocation = active.get(invocation.context.parentInvocationId());
      String parent = parentInvocation != null && parentInvocation.detailed ? invocation.context.parentInvocationId() : null;
      pending.addLast(new Run(id, invocation.name, bootId, parent, parent == null ? null : invocation.context.callerNodeName(),
          invocation.runtimeRevision, invocation.policyRevision, invocation.startedAt, duration, result,
          exceptionType(failure), invocation.truncated || !invocation.startedNodes.isEmpty(), invocation.nodes));
    }
  }

  public void expire() {
    synchronized (lock) {
      long now = clock.millis();
      var expired = active.entrySet().stream().filter(entry -> expired(entry.getValue(), now)).map(Map.Entry::getKey).toList();
      expired.forEach(id -> complete(id, "incomplete", null));
    }
  }
  public List<Window> windows() { synchronized (lock) { return windows.values().stream().map(MutableWindow::snapshot).toList(); } }
  public List<Run> pending() { synchronized (lock) { return List.copyOf(pending); } }
  public void acknowledgeRun(String id) { synchronized (lock) { pending.removeIf(run -> run.id().equals(id)); } }
  public void acknowledgeWindow(Window saved) {
    synchronized (lock) {
      var current = windows.get(saved.id());
      if (current != null && current.snapshot().equals(saved) && saved.startedAt() < clock.millis() - 120_000
          && active.values().stream().noneMatch(invocation -> invocation.windowId.equals(saved.id()))) { windows.remove(saved.id()); }
    }
  }
  public Status status(String error) {
    synchronized (lock) {
      var diagnostics = new LinkedHashMap<String, String>();
      policies.flows().forEach((name, policy) -> diagnostics.put(name, !policy.monitorEnabled() ? "off"
          : !policy.diagnosticEnabled() ? "monitoring" : !policy.diagnosticAt(clock.millis()) ? "expired"
          : captureCounts.getOrDefault(policy.captureId(), 0) >= 100 ? "capacity" : "recording"));
      return new Status(bootId, revision, clock.millis(), error, rejected, dropped, active.size(), diagnostics);
    }
  }
  private static boolean expired(Invocation invocation, long now) {
    return invocation.drainUntil <= now || invocation.startedAt + 3_600_000 <= now
        || invocation.detailed && invocation.captureExpiresAt + 30_000 <= now;
  }
  private double elapsed(long start) { return Math.max(0, (nanoTime.getAsLong() - start) / 1_000_000d); }
  private static String key(FlowModel model) { return FlowModelDefinitions.nameKey(model.getName()); }
  private static String clip(String value) { return value == null ? null : value.substring(0, Math.min(200, value.length())); }
  private static String exceptionType(Throwable failure) {
    var cause = failure;
    for (int depth = 0; depth < 16 && cause != null && cause.getCause() != null && cause.getCause() != cause; depth++) { cause = cause.getCause(); }
    return cause == null ? null : clip(cause.getClass().getSimpleName());
  }

  private static final class Invocation {
    final FlowExecutionContext context;
    final String name;
    final String runtimeRevision;
    final String policyRevision;
    final String windowId;
    final long startedAt;
    final long nanoStart;
    final boolean detailed;
    final String captureId;
    final long captureExpiresAt;
    final List<Span> nodes = new ArrayList<>();
    final Map<String, Long> startedNodes = new HashMap<>();
    long drainUntil = Long.MAX_VALUE;
    boolean truncated;
    Invocation(FlowExecutionContext context, String name, String runtimeRevision, String policyRevision,
        String windowId, long startedAt, long nanoStart, boolean detailed, String captureId, long captureExpiresAt) {
      this.context = context; this.name = name; this.runtimeRevision = runtimeRevision; this.policyRevision = policyRevision;
      this.windowId = windowId; this.startedAt = startedAt; this.nanoStart = nanoStart; this.detailed = detailed; this.captureId = captureId; this.captureExpiresAt = captureExpiresAt;
    }
  }
  private final class MutableWindow {
    final String id;
    final String name;
    final String runtimeRevision;
    final String policyRevision;
    final long startedAt;
    long updatedAt;
    long completed;
    long failed;
    long incomplete;
    final long[] histogram = new long[31];
    MutableWindow(String id, String name, String runtimeRevision, String policyRevision, long startedAt) {
      this.id = id; this.name = name; this.runtimeRevision = runtimeRevision; this.policyRevision = policyRevision;
      this.startedAt = startedAt; this.updatedAt = clock.millis();
    }
    Window snapshot() {
      return new Window(id, name, bootId, runtimeRevision, policyRevision, startedAt, updatedAt,
          completed, failed, incomplete, java.util.Arrays.stream(histogram).boxed().toList());
    }
  }
}
