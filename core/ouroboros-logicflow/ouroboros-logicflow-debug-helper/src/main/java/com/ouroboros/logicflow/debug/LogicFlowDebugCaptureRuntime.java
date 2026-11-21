package com.ouroboros.logicflow.debug;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus;
import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus.Loss;
import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;
import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.util.Json;

/** Process-local, lease-gated capture. No network operation runs on business threads. */
public final class LogicFlowDebugCaptureRuntime {
  public record Limits(int maxActiveRuns, int maxRuns, int maxInvocations, int maxRunEvents,
                       long maxRunBytes, int maxWindowEvents, long maxWindowBytes, int maxEventBytes) {
    public Limits {
      if (maxActiveRuns < 1 || maxRuns < 1 || maxInvocations < 1 || maxRunEvents < 1 || maxRunBytes < 1
          || maxWindowEvents < 1 || maxWindowBytes < 1 || maxEventBytes < 1) {
        throw new IllegalArgumentException("Debug capture limits must be positive");
      }
    }
    public static Limits defaults() {
      return new Limits(32, 100, 256, 2000, 8L * 1024 * 1024, 4096, 16L * 1024 * 1024, 65536);
    }
  }
  public record Packet(long captureSeq, byte[] payload) {
    public Packet { payload = payload.clone(); }
    @Override public byte[] payload() { return payload.clone(); }
  }
  private static final long DRAIN_NANOS = Duration.ofSeconds(30).toNanos();
  private final LongSupplier clock;
  private final Limits limits;
  private final RuntimeGraphSnapshotter graphs = new RuntimeGraphSnapshotter();
  private Capture capture;

  public LogicFlowDebugCaptureRuntime() {
    this(System::nanoTime, Limits.defaults());
  }

  public LogicFlowDebugCaptureRuntime(LongSupplier clock, Limits limits) {
    this.clock = Objects.requireNonNull(clock);
    this.limits = Objects.requireNonNull(limits);
  }

  public synchronized boolean knowsCapture(String captureId) {
    return capture != null && capture.id.equals(captureId);
  }

  public synchronized boolean isRecording() {
    return live() && (capture.accepting || !capture.invocations.isEmpty());
  }

  public synchronized FlowDebugCaptureStatus start(String id, FlowDebugTarget target, String flowName,
                                                   long version, long leaseMillis) {
    validateLease(leaseMillis);
    Objects.requireNonNull(id);
    Objects.requireNonNull(target);
    Objects.requireNonNull(flowName);
    expire();
    if (capture != null && capture.id.equals(id)) {
      if (!capture.target.equals(target) || !capture.flowName.equals(flowName)) {
        throw new IllegalArgumentException("Capture identity was reused for a different target or flow");
      }
      return status(id);
    }
    if (capture != null && (!capture.window.isEmpty() || !capture.invocations.isEmpty() || (capture.accepting && live()))) {
      throw new IllegalStateException("This process already has an active or draining capture");
    }
    capture = new Capture(id, target, flowName, version, clock.getAsLong() + Duration.ofMillis(leaseMillis).toNanos());
    return status(id);
  }

  public synchronized FlowDebugCaptureStatus renew(String id, FlowDebugTarget target, long version,
                                                   long leaseMillis, long acknowledgedThrough) {
    validateLease(leaseMillis);
    requireCapture(id, target);
    expire();
    if (!live()) {
      throw new IllegalStateException("Expired capture cannot be renewed");
    }
    acknowledge(id, target, acknowledgedThrough);
    if (version > capture.version) {
      capture.version = version;
      capture.deadline = clock.getAsLong() + Duration.ofMillis(leaseMillis).toNanos();
    }
    return status(id);
  }

  public synchronized FlowDebugCaptureStatus stop(String id, FlowDebugTarget target, long version, long acknowledgedThrough) {
    requireCapture(id, target);
    acknowledge(id, target, acknowledgedThrough);
    if (version > capture.version) {
      capture.version = version;
      capture.accepting = false;
    }
    return status(id);
  }

  public synchronized void acknowledge(String id, FlowDebugTarget target, long through) {
    requireCapture(id, target);
    if (through < 0 || through > capture.highest) {
      throw new IllegalArgumentException("Invalid capture acknowledgement");
    }
    if (through <= capture.acknowledged) {
      return;
    }
    capture.acknowledged = through;
    var iterator = capture.window.headMap(through, true).values().iterator();
    while (iterator.hasNext()) {
      capture.bytes -= iterator.next().payload.length;
      iterator.remove();
    }
    capture.losses.removeIf(loss -> loss.lastCaptureSeq() <= through);
  }

