package com.ouroboros.develop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.ouroboros.app.cache.SpringBeanCacheManager;
import com.ouroboros.logicflow.dev.service.CacheLogicFlowDebugEventStore;
import com.ouroboros.logicflow.dev.service.DebugStoreException;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore;
import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus.Loss;
import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;

/** LF-DBG-STORE-001/002: two independent clients, actual default repository and Redis adapter. */
@EnabledIfSystemProperty(named = "logicflow.redis.port", matches = "[0-9]+")
class LogicFlowDebugSharedRepositoryIT {
  private static final String APP = "debug-it";
  private static final FlowDebugTarget TARGET = new FlowDebugTarget(APP, "alice", "runtime-1", 1, "helper-1", 100, "incarnation");

  @Test
  void twoReplicasCompeteAndRecoverOrderedHistoryAfterReplicaRestart() {
    try (var fixture = new Fixture()) {
      var wins = new AtomicInteger();
      var tasks = new ArrayList<CompletableFuture<Void>>();
      for (int index = 0; index < 40; index++) {
        int id = index;
        tasks.add(CompletableFuture.runAsync(() -> {
          try {
            fixture.stores.get(id % 2).tryCreateCapture(create("capture-" + id));
            wins.incrementAndGet();
          } catch (DebugStoreException conflict) {
            assertTrue(conflict.getMessage().contains("active capture"));
          }
        }));
      }
      CompletableFuture.allOf(tasks.toArray(CompletableFuture<?>[]::new)).join();
      assertEquals(1, wins.get());
      var first = fixture.stores.getFirst();
      var second = fixture.stores.getLast();
      var capture = first.listCaptures(APP).getFirst();
      String id = capture.captureId();
      first.putGraph(APP, id, graph());
      assertEquals(0, second.append(event(id, 2, "node.completed")).receivedThrough());
      assertEquals(0, first.append(event(id, 3, "flow.completed")).receivedThrough());
      assertEquals(3, second.append(event(id, 1, "flow.started")).receivedThrough());
      assertTrue(first.append(event(id, 2, "node.completed")).duplicate());
      var restarted = fixture.store(fixture.clients.getLast());
      var page = restarted.readEvents(APP, id, "run", 0, 2);
      assertEquals(List.of(1L, 2L), page.items().stream().map(FlowDebugEvent::getSeq).toList());
      assertTrue(page.complete());
      assertTrue(page.hasMore());
      assertEquals(3, restarted.readEvents(APP, id, "run", page.afterSeq(), 2).items().getFirst().getSeq());
      assertTrue(restarted.getCapture(APP, id).orElseThrow().bytes() > 0);
      assertTrue(restarted.capabilities().shared());
    }
  }

  @Test
  void individualEvictionAndCacheClearCannotPretendToBeCompleteOrReviveControl() {
    try (var fixture = new Fixture()) {
      var first = fixture.stores.getFirst();
      var second = fixture.stores.getLast();
      var capture = first.tryCreateCapture(create("capture"));
      first.putGraph(APP, "capture", graph());
      first.append(event("capture", 1, "flow.started"));
      first.append(event("capture", 2, "flow.completed"));
      assertTrue(second.readEvents(APP, "capture", "run", 0, 100).complete());
      try (var connection = fixture.factories.getFirst().getConnection()) {
        var keys = connection.keyCommands().keys((fixture.region + "::*").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var graph = keys.stream().filter(key -> new String(key, java.nio.charset.StandardCharsets.UTF_8).contains(":graph:")).findFirst().orElseThrow();
        connection.keyCommands().del(graph);
      }
      assertFalse(second.readEvents(APP, "capture", "run", 1, 100).complete());
      assertEquals("INCOMPLETE_OR_EXPIRED", second.readEvents(APP, "capture", "run", 0, 100).availability());
      fixture.clients.getFirst().clear(fixture.region);
      assertThrows(DebugStoreException.class, () -> second.updateCapture(APP, "capture", capture.version(), LogicFlowDebugEventStore.State.ACTIVE, 20000));
      assertThrows(DebugStoreException.class, () -> second.append(event("capture", 2, "flow.completed")));
      assertEquals("NOT_FOUND_OR_EXPIRED", second.readEvents(APP, "capture", "run", 0, 100).availability());
    }
  }

  @Test
  void declaredLossAdvancesAcrossPermanentHoleAndCannotMarkExecutionSuccessful() {
    try (var fixture = new Fixture()) {
      var first = fixture.stores.getFirst();
      var second = fixture.stores.getLast();
      first.tryCreateCapture(create("capture"));
      first.putGraph(APP, "capture", graph());
      first.append(event("capture", 1, "flow.started"));
      second.append(event("capture", 3, "flow.completed"));
      assertEquals(3, first.declareLoss(APP, "capture", TARGET, new Loss("run", 2, 2, "test-disconnect")).receivedThrough());
      assertTrue(second.getCapture(APP, "capture").orElseThrow().runs().getFirst().incomplete());
      assertFalse(second.readEvents(APP, "capture", "run", 0, 100).complete());
    }
  }

  private static LogicFlowDebugEventStore.CreateCapture create(String id) {
    return new LogicFlowDebugEventStore.CreateCapture(id, id, "alice", TARGET, "flow", 30000);
  }

  private static FlowDebugGraph graph() {
    return new FlowDebugGraph("graph", "flow", "revision", true, "entry", "exit", null, List.of(), List.of());
  }

  private static FlowDebugEvent event(String id, long seq, String kind) {
    var event = new FlowDebugEvent();
    event.setSchemaVersion("2.0"); event.setAppName(APP); event.setCaptureId(id); event.setTarget(TARGET);
    event.setCaptureSeq(seq); event.setSeq(seq); event.setRunId("run"); event.setSessionId("run");
    event.setFlowInvocationId("run"); event.setScope("main"); event.setGraphId("graph");
    event.setFlowName("flow"); event.setKind(kind); event.setTimestamp(1000 + seq);
    return event;
  }

  private static final class Fixture implements AutoCloseable {
    final String region = "logicflow-repository-it-" + UUID.randomUUID();
    final List<LettuceConnectionFactory> factories = new ArrayList<>();
    final List<SpringBeanCacheManager> clients = new ArrayList<>();
    final List<CacheLogicFlowDebugEventStore> stores = new ArrayList<>();

    Fixture() {
      for (int index = 0; index < 2; index++) {
        var factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("logicflow.redis.port")));
        factory.afterPropertiesSet(); factories.add(factory);
        var manager = RedisCacheManager.builder(factory).cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()).build();
        manager.afterPropertiesSet();
        var beans = new StaticListableBeanFactory(Map.of("cacheManager", manager, "connections", factory));
        var client = new SpringBeanCacheManager(beans.getBeanProvider(CacheManager.class), beans.getBeanProvider(RedisConnectionFactory.class));
        clients.add(client); stores.add(store(client));
      }
    }

    CacheLogicFlowDebugEventStore store(SpringBeanCacheManager client) {
      return new CacheLogicFlowDebugEventStore(() -> client, region, System::currentTimeMillis,
          CacheLogicFlowDebugEventStore.Limits.defaults(), true);
    }

    @Override public void close() {
      try { clients.getFirst().clear(region); } finally { factories.forEach(LettuceConnectionFactory::destroy); }
    }
  }
}
