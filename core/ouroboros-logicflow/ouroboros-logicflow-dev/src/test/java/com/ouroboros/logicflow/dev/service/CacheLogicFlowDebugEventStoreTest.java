package com.ouroboros.logicflow.dev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.*;
import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;

class CacheLogicFlowDebugEventStoreTest {
  private final AtomicLong clock = new AtomicLong(1000);
  private final MemoryCacheManager cache = new MemoryCacheManager();
  private final String region = "debug-" + UUID.randomUUID();
  private final FlowDebugTarget target = new FlowDebugTarget("test-app", "test", "container-1", 1, "helper-1", 100, "incarnation-1");
  private final CacheLogicFlowDebugEventStore first = store();
  private final CacheLogicFlowDebugEventStore second = store();

  private CacheLogicFlowDebugEventStore store() {
    return new CacheLogicFlowDebugEventStore(() -> cache, region, clock::get,
        CacheLogicFlowDebugEventStore.Limits.defaults(), false);
  }

  @Test
  void onlyOnePlatformWinsTheSameTargetAndRepeatedOperationIsIdempotent() {
    var wins = new AtomicInteger();
    var tasks = new ArrayList<CompletableFuture<Void>>();
    for (int i = 0; i < 40; i++) {
      int index = i;
      tasks.add(CompletableFuture.runAsync(() -> {
        try {
          (index % 2 == 0 ? first : second).tryCreateCapture(create("capture-" + index));
          wins.incrementAndGet();
        } catch (DebugStoreException conflict) {
          assertTrue(conflict.getMessage().contains("already has an active capture"));
        }
      }));
    }
    CompletableFuture.allOf(tasks.toArray(CompletableFuture<?>[]::new)).join();
    assertEquals(1, wins.get());
    var winner = first.listCaptures("test-app").getFirst();
    assertEquals(winner, second.tryCreateCapture(create(winner.captureId())));
    assertEquals(1, second.listCaptures("test-app").size());
  }

  @Test
  void staleVersionAndLateCloseCannotAffectTheNextCapture() {
    var capture = first.tryCreateCapture(create("old"));
    var active = second.updateCapture("test-app", "old", capture.version(), State.ACTIVE, 30000);
    assertThrows(DebugStoreException.class, () -> first.updateCapture("test-app", "old", capture.version(), State.ACTIVE, 30000));
    first.updateCapture("test-app", "old", active.version(), State.CLOSED, 30000);
    var next = second.tryCreateCapture(create("new"));
    assertThrows(DebugStoreException.class, () -> first.updateCapture("test-app", "old", active.version(), State.CLOSED, 30000));
    assertEquals(next, second.getCapture("test-app", "new").orElseThrow());
  }

  @Test
  void replacementProcessCanCaptureImmediatelyAndOldCloseCannotReleaseItsClaim() {
    var old = first.tryCreateCapture(create("old"));
    var replacement = new FlowDebugTarget("test-app", "test", "container-2", 2, "helper-2", 200, "incarnation-2");
    var fresh = new CreateCapture("new", "new-operation", "owner", replacement, "flow", 30000);
    var next = second.tryCreateCapture(fresh);
    first.updateCapture("test-app", "old", old.version(), State.CLOSED, 30000);
    assertEquals(next, first.tryCreateCapture(fresh));
    assertThrows(DebugStoreException.class, () -> first.tryCreateCapture(
        new CreateCapture("duplicate", "duplicate-operation", "owner", replacement, "flow", 30000)));
    assertEquals(2, second.listCaptures("test-app").size());
  }

  @Test
  void reorderedDuplicateEventsAdvanceOnlyContiguousAcknowledgement() {
    first.tryCreateCapture(create("capture"));
    first.putGraph("test-app", "capture", graph());
    assertEquals(0, first.append(event(2, "node.completed")).receivedThrough());
    assertEquals(0, second.append(event(3, "flow.completed")).receivedThrough());
    assertEquals(3, second.append(event(1, "flow.started")).receivedThrough());
    assertTrue(first.append(event(2, "node.completed")).duplicate());
    var capture = second.getCapture("test-app", "capture").orElseThrow();
    assertEquals(1, capture.runs().size());
    assertEquals(3, capture.runs().getFirst().highestSeq());
    var page = second.readEvents("test-app", "capture", "run", 0, 2);
    assertEquals(List.of(1L, 2L), page.items().stream().map(FlowDebugEvent::getSeq).toList());
    assertTrue(page.hasMore());
    assertTrue(page.complete());
    assertEquals(3, second.readEvents("test-app", "capture", "run", page.afterSeq(), 2).items().getFirst().getSeq());
  }