  public synchronized FlowDebugCaptureStatus status(String id) {
    if (capture == null || !capture.id.equals(id)) {
      throw new IllegalStateException("Capture does not exist in this process");
    }
    expire();
    return new FlowDebugCaptureStatus(capture.id, capture.target, capture.accepting && live(),
        Math.max(0, Duration.ofNanos(capture.deadline - clock.getAsLong()).toMillis()), capture.highest,
        capture.window.isEmpty() ? capture.highest + 1 : capture.window.firstKey(), capture.acknowledged,
        capture.bytes, capture.completed, capture.rejected, capture.invocations.size(), capture.losses, capture.transportError);
  }

  public synchronized List<FlowDebugEvent> read(String id, FlowDebugTarget target, long afterSeq, int limit) {
    requireCapture(id, target);
    if (afterSeq < 0 || limit < 1 || limit > 200) {
      throw new IllegalArgumentException("Invalid capture event cursor");
    }
    expire();
    return capture.window.tailMap(afterSeq, false).values().stream().limit(limit)
        .map(entry -> Json.tryToBean(FlowDebugEvent.class, new String(entry.payload, StandardCharsets.UTF_8)).get()).toList();
  }

  synchronized void record(FlowExecutionContext context, FlowModel model, FlowNode node, String kind,
                           Supplier<Map<String, Object>> payload) {
    expire();
    if (!live()) {
      return;
    }
    Invocation invocation = capture.invocations.get(context.invocationId());
    if ("flow.started".equals(kind)) {
      if (invocation != null) {
        return;
      }
      invocation = begin(context, model);
    }
    if (invocation == null || invocation.run.incomplete) {
      return;
    }
    var run = invocation.run;
    if (run.sequence >= limits.maxRunEvents()) {
      lose(run, "run-event-capacity");
      return;
    }
    try {
      var event = new FlowDebugEvent();
      event.setSchemaVersion("2.0");
      event.setAppName(capture.target.appName());
      event.setTarget(capture.target);
      event.setCaptureId(capture.id);
      event.setCaptureSeq(capture.highest + 1);
      event.setRunId(run.id);
      event.setSessionId(run.id);
      event.setSeq(run.sequence + 1);
      event.setFlowInvocationId(context.invocationId());
      event.setParentInvocationId(context.parentInvocationId());
      event.setCallerNodeName(context.callerNodeName());
      event.setScope(context.scope());
      event.setFlowName(model.getName());
      event.setModelId(model.getName());
      event.setKind(kind);
      event.setTimestamp(System.currentTimeMillis());
      event.setGraphId(invocation.graph.id());
      if (node != null) {
        event.setNodeName(node.getName());
        event.setNodeType(node.getType());
      }
      var data = new LinkedHashMap<>(payload.get());
      if ("flow.started".equals(kind)) {
        data.put("graph", invocation.graph);
      }
      event.setPayload(data);
      byte[] bytes = encode(event);
      if (bytes.length > limits.maxEventBytes()) {
        data.keySet().removeIf(key -> key.equals("inputSnapshots") || key.equals("outputSnapshots") || key.equals("exceptionSnapshot"));
        data.put("dataTruncated", true);
        data.put("truncationReason", "event-byte-capacity");
        bytes = encode(event);
      }
      if (bytes.length > limits.maxEventBytes() || run.bytes + bytes.length > limits.maxRunBytes()
          || capture.window.size() >= limits.maxWindowEvents() || capture.bytes + bytes.length > limits.maxWindowBytes()) {
        lose(run, "recording-capacity");
        return;
      }
      capture.highest++;
      run.sequence++;
      run.bytes += bytes.length;
      capture.bytes += bytes.length;
      capture.window.put(capture.highest, new Pending(bytes));
      if ("flow.completed".equals(kind) || "flow.failed".equals(kind)) {
        capture.invocations.remove(context.invocationId());
        if (context.invocationId().equals(run.id)) {
          capture.invocations.values().removeIf(value -> value.run == run);
          capture.completed++;
        }
      }
    } catch (RuntimeException failure) {
      lose(run, "snapshot-failed:" + failure.getClass().getSimpleName());
    }
  }

