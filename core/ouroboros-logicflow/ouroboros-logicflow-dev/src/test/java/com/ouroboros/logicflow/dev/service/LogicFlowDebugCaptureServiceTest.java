package com.ouroboros.logicflow.dev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.Capture;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugRuntime.Operation;
import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;

class LogicFlowDebugCaptureServiceTest {
  private static final FlowDebugTarget TARGET = new FlowDebugTarget("crm", "alice", "container", 1, "helper", 100, "app");
  private final AtomicReference<String> owner = new AtomicReference<>("alice");
  private final AtomicReference<Boolean> allowed = new AtomicReference<>(true);
  private final LogicFlowDebugRuntime runtime = mock(LogicFlowDebugRuntime.class);
  private final MemoryCacheManager cache = new MemoryCacheManager();
  private final LogicFlowDebugEventStore store = new CacheLogicFlowDebugEventStore(() -> cache, "test-" + UUID.randomUUID(),
      System::currentTimeMillis, CacheLogicFlowDebugEventStore.Limits.defaults(), false);
  private final StaticListableBeanFactory platform = new StaticListableBeanFactory();
  private final LogicFlowDebugCaptureService first = service();
  private final LogicFlowDebugCaptureService second = service();

  LogicFlowDebugCaptureServiceTest() {
    platform.addBean("runtime", runtime);
    platform.addBean("access", new LogicFlowDebugAccess() {
      @Override public String currentOwner() { return owner.get(); }
      @Override public boolean canDebug(String appName, String profile, String principal) {
        return allowed.get() && "crm".equals(appName) && (profile == null || profile.equals(principal));
      }
    });
    when(runtime.probe(eq("crm"), eq("alice"), eq("container"), anyString())).thenReturn(TARGET);
  }

  @Test
  void aLostStartResponseCanBeRetriedByAnotherReplicaWithoutAllocatingAnotherCapture() {
    when(runtime.control(any(), eq(Operation.START), anyLong()))
        .thenThrow(new BusinessException("助手响应超时"))
        .thenAnswer(call -> status(call.getArgument(0), true));
    String operation = UUID.randomUUID().toString();
    assertThrows(BusinessException.class, () -> first.start("crm", "container", "flow", operation));
    assertEquals("STARTING", second.read("crm", operation).state());
    assertEquals("ACTIVE", second.start("crm", "container", "flow", operation).state());
    assertEquals(List.of(operation), first.list("crm", "flow").stream().map(view -> view.captureId()).toList());
    assertThrows(BusinessException.class, () -> second.start("crm", "container", "other-flow", operation));
  }

  @Test
  void ownershipAndCurrentApplicationAuthorizationAreRequiredOnReadAndControl() {
    String id = start();
    owner.set("bob");
    assertEquals(List.of(), second.list("crm", "flow"));
    assertThrows(BusinessException.class, () -> second.read("crm", id));
    assertThrows(BusinessException.class, () -> second.stop("crm", id));
    owner.set("alice");
    allowed.set(false);
    assertThrows(BusinessException.class, () -> first.renew("crm", id));
  }

  @Test
  void stopWaitsForOutstandingInvocationsBeforeClosingAndKeepsHistory() {
    String id = start();
    when(runtime.control(any(), eq(Operation.STOP), anyLong())).thenAnswer(call -> {
      Capture capture = call.getArgument(0);
      return new FlowDebugCaptureStatus(capture.captureId(), capture.target(), false, 5000, 0, 1, 0, 0, 0, 0, 1, List.of(), null);
    }).thenAnswer(call -> status(call.getArgument(0), false));
    assertEquals("DRAINING", first.stop("crm", id).state());
    assertEquals("CLOSED", second.renew("crm", id).state());
    assertEquals(id, first.read("crm", id).captureId());
  }

  @Test
  void saturatedStoreStopsHelperOnNextControlAndCannotRenewItsAdmission() {
    String id = start();
    var active = store.getCapture("crm", id).orElseThrow();
    store.updateCapture("crm", id, active.version(), LogicFlowDebugEventStore.State.SATURATED, 1000);
    assertEquals("SATURATED", second.read("crm", id).state());
    when(runtime.control(any(), eq(Operation.STOP), anyLong())).thenAnswer(call -> status(call.getArgument(0), false));
    assertEquals("CLOSED", second.renew("crm", id).state());
  }

  @Test
  void helperSaturationIsVisibleToOtherReplicasBeforeClosingDrainedCapture() {
    String id = start();
    when(runtime.control(any(), eq(Operation.RENEW), anyLong())).thenAnswer(call -> status(call.getArgument(0), false));
    assertEquals("SATURATED", first.renew("crm", id).state());
    assertEquals("SATURATED", second.read("crm", id).state());
    when(runtime.control(any(), eq(Operation.STOP), anyLong())).thenAnswer(call -> status(call.getArgument(0), false));
    assertEquals("CLOSED", second.renew("crm", id).state());
  }

  private String start() {
    when(runtime.control(any(), eq(Operation.START), anyLong())).thenAnswer(call -> status(call.getArgument(0), true));
    return first.start("crm", "container", "flow", UUID.randomUUID().toString()).captureId();
  }

  private LogicFlowDebugCaptureService service() {
    return new LogicFlowDebugCaptureService(store, platform.getBeanProvider(LogicFlowDebugAccess.class),
        platform.getBeanProvider(LogicFlowDebugRuntime.class));
  }

  private FlowDebugCaptureStatus status(Capture capture, boolean accepting) {
    return new FlowDebugCaptureStatus(capture.captureId(), capture.target(), accepting, 10000, 0, 1, 0, 0, 0, 0, 0, List.of(), null);
  }
}