  @Test
  void identityCollisionAndUnknownCaptureCannotReceiveAnAcknowledgement() {
    first.tryCreateCapture(create("capture"));
    first.append(event(1, "flow.started"));
    var changed = event(1, "flow.failed");
    assertThrows(DebugStoreException.class, () -> first.append(changed));
    var spoofed = event(2, "node.completed");
    spoofed.setTarget(new FlowDebugTarget("test-app", "test", "other-container", 1, "helper-1", 100, "incarnation-1"));
    assertThrows(DebugStoreException.class, () -> second.append(spoofed));
    assertEquals(1, first.getCapture("test-app", "capture").orElseThrow().receivedThrough());
    var unknown = event(2, "node.completed");
    unknown.setCaptureId("unknown");
    assertThrows(DebugStoreException.class, () -> second.append(unknown));
    assertTrue(second.getCapture("test-app", "unknown").isEmpty());
  }

  @Test
  void expiredLeaseAndLostControlCannotBeRevivedByRenewalOrDelayedEvents() {
    var capture = first.tryCreateCapture(create("capture"));
    clock.addAndGet(30001);
    assertThrows(DebugStoreException.class, () -> first.updateCapture("test-app", "capture", capture.version(), State.ACTIVE, 30000));
    assertThrows(DebugStoreException.class, () -> first.append(event(1, "flow.started")));
    cache.clear(region);
    assertThrows(DebugStoreException.class, () -> second.updateCapture("test-app", "capture", capture.version(), State.ACTIVE, 30000));
    assertEquals("NOT_FOUND_OR_EXPIRED", second.readEvents("test-app", "capture", "run", 0, 100).availability());
  }

  @Test
  void cachedGraphAndEventsAreImmutableAndEvictionIsVisibleAsIncomplete() {
    first.tryCreateCapture(create("capture"));
    first.putGraph("test-app", "capture", graph());
    var start = event(1, "flow.started");
    start.getPayload().put("value", "before");
    first.append(start);
    start.getPayload().put("value", "after");
    first.append(event(2, "flow.completed"));
    assertEquals("before", second.readEvents("test-app", "capture", "run", 0, 100).items().getFirst().getPayload().get("value"));
    assertTrue(second.readEvents("test-app", "capture", "run", 0, 100).complete());
    assertThrows(DebugStoreException.class, () -> first.putGraph("test-app", "capture",
        new FlowDebugGraph("graph", "flow", "changed", true, "entry", "exit", null, List.of(), List.of())));
    assertThrows(DebugStoreException.class, () -> second.readEvents("test-app", "capture", "run", 0, Integer.MAX_VALUE));
    assertTrue(second.readEvents("test-app", "capture", "run", 100, 100).items().isEmpty());
    clock.addAndGet(2400001);
    assertTrue(second.getCapture("test-app", "capture").isEmpty());
  }

  @Test
  void multiActiveConfigurationRejectsLocalMemoryRatherThanSilentlyFallingBack() {
    var shared = new CacheLogicFlowDebugEventStore(() -> cache, region, clock::get,
        CacheLogicFlowDebugEventStore.Limits.defaults(), true);
    assertThrows(DebugStoreException.class, shared::capabilities);
  }

  @Test
  void graphAndDuplicatedEventStorageAreChargedOnceAndReadChecksNestedGraphs() {
    first.tryCreateCapture(create("capture"));
    first.putGraph("test-app", "capture", graph());
    long graphBytes = first.getCapture("test-app", "capture").orElseThrow().bytes();
    assertTrue(graphBytes > 0);
    second.putGraph("test-app", "capture", graph());
    assertEquals(graphBytes, first.getCapture("test-app", "capture").orElseThrow().bytes());
    first.append(event(1, "flow.started"));
    long eventBytes = first.getCapture("test-app", "capture").orElseThrow().bytes();
    second.append(event(1, "flow.started"));
    assertEquals(eventBytes, first.getCapture("test-app", "capture").orElseThrow().bytes());
    var nested = event(2, "flow.started");
    nested.setFlowInvocationId("child"); nested.setGraphId("missing-graph");
    first.append(nested); first.append(event(3, "flow.completed"));
    assertFalse(second.readEvents("test-app", "capture", "run", 0, 100).complete());
  }