  private Invocation begin(FlowExecutionContext context, FlowModel model) {
    Run run;
    if (context.parentInvocationId() == null) {
      if (!capture.accepting || !capture.flowName.equals(model.getName())) {
        return null;
      }
      long active = capture.invocations.values().stream().map(value -> value.run).distinct().count();
      if (active >= limits.maxActiveRuns() || capture.started >= limits.maxRuns()) {
        capture.rejected++;
        capture.accepting = false;
        return null;
      }
      run = new Run(context.invocationId());
      capture.started++;
      if (capture.started == limits.maxRuns()) {
        capture.accepting = false;
      }
    } else {
      var parent = capture.invocations.get(context.parentInvocationId());
      if (parent == null || parent.run.incomplete) {
        return null;
      }
      run = parent.run;
    }
    if (capture.invocations.size() >= limits.maxInvocations()) {
      lose(run, "invocation-capacity");
      return null;
    }
    try {
      var invocation = new Invocation(run, graphs.capture(model));
      capture.invocations.put(context.invocationId(), invocation);
      return invocation;
    } catch (RuntimeException failure) {
      lose(run, "graph-unavailable:" + failure.getClass().getSimpleName());
      return null;
    }
  }

  private void lose(Run run, String reason) {
    if (!run.incomplete) {
      run.incomplete = true;
      capture.accepting = false;
      capture.losses.add(new Loss(run.id, ++capture.highest, ++run.sequence, reason));
    }
    capture.invocations.values().removeIf(value -> value.run == run);
  }

  /** At most fifty bounded packets; transport calls occur after this synchronized method returns. */
  synchronized List<Packet> duePackets() {
    expire();
    if (capture == null) {
      return List.of();
    }
    long now = clock.getAsLong();
    var packets = new ArrayList<Packet>();
    for (var entry : capture.window.entrySet()) {
      var pending = entry.getValue();
      if (pending.attempts == 0 || now - pending.nextSend >= 0) {
        packets.add(new Packet(entry.getKey(), pending.payload));
        pending.attempts++;
        long delayMillis = Math.min(5000, 100L << Math.min(pending.attempts, 5));
        pending.nextSend = now + Duration.ofMillis(delayMillis).toNanos();
        if (packets.size() == 50) {
          break;
        }
      }
    }
    return packets;
  }

  synchronized void transportFailed(RuntimeException failure) {
    if (capture != null) {
      capture.transportError = failure.getClass().getSimpleName();
    }
  }

  synchronized void transportSucceeded() {
    if (capture != null) {
      capture.transportError = null;
    }
  }

  private boolean live() {
    return capture != null && clock.getAsLong() - capture.deadline < 0;
  }

  private void expire() {
    if (capture == null || live()) {
      return;
    }
    capture.accepting = false;
    var runs = capture.invocations.values().stream().map(value -> value.run).distinct().toList();
    runs.forEach(run -> lose(run, "capture-lease-expired"));
    if (clock.getAsLong() - capture.deadline >= DRAIN_NANOS && !capture.window.isEmpty()) {
      capture.losses.add(new Loss(null, capture.acknowledged + 1, 0, capture.highest, 0, "retry-window-expired"));
      // Loss declarations remain in the bounded status lane after the retry window expires.
      capture.window.clear();
      capture.bytes = 0;
    }
  }

  private void requireCapture(String id, FlowDebugTarget target) {
    if (capture == null || !capture.id.equals(id) || !capture.target.equals(target)) {
      throw new IllegalStateException("Capture is not bound to this process");
    }
  }

  private static void validateLease(long millis) {
    if (millis < 1000 || millis > 60000) {
      throw new IllegalArgumentException("Capture lease must be between one and sixty seconds");
    }
  }

  private static byte[] encode(FlowDebugEvent event) {
    return Json.tryToJsonString(event).get().getBytes(StandardCharsets.UTF_8);
  }

  private static final class Capture {
    final String id;
    final FlowDebugTarget target;
    final String flowName;
    final Map<String, Invocation> invocations = new HashMap<>();
    final TreeMap<Long, Pending> window = new TreeMap<>();
    final List<Loss> losses = new ArrayList<>();
    long deadline;
    long version;
    long highest;
    long acknowledged;
    long bytes;
    long started;
    long completed;
    long rejected;
    boolean accepting = true;
    String transportError;
    Capture(String id, FlowDebugTarget target, String flowName, long version, long deadline) {
      this.id = id;
      this.target = target;
      this.flowName = flowName;
      this.version = version;
      this.deadline = deadline;
    }
  }
  private record Invocation(Run run, FlowDebugGraph graph) {}
  private static final class Run {
    final String id;
    long sequence;
    long bytes;
    boolean incomplete;
    Run(String id) { this.id = id; }
  }
  private static final class Pending {
    final byte[] payload;
    long nextSend;
    int attempts;
    Pending(byte[] payload) { this.payload = payload; }
  }
}
