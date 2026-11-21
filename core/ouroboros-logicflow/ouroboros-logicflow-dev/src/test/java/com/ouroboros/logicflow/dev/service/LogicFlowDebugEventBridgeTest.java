package com.ouroboros.logicflow.dev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.CreateCapture;
import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;
import com.ouroboros.util.Json;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class LogicFlowDebugEventBridgeTest {
  private final FlowDebugTarget target = new FlowDebugTarget("crm", "alice", "runtime", 1, "helper", 100, "app");
  private final MemoryCacheManager cache = new MemoryCacheManager();
  private final LogicFlowDebugEventStore store = new CacheLogicFlowDebugEventStore(() -> cache, "test-" + UUID.randomUUID(),
      System::currentTimeMillis, CacheLogicFlowDebugEventStore.Limits.defaults(), false);
  private final LogicFlowDebugCaptureService captures = mock(LogicFlowDebugCaptureService.class);
  private final LogicFlowDebugNotifications sender = mock(LogicFlowDebugNotifications.class);
  private final StaticListableBeanFactory platform = new StaticListableBeanFactory();
  private final LogicFlowDebugEventBridge bridge = bridge();

  private LogicFlowDebugEventBridge bridge() {
    platform.addBean("notifications", sender);
    return new LogicFlowDebugEventBridge(store, captures, platform.getBeanProvider(LogicFlowDebugNotifications.class));
  }

  @Test
  void graphAndEventAreAvailableBeforeTheOwnerReceivesAnUpdateAndDuplicatesDoNotNotify() {
    create();
    when(captures.allowed("crm", "alice", "alice")).thenReturn(true);
    var delivered = new ArrayList<Long>();
    doAnswer(call -> {
      var event = store.readEvents("crm", "capture", "run", 0, 10).items().getFirst();
      assertEquals("flow.started", event.getKind());
      assertFalse(event.getPayload().containsKey("graph"));
      assertEquals("flow", store.getGraph("crm", "capture", event.getGraphId()).orElseThrow().flowName());
      delivered.add(call.getArgument(2, Long.class));
      return null;
    }).when(sender).updated(org.mockito.ArgumentMatchers.eq("alice"),
        org.mockito.ArgumentMatchers.eq("capture"), org.mockito.ArgumentMatchers.anyLong());

    deliver(event());
    deliver(event());
    assertEquals(List.of(1L), delivered);
  }

  @Test
  void invalidGraphAndWrongProcessCannotCreateReplayRecords() {
    create();
    var invalid = event();
    invalid.getPayload().clear();
    deliver(invalid);
    var wrong = event();
    wrong.setTarget(new FlowDebugTarget("crm", "alice", "runtime", 1, "different", 101, "app"));
    deliver(wrong);

    assertEquals(0, store.getCapture("crm", "capture").orElseThrow().receivedThrough());
    assertEquals(List.of(), store.getCapture("crm", "capture").orElseThrow().runs());
    verifyNoInteractions(sender);
  }

  @Test
  void revokedApplicationAccessSuppressesLiveNotification() {
    create();
    deliver(event());
    assertEquals(1, store.getCapture("crm", "capture").orElseThrow().receivedThrough());
    verifyNoInteractions(sender);
  }

  @Test
  void fullRecordingPublishesSaturationAndExplicitLossWithoutFalseAcknowledgedEvents() {
    var bounded = new CacheLogicFlowDebugEventStore(() -> cache, "bounded-" + UUID.randomUUID(),
        System::currentTimeMillis, new CacheLogicFlowDebugEventStore.Limits(2, 1, 10, 10, 200, 65536, 262144, 60000, 60000), false);
    bounded.tryCreateCapture(new CreateCapture("capture", "operation", "alice", target, "flow", 30000));
    var listener = new LogicFlowDebugEventBridge(bounded, captures, platform.getBeanProvider(LogicFlowDebugNotifications.class));
    listener.handleMessage(MessageBuilder.withPayload(Json.toJsonString(event())).build());
    var saturated = bounded.getCapture("crm", "capture").orElseThrow();
    assertEquals(LogicFlowDebugEventStore.State.SATURATED, saturated.state());
    assertEquals(1, saturated.receivedThrough());
    assertEquals("shared-recording-capacity", saturated.losses().getFirst().reason());
    assertEquals("INCOMPLETE", saturated.runs().getFirst().status());
    assertEquals(true, saturated.runs().getFirst().incomplete());
    verifyNoInteractions(sender);
  }

  private void create() {
    store.tryCreateCapture(new CreateCapture("capture", "operation", "alice", target, "flow", 30000));
  }

  private void deliver(FlowDebugEvent event) {
    bridge.handleMessage(MessageBuilder.withPayload(Json.toJsonString(event)).build());
  }

  private FlowDebugEvent event() {
    var event = new FlowDebugEvent();
    event.setSchemaVersion("2.0");
    event.setAppName("crm");
    event.setCaptureId("capture");
    event.setTarget(target);
    event.setCaptureSeq(1L);
    event.setSeq(1L);
    event.setRunId("run");
    event.setSessionId("run");
    event.setFlowInvocationId("run");
    event.setKind("flow.started");
    event.setFlowName("flow");
    event.setScope("main");
    event.setGraphId("graph");
    event.setTimestamp(1L);
    event.getPayload().put("graph", new FlowDebugGraph("graph", "flow", "rev", true, "entry", "exit", null, List.of(), List.of()));
    return event;
  }
}