  @Test
  void partialEventWriteRetriesReservedBudgetWithoutDoubleChargingOrAcknowledgingEarly() {
    var faulty = spy(new MemoryCacheManager());
    var store = new CacheLogicFlowDebugEventStore(() -> faulty, region, clock::get,
        CacheLogicFlowDebugEventStore.Limits.defaults(), false);
    store.tryCreateCapture(create("capture"));
    doThrow(new IllegalStateException("cache disconnected"))
        .doCallRealMethod().when(faulty).putIfAbsent(eq(region), argThat(key -> key.contains(":run-event:")), anyString(), any());
    assertThrows(IllegalStateException.class, () -> store.append(event(1, "flow.started")));
    var partial = store.getCapture("test-app", "capture").orElseThrow();
    assertEquals(0, partial.receivedThrough());
    assertEquals(1, partial.staged().size());
    assertTrue(partial.bytes() > 0);
    assertEquals(1, store.append(event(1, "flow.started")).receivedThrough());
    var completed = store.getCapture("test-app", "capture").orElseThrow();
    assertEquals(partial.bytes(), completed.bytes());
    assertTrue(completed.staged().isEmpty());
  }

  @Test
  void applicationBudgetIsAtomicallyReservedAndAdmissionReturnsAfterAbsoluteExpiry() {
    for (int index = 0; index < 4; index++) {
      var candidate = new FlowDebugTarget("test-app", "profile-" + index, "container-" + index, 1, "helper", 100, "incarnation");
      first.tryCreateCapture(new CreateCapture("capture-" + index, "op-" + index, "owner", candidate, "flow", 30000));
    }
    var fifth = new FlowDebugTarget("test-app", "profile-5", "container-5", 1, "helper", 100, "incarnation");
    assertThrows(DebugStoreException.class, () -> second.tryCreateCapture(new CreateCapture("fifth", "op", "owner", fifth, "flow", 30000)));
    assertTrue(second.getCapture("test-app", "fifth").isEmpty());
    clock.addAndGet(2400001);
    assertEquals("fifth", second.tryCreateCapture(new CreateCapture("fifth", "op", "owner", fifth, "flow", 30000)).captureId());
  }

  @Test
  void equivalentPayloadMapOrderDoesNotTurnRedeliveryIntoAConflict() {
    first.tryCreateCapture(create("capture"));
    var original = event(1, "flow.started");
    var nested = new java.util.LinkedHashMap<String, Object>();
    nested.put("b", 2); nested.put("a", 1);
    original.getPayload().put("snapshot", nested);
    first.append(original);
    var redelivery = event(1, "flow.started");
    var reordered = new java.util.LinkedHashMap<String, Object>();
    reordered.put("a", 1); reordered.put("b", 2);
    redelivery.getPayload().put("snapshot", reordered);
    assertTrue(second.append(redelivery).duplicate());
    assertEquals(1, second.getCapture("test-app", "capture").orElseThrow().receivedThrough());
  }

  private CreateCapture create(String id) {
    return new CreateCapture(id, "operation-" + id, "owner", target, "flow", 30000);
  }

  private FlowDebugGraph graph() {
    return new FlowDebugGraph("graph", "flow", "revision", true, "entry", "exit", null, List.of(), List.of());
  }

  private FlowDebugEvent event(long sequence, String kind) {
    var event = new FlowDebugEvent();
    event.setSchemaVersion("2.0");
    event.setAppName("test-app");
    event.setCaptureId("capture");
    event.setTarget(target);
    event.setCaptureSeq(sequence);
    event.setSeq(sequence);
    event.setRunId("run");
    event.setSessionId("run");
    event.setFlowInvocationId("run");
    event.setScope("main");
    event.setGraphId("graph");
    event.setFlowName("flow");
    event.setKind(kind);
    event.setTimestamp(1000 + sequence);
    return event;
  }
}
