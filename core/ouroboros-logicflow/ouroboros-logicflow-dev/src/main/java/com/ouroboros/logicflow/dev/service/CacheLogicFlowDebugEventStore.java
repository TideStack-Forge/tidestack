package com.ouroboros.logicflow.dev.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;
import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus.Loss;
import com.ouroboros.util.Json;

/** CAS publishes bounded indexes only after immutable records are queryable. */
public final class CacheLogicFlowDebugEventStore implements LogicFlowDebugEventStore {
  public record Limits(int maxCaptures, int maxRuns, int maxRunEvents, int maxGap,
                       long maxCaptureBytes, int maxEventBytes, int maxGraphBytes,
                       long maxCaptureMillis, long retentionMillis) {
    public Limits {
      if (maxCaptures < 1 || maxRuns < 1 || maxRunEvents < 1 || maxGap < 1 || maxCaptureBytes < 1
          || maxEventBytes < 1 || maxGraphBytes < 1 || maxCaptureMillis < 1 || retentionMillis < 1) {
        throw new IllegalArgumentException("Debug recording limits must be positive");
      }
    }
    public static Limits defaults() {
      return new Limits(32, 100, 2000, 2000, 64L * 1024 * 1024, 65536, 262144, 600000, 1800000);
    }
  }

  private static final com.fasterxml.jackson.databind.ObjectMapper RECORDS = Json.mapperCopy()
      .configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  private static final long APPLICATION_BYTES = 256L * 1024 * 1024;
  private static final long PLATFORM_BYTES = 1024L * 1024 * 1024;
  private static final int MAX_GRAPHS = 256;

  private record IndexEntry(String appName, String captureId, long expiresAt) {}
  private record CaptureIndex(List<IndexEntry> entries) {
    private CaptureIndex { entries = List.copyOf(entries); }
  }
  private record StoredCapture(String json, Capture value) {}
  private final Supplier<CacheManager> caches;
  private final String region;
  private final LongSupplier clock;
  private final Limits limits;
  private final boolean requireShared;

  public CacheLogicFlowDebugEventStore(Supplier<CacheManager> caches, String region, LongSupplier clock,
                                      Limits limits, boolean requireShared) {
    this.caches = Objects.requireNonNull(caches);
    this.region = requireText(region);
    this.clock = Objects.requireNonNull(clock);
    this.limits = Objects.requireNonNull(limits);
    this.requireShared = requireShared;
  }

  @Override
  public Capabilities capabilities() {
    var capabilities = cache().capabilities();
    return new Capabilities(capabilities.shared(), capabilities.atomicUpdates(), false);
  }

  private CacheManager cache() {
    var cache = caches.get();
    var caps = cache.capabilities();
    if (!caps.atomicUpdates() || (requireShared && !caps.shared())) {
      throw new DebugStoreException("Configured cache cannot provide atomic shared debug control");
    }
    return cache;
  }

  @Override
  public Capture tryCreateCapture(CreateCapture command) {
    requireText(command.captureId());
    requireText(command.operationId());
    requireText(command.owner());
    requireText(command.flowName());
    validateLease(command.leaseMillis());
    long now = clock.getAsLong();
    long expires = now + limits.maxCaptureMillis() + limits.retentionMillis();
    var fresh = new Capture(command.captureId(), command.operationId(), command.owner(), command.target(),
        command.flowName(), State.STARTING, 1, now, now + command.leaseMillis(), expires, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
    String key = captureKey(command.target().appName(), command.captureId());
    var original = stored(command.target().appName(), command.captureId());
    if (original.isPresent()) {
      var capture = original.get().value();
      if (!capture.operationId().equals(command.operationId()) || !capture.owner().equals(command.owner())
          || !capture.target().equals(command.target()) || !capture.flowName().equals(command.flowName())) {
        throw new DebugStoreException("Capture operation identity was reused with different parameters");
      }
      requireOwnedSlot(capture);
      if (capture.state() == State.DRAINING) {
        throw new DebugStoreException("Capture operation has already stopped");
      }
      return capture;
    }
    String slot = slotKey(fresh);
    if (!cache().putIfAbsent(region, slot, fresh.captureId(), leaseTtl(fresh))) {
      throw new DebugStoreException("The selected target already has an active capture; retry the same operation");
    }
    boolean created = false;
    try {
      // Index admission precedes any recording. A rejected slot or app limit creates no history.
      reservePlatformCapacity(fresh);
      if (!cache().putIfAbsent(region, key, encode(fresh), ttl(expires))) {
        throw new DebugStoreException("Capture operation identity is already in use");
      }
      created = true;
      requireOwnedSlot(fresh);
      return fresh;
    } finally {
      if (!created) {
        cache().evictIfEquals(region, slot, fresh.captureId());
      }
    }
  }

  @Override
  public Optional<Capture> getCapture(String appName, String captureId) {
    return stored(appName, captureId).map(StoredCapture::value);
  }

  private Optional<StoredCapture> stored(String appName, String captureId) {
    return text(captureKey(appName, captureId)).map(json -> new StoredCapture(json, decode(Capture.class, json)))
        .filter(record -> record.value().expiresAt() > clock.getAsLong());
  }

  @Override
  public Capture updateCapture(String appName, String captureId, long expectedVersion, State state, long leaseMillis) {
    validateLease(leaseMillis);
    for (int attempt = 0; attempt < 64; attempt++) {
      var stored = stored(appName, captureId).orElseThrow(() -> unavailable());
      var current = stored.value();
      if (current.version() != expectedVersion) {
        throw new DebugStoreException("Capture version conflict");
      }
      requireOwnedSlot(current);
      boolean legal = switch (current.state()) {
        case STARTING -> state == State.ACTIVE || state == State.SATURATED || state == State.DRAINING || state == State.CLOSED;
        case ACTIVE -> state == State.ACTIVE || state == State.SATURATED || state == State.DRAINING || state == State.CLOSED;
        case SATURATED -> state == State.SATURATED || state == State.DRAINING || state == State.CLOSED;
        case DRAINING -> state == State.DRAINING || state == State.CLOSED;
        case CLOSED -> false;
      };
      if (!legal) {
        throw new DebugStoreException("Invalid capture state transition");
      }
      long until = state != State.ACTIVE || current.state() == State.STARTING ? current.leaseUntil()
          : Math.min(clock.getAsLong() + leaseMillis, current.createdAt() + limits.maxCaptureMillis());
      var next = copy(current, state, current.version() + 1, until, current.receivedThrough(), current.bytes(),
          current.runs(), current.pending());
      if (!cache().compareAndSet(region, captureKey(appName, captureId), stored.json(), encode(next), ttl(next.expiresAt()))) {
        continue;
      }
      if (state == State.CLOSED) {
        cache().evictIfEquals(region, slotKey(current), captureId);
      } else if (!cache().compareAndSet(region, slotKey(current), captureId, captureId, leaseTtl(next))) {
        throw new DebugStoreException("Capture target claim has expired or disappeared");
      }
      return next;
    }
    throw new DebugStoreException("Capture recording is busy; retry the same operation");
  }

  @Override
  public List<Capture> listCaptures(String appName) {
    var index = text(indexKey()).map(json -> decode(CaptureIndex.class, json));
    return index.stream().flatMap(value -> value.entries().stream()).filter(entry -> appName.equals(entry.appName()))
        .map(entry -> getCapture(appName, entry.captureId())).flatMap(Optional::stream)
        .sorted(Comparator.comparingLong(Capture::createdAt).reversed()).toList();
  }

  // Reserve the full budget before creating history. One CAS enforces both capacity limits.
  // Partial writes retain their reservation until expiry, so they cannot overcommit storage.
  private void reservePlatformCapacity(Capture capture) {
    String key = indexKey();
    String app = capture.target().appName();
    long appCapacity = Math.min(limits.maxCaptures(), APPLICATION_BYTES / limits.maxCaptureBytes());
    long platformCapacity = PLATFORM_BYTES / limits.maxCaptureBytes();
    for (int attempt = 0; attempt < 64; attempt++) {
      var original = text(key);
      var ids = new ArrayList<>(original.map(json -> decode(CaptureIndex.class, json).entries()).orElse(List.of()));
      long now = clock.getAsLong();
      ids.removeIf(entry -> entry.expiresAt() <= now);
      if (ids.stream().anyMatch(entry -> app.equals(entry.appName()) && capture.captureId().equals(entry.captureId()))) {
        throw new DebugStoreException("Capture operation identity is already in use or has expired");
      }
      if (ids.size() >= platformCapacity || ids.stream().filter(entry -> app.equals(entry.appName())).count() >= appCapacity) {
        throw new DebugStoreException("Debug recording capacity reached");
      }
      ids.add(new IndexEntry(app, capture.captureId(), capture.expiresAt()));
      String next = encode(new CaptureIndex(ids));
      var timeout = Duration.ofMillis(limits.maxCaptureMillis() + limits.retentionMillis());
      boolean written = original.isEmpty() ? cache().putIfAbsent(region, key, next, timeout)
          : cache().compareAndSet(region, key, original.get(), next, timeout);
      if (written) {
        return;
      }
    }
    throw new DebugStoreException("Debug recording index is busy; retry the same operation");
  }

  @Override
  public void putGraph(String appName, String captureId, FlowDebugGraph graph) {
    requireText(graph.id());
    if (graph.nodes().size() > 2000 || graph.connections().size() > 10000) {
      throw DebugStoreException.capacity("Runtime graph exceeds recording budget");
    }
    String json = encode(graph);
    int bytes = size(json);
    if (bytes > limits.maxGraphBytes()) {
      throw DebugStoreException.capacity("Runtime graph exceeds recording budget");
    }
    var record = new GraphRecord(graph.id(), key("graph-digest", json), bytes);
    for (int attempt = 0; attempt < 64; attempt++) {
      var stored = stored(appName, captureId).orElseThrow(() -> unavailable());
      var capture = stored.value();
      requireOwnedSlot(capture);
      var previous = capture.graphs().stream().filter(item -> item.graphId().equals(graph.id())).findFirst();
      if (previous.isPresent()) {
        if (!previous.get().equals(record)) {
          throw new DebugStoreException("Conflicting immutable debug graph");
        }
        putImmutable(graphKey(appName, captureId, graph.id()), json, capture.expiresAt());
        return;
      }
      if (capture.graphs().size() >= MAX_GRAPHS || capture.bytes() + bytes > limits.maxCaptureBytes()) {
        throw DebugStoreException.capacity("Capture recording graph capacity reached");
      }
      var graphs = new ArrayList<>(capture.graphs());
      graphs.add(record);
      var next = new Capture(capture.captureId(), capture.operationId(), capture.owner(), capture.target(), capture.flowName(),
          capture.state(), capture.version(), capture.createdAt(), capture.leaseUntil(), capture.expiresAt(), capture.receivedThrough(),
          capture.bytes() + bytes, capture.runs(), capture.pending(), capture.losses(), graphs, capture.staged());
      if (cache().compareAndSet(region, captureKey(appName, captureId), stored.json(), encode(next), ttl(next.expiresAt()))) {
        putImmutable(graphKey(appName, captureId, graph.id()), json, capture.expiresAt());
        return;
      }
    }
    throw new DebugStoreException("Capture graph index is busy; retry the same graph");
  }

  @Override
  public Optional<FlowDebugGraph> getGraph(String appName, String captureId, String graphId) {
    if (getCapture(appName, captureId).isEmpty()) {
      return Optional.empty();
    }
    return text(graphKey(appName, captureId, graphId)).map(json -> decode(FlowDebugGraph.class, json));
  }

  @Override
  public Receipt append(FlowDebugEvent event) {
    validateEvent(event);
    String eventJson = encode(event);
    int bytes = size(eventJson);
    if (bytes > limits.maxEventBytes()) {
      throw DebugStoreException.capacity("Debug event exceeds recording budget");
    }
    reserveEvent(event, eventJson, bytes);
    for (int attempt = 0; attempt < 64; attempt++) {
      var stored = stored(event.getAppName(), event.getCaptureId()).orElseThrow(() -> unavailable());
      var capture = stored.value();
      requireOwnedSlot(capture);
      if (!capture.target().equals(event.getTarget())) {
        throw new DebugStoreException("Debug event source does not match the bound process");
      }
      if (event.getCaptureSeq() > capture.receivedThrough() + limits.maxGap()) {
        throw new DebugStoreException("Event is beyond the receive window; retransmit earlier events first");
      }
      boolean duplicate = event.getCaptureSeq() <= capture.receivedThrough() || capture.pending().contains(event.getCaptureSeq());
      var runs = new ArrayList<>(capture.runs());
      var previous = runs.stream().filter(run -> run.runId().equals(event.getRunId())).findFirst();
      if (previous.isEmpty() && runs.size() >= limits.maxRuns()) {
        throw DebugStoreException.capacity("Capture no longer accepts new executions");
      }
      var run = updateRun(previous.orElse(null), event, capture.losses());
      putImmutable(eventKey(event.getAppName(), capture.captureId(), event.getCaptureSeq()), eventJson, capture.expiresAt());
      putImmutable(runEventKey(event.getAppName(), capture.captureId(), event.getRunId(), event.getSeq()), eventJson, capture.expiresAt());
      if (duplicate) {
        return new Receipt(capture.receivedThrough(), true);
      }
      var pending = new TreeSet<>(capture.pending());
      pending.add(event.getCaptureSeq());
      long through = advance(capture, pending, capture.losses());
      previous.ifPresent(runs::remove);
      runs.add(run);
      runs.sort(Comparator.comparingLong(Run::firstCaptureSeq));
      var staged = capture.staged().stream().filter(item -> item.captureSeq() != event.getCaptureSeq()).toList();
      var next = new Capture(capture.captureId(), capture.operationId(), capture.owner(), capture.target(), capture.flowName(),
          capture.state(), capture.version(), capture.createdAt(), capture.leaseUntil(), capture.expiresAt(), through,
          capture.bytes(), runs, new ArrayList<>(pending), capture.losses(), capture.graphs(), staged);
      if (cache().compareAndSet(region, captureKey(event.getAppName(), capture.captureId()), stored.json(), encode(next), ttl(next.expiresAt()))) {
        return new Receipt(through, false);
      }
    }
    throw new DebugStoreException("Capture recording is busy; retry the same event");
  }

  private void reserveEvent(FlowDebugEvent event, String json, int bytes) {
    var reservation = new EventReservation(event.getCaptureSeq(), event.getRunId(), event.getSeq(), key("event-digest", json));
    for (int attempt = 0; attempt < 64; attempt++) {
      var stored = stored(event.getAppName(), event.getCaptureId()).orElseThrow(() -> unavailable());
      var capture = stored.value();
      requireOwnedSlot(capture);
      if (!capture.target().equals(event.getTarget()) || event.getCaptureSeq() > capture.receivedThrough() + limits.maxGap()) {
        throw new DebugStoreException("Event source or receive window does not match capture");
      }
      if (event.getCaptureSeq() <= capture.receivedThrough() || capture.pending().contains(event.getCaptureSeq())) {
        return;
      }
      var previous = capture.staged().stream().filter(item -> item.captureSeq() == event.getCaptureSeq()
          || (item.runId().equals(event.getRunId()) && item.runSeq() == event.getSeq())).findFirst();
      if (previous.isPresent()) {
        if (!previous.get().equals(reservation)) {
          throw new DebugStoreException("Conflicting reserved debug event");
        }
        return;
      }
      long runs = java.util.stream.Stream.concat(capture.runs().stream().map(Run::runId),
          capture.staged().stream().map(EventReservation::runId)).distinct().count();
      boolean knownRun = capture.runs().stream().anyMatch(run -> run.runId().equals(event.getRunId()))
          || capture.staged().stream().anyMatch(item -> item.runId().equals(event.getRunId()));
      if ((!knownRun && runs >= limits.maxRuns()) || capture.staged().size() >= limits.maxGap()
          || capture.bytes() + 2L * bytes > limits.maxCaptureBytes()) {
        throw DebugStoreException.capacity("Capture recording byte or execution capacity reached");
      }
      var staged = new ArrayList<>(capture.staged());
      staged.add(reservation);
      var next = new Capture(capture.captureId(), capture.operationId(), capture.owner(), capture.target(), capture.flowName(),
          capture.state(), capture.version(), capture.createdAt(), capture.leaseUntil(), capture.expiresAt(), capture.receivedThrough(),
          capture.bytes() + 2L * bytes, capture.runs(), capture.pending(), capture.losses(), capture.graphs(), staged);
      if (cache().compareAndSet(region, captureKey(event.getAppName(), capture.captureId()), stored.json(), encode(next), ttl(next.expiresAt()))) {
        return;
      }
    }
    throw new DebugStoreException("Capture admission is busy; retry the same event");
  }

  @Override
  public Receipt declareLoss(String appName, String captureId, FlowDebugTarget target, Loss loss) {
    if (loss.captureSeq() < 1 || loss.lastCaptureSeq() < loss.captureSeq()
        || loss.lastCaptureSeq() > (long) limits.maxRuns() * (limits.maxRunEvents() + 1)
        || loss.runSeq() < 0 || loss.lastRunSeq() < loss.runSeq() || loss.lastRunSeq() > limits.maxRunEvents() + 1L) {
      throw new DebugStoreException("Invalid missing event range");
    }
    requireText(loss.reason());
    for (int attempt = 0; attempt < 64; attempt++) {
      var stored = stored(appName, captureId).orElseThrow(() -> unavailable());
      var capture = stored.value();
      if (!capture.target().equals(target)) {
        throw new DebugStoreException("Loss report does not match the bound process");
      }
      if (capture.losses().contains(loss)) {
        return new Receipt(capture.receivedThrough(), true);
      }
      if (capture.losses().size() >= limits.maxRuns() + 1) {
        throw new DebugStoreException("Loss declaration capacity reached");
      }
      var losses = new ArrayList<>(capture.losses());
      losses.add(loss);
      var pending = new TreeSet<>(capture.pending());
      long through = advance(capture, pending, losses);
      var runs = new ArrayList<>(capture.runs().stream().map(run -> loss.runId() == null || run.runId().equals(loss.runId())
          ? new Run(run.runId(), run.flowName(), run.graphId(), run.firstCaptureSeq(), run.startedAt(), run.lastEventAt(),
              Math.max(run.highestSeq(), loss.runId() == null ? 0 : loss.lastRunSeq()), run.terminalSeq(), "INCOMPLETE", true)
          : run).toList());
      if (loss.runId() != null && runs.size() < limits.maxRuns()
          && runs.stream().noneMatch(run -> run.runId().equals(loss.runId()))) {
        long now = clock.getAsLong();
        runs.add(new Run(loss.runId(), capture.flowName(), null, loss.captureSeq(), now, now,
            loss.lastRunSeq(), null, "INCOMPLETE", true));
      }
      var next = new Capture(capture.captureId(), capture.operationId(), capture.owner(), capture.target(), capture.flowName(),
          capture.state(), capture.version(), capture.createdAt(), capture.leaseUntil(), capture.expiresAt(), through,
          capture.bytes(), runs, new ArrayList<>(pending), losses, capture.graphs(), capture.staged());
      if (cache().compareAndSet(region, captureKey(appName, captureId), stored.json(), encode(next), ttl(next.expiresAt()))) {
        return new Receipt(through, false);
      }
    }
    throw new DebugStoreException("Capture recording is busy; retry the same loss report");
  }

  private long advance(Capture capture, TreeSet<Long> pending, List<Loss> losses) {
    long through = capture.receivedThrough();
    while (true) {
      long next = through + 1;
      var loss = losses.stream().filter(range -> range.captureSeq() <= next && range.lastCaptureSeq() >= next)
          .max(Comparator.comparingLong(Loss::lastCaptureSeq));
      if (loss.isPresent()) {
        through = loss.get().lastCaptureSeq();
        pending.headSet(through, true).clear();
      } else if (pending.contains(next) && text(eventKey(capture.target().appName(), capture.captureId(), next)).isPresent()) {
        pending.remove(next);
        through = next;
      } else {
        return through;
      }
    }
  }

  private Run updateRun(Run previous, FlowDebugEvent event, List<Loss> losses) {
    long first = previous == null ? event.getCaptureSeq() : Math.min(previous.firstCaptureSeq(), event.getCaptureSeq());
    long started = previous == null ? event.getTimestamp() : Math.min(previous.startedAt(), event.getTimestamp());
    long last = previous == null ? event.getTimestamp() : Math.max(previous.lastEventAt(), event.getTimestamp());
    long highest = previous == null ? event.getSeq() : Math.max(previous.highestSeq(), event.getSeq());
    Long terminal = previous == null ? null : previous.terminalSeq();
    String status = previous == null ? "ACTIVE" : previous.status();
    boolean root = event.getRunId().equals(event.getFlowInvocationId());
    if (root && ("flow.completed".equals(event.getKind()) || "flow.failed".equals(event.getKind()))) {
      if (terminal != null && !terminal.equals(event.getSeq())) {
        throw new DebugStoreException("Execution has conflicting terminal events");
      }
      terminal = event.getSeq();
      status = "flow.failed".equals(event.getKind()) ? "FAILED" : "COMPLETED";
    }
    return new Run(event.getRunId(), previous == null ? event.getFlowName() : previous.flowName(),
        previous == null || root || previous.graphId() == null ? event.getGraphId() : previous.graphId(), first, started, last, highest, terminal, status, (previous != null && previous.incomplete())
            || losses.stream().anyMatch(loss -> event.getRunId().equals(loss.runId())
                || (loss.runId() == null && event.getCaptureSeq() <= loss.lastCaptureSeq())));
  }

  @Override
  public EventPage readEvents(String appName, String captureId, String runId, long afterSeq, int limit) {
    if (afterSeq < 0 || limit < 1 || limit > 200) {
      throw new DebugStoreException("Invalid event cursor or page size");
    }
    var capture = getCapture(appName, captureId);
    if (capture.isEmpty()) {
      return new EventPage(List.of(), afterSeq, 0, false, false, "NOT_FOUND_OR_EXPIRED");
    }
    var run = capture.get().runs().stream().filter(value -> value.runId().equals(runId)).findFirst();
    if (run.isEmpty()) {
      return new EventPage(List.of(), afterSeq, 0, false, false, "NOT_FOUND_OR_EXPIRED");
    }
    long highest = run.get().highestSeq();
    var events = new ArrayList<FlowDebugEvent>();
    long end = Math.max(afterSeq, Math.min(highest, afterSeq > Long.MAX_VALUE - limit ? Long.MAX_VALUE : afterSeq + limit));
    boolean allPresent = true;
    boolean rootStart = false;
    var graphIds = new HashSet<String>();
    // Read bounded chunks through the cache adapter; Redis performs one MGET per chunk.
    for (long start = 1; start <= highest; start += 200) {
      var keys = new ArrayList<String>();
      long stop = Math.min(highest, start + 199);
      for (long seq = start; seq <= stop; seq++) {
        keys.add(runEventKey(appName, captureId, runId, seq));
      }
      var values = cache().getMany(region, keys);
      for (long seq = start; seq <= stop; seq++) {
        Object value = values.get(keys.get((int) (seq - start)));
        if (!(value instanceof String json)) {
          allPresent = false;
          continue;
        }
        var event = decode(FlowDebugEvent.class, json);
        graphIds.add(event.getGraphId());
        if (seq == 1) {
          rootStart = "flow.started".equals(event.getKind()) && runId.equals(event.getFlowInvocationId());
        }
        if (seq > afterSeq && seq <= end) {
          events.add(event);
        }
      }
    }
    var graphKeys = graphIds.stream().map(id -> graphKey(appName, captureId, id)).toList();
    boolean graphs = run.get().graphId() != null && graphIds.contains(run.get().graphId())
        && cache().getMany(region, graphKeys).size() == graphKeys.size();
    boolean complete = !run.get().incomplete() && allPresent && graphs && run.get().terminalSeq() != null
        && run.get().terminalSeq() == highest && rootStart;
    return new EventPage(events, end, highest, end < highest, complete,
        allPresent && graphs ? "AVAILABLE" : "INCOMPLETE_OR_EXPIRED");
  }

  private void putImmutable(String key, String value, long expiresAt) {
    if (!cache().putIfAbsent(region, key, value, ttl(expiresAt)) && !value.equals(text(key).orElse(null))) {
      throw new DebugStoreException("Conflicting immutable debug record");
    }
  }

  private void validateEvent(FlowDebugEvent event) {
    if (event == null || !"2.0".equals(event.getSchemaVersion()) || event.getTarget() == null
        || !event.getTarget().appName().equals(event.getAppName()) || event.getSeq() == null
        || event.getSeq() < 1 || event.getSeq() > limits.maxRunEvents() || event.getCaptureSeq() == null
        || event.getCaptureSeq() < 1 || event.getTimestamp() == null) {
      throw new DebugStoreException("Invalid debug event envelope");
    }
    requireText(event.getCaptureId());
    requireText(event.getRunId());
    requireText(event.getFlowInvocationId());
    requireText(event.getGraphId());
    if (event.getKind() == null || event.getScope() == null || !List.of("flow.started", "flow.completed", "flow.failed", "node.started", "node.completed",
        "node.failed", "node.skipped", "node.blocked", "input.transfer").contains(event.getKind())
        || !List.of("main", "error").contains(event.getScope())) {
      throw new DebugStoreException("Unsupported debug event kind or scope");
    }
    requireText(event.getFlowName());
  }

  private void requireOwnedSlot(Capture capture) {
    requireLive(capture);
    if (capture.state() == State.CLOSED || !capture.captureId().equals(cache().get(region, slotKey(capture)).orElse(null))) {
      throw new DebugStoreException("Capture target claim has expired or disappeared");
    }
  }

  private void requireLive(Capture capture) {
    long now = clock.getAsLong();
    if (capture.leaseUntil() <= now || capture.createdAt() + limits.maxCaptureMillis() <= now) {
      throw new DebugStoreException("Capture lease has expired");
    }
  }

  private void validateLease(long millis) {
    if (millis < 1000 || millis > 60000) {
      throw new DebugStoreException("Capture lease must be between one and sixty seconds");
    }
  }

  private Capture copy(Capture capture, State state, long version, long until, long through, long bytes,
                       List<Run> runs, List<Long> pending) {
    return new Capture(capture.captureId(), capture.operationId(), capture.owner(), capture.target(), capture.flowName(), state,
        version, capture.createdAt(), until, capture.expiresAt(), through, bytes, runs, pending, capture.losses(), capture.graphs(), capture.staged());
  }

  private Duration ttl(long expiresAt) {
    long remaining = expiresAt - clock.getAsLong();
    if (remaining <= 0) {
      throw unavailable();
    }
    return Duration.ofMillis(remaining);
  }

  private Duration leaseTtl(Capture capture) {
    return ttl(capture.leaseUntil());
  }

  private Optional<String> text(String key) {
    return cache().get(region, key).map(value -> {
      if (!(value instanceof String text)) {
        throw new DebugStoreException("Debug cache region contains an incompatible value");
      }
      return text;
    });
  }

  private static String encode(Object value) {
    return io.vavr.control.Try.of(() -> RECORDS.writeValueAsString(value)).get();
  }

  private static <T> T decode(Class<T> type, String json) {
    return Json.tryToBean(type, json).get();
  }

  private static int size(String json) {
    return json.getBytes(StandardCharsets.UTF_8).length;
  }

  private static String requireText(String value) {
    if (value == null || value.isBlank() || value.length() > 256) {
      throw new DebugStoreException("Invalid debug record identity");
    }
    return value;
  }

  private String captureKey(String app, String id) { return key("capture", app, id); }
  private String indexKey() { return "capture-index:v2"; }
  private String slotKey(Capture capture) {
    var target = capture.target();
    return key("target", target.appName(), target.profile(), target.appIncarnationId(), target.helperInstanceId(),
        Long.toString(target.helperStartedAtEpochMillis()));
  }
  private String graphKey(String app, String capture, String graph) { return capturePrefix(app, capture) + key("graph", graph); }
  private String eventKey(String app, String capture, long seq) { return key("event", app, capture, Long.toString(seq)); }
  private String runEventKey(String app, String capture, String run, long seq) {
    return capturePrefix(app, capture) + key("run-event", run, Long.toString(seq));
  }

  private String capturePrefix(String app, String capture) { return "{" + key("data", app, capture) + "}:"; }

  private static String key(String... parts) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(encode(parts).getBytes(StandardCharsets.UTF_8));
      return parts[0] + ":" + HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is unavailable", failure);
    }
  }

  private static DebugStoreException unavailable() {
    return new DebugStoreException("Debug recording does not exist or has expired");
  }
}
