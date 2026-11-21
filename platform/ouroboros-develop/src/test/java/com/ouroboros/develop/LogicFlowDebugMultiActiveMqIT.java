package com.ouroboros.develop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.ouroboros.app.cache.SpringBeanCacheManager;
import com.ouroboros.develop.logicflowdebug.DebugMqFixture;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.dev.service.*;
import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.util.Json;
import io.vavr.control.Try;

/** LF-DBG-MQ-001/002/003: actual MQBridge/commands/analyzer and two Redis-backed platform replicas. */
@EnabledIfSystemProperty(named = "logicflow.rabbit.port", matches = "[0-9]+")
@EnabledIfSystemProperty(named = "logicflow.redis.port", matches = "[0-9]+")
class LogicFlowDebugMultiActiveMqIT {
  private static final String APP = "debug-mq-it";
  private static final String OWNER = "alice";

  @Test
  void selectedProcessRecordsAcrossReplicasAndSurvivesBrokerOutageWithoutAffectingBusiness() throws Exception {
    try (var fixture = new Fixture(); var alice = new Helper("alice"); var bob = new Helper("bob")) {
      fixture.selected(alice);
      var first = fixture.platform();
      var second = fixture.platform();
      var firstApi = first.getBean(LogicFlowDebugCaptureService.class);
      var secondApi = second.getBean(LogicFlowDebugCaptureService.class);
      assertEquals("runtime-alice", firstApi.candidates(APP).getFirst().runtimeId());
      String id = UUID.randomUUID().toString();
      var capture = firstApi.start(APP, "runtime-alice", "flow", id);
      assertEquals("ACTIVE", capture.state());
      assertEquals(alice.identity.getInstanceId(), fixture.stores.getFirst().getCapture(APP, id).orElseThrow().target().helperInstanceId());
      alice.execute("ok", 1);
      alice.execute("fail", 2);
      bob.execute("ok", 1);
      await(() -> terminalRuns(secondApi, id) == 2, "two selected helper executions reach either platform");
      var history = secondApi.read(APP, id);
      assertEquals(2, history.runs().size());
      var events = new ArrayList<FlowDebugEvent>();
      for (var run : history.runs()) {
        var page = fixture.stores.getLast().readEvents(APP, id, run.runId(), 0, 200);
        assertTrue(page.complete(), page.availability());
        events.addAll(page.items());
      }
      assertTrue(events.stream().anyMatch(event -> "node.completed".equals(event.getKind()) && event.getPayload().containsKey("outputSnapshots")));
      assertTrue(events.stream().anyMatch(event -> "node.failed".equals(event.getKind())
          && Json.tryToJsonString(event.getPayload()).get().contains("fixture business failure")));
      assertTrue(events.stream().allMatch(event -> event.getTarget().helperInstanceId().equals(alice.identity.getInstanceId())));
      secondApi.renew(APP, id);
      first.close();
      var restarted = fixture.platform();
      assertEquals(2, restarted.getBean(LogicFlowDebugCaptureService.class).read(APP, id).runs().size());

      String broker = System.getProperty("logicflow.rabbit.container");
      if (broker != null) {
        assertEquals("logicflow-online-debug", docker("inspect", "--format", "{{index .Config.Labels \"codex.task\"}}", broker).trim());
        docker("stop", "--time", "1", broker);
        try {
          alice.execute("ok", 3);
          alice.execute("fail", 4);
          assertTrue(alice.process.isAlive(), "business process stays alive while debug transport is down");
        } finally {
          docker("start", broker);
        }
        await(() -> terminalRuns(secondApi, id) == 4, "retained debug packets recover after broker restart");
        secondApi.renew(APP, id);
      }
      var stopping = secondApi.stop(APP, id);
      await(() -> "CLOSED".equals(secondApi.renew(APP, id).state()), "stop drains and acknowledges buffered events");
      assertTrue(stopping.state().equals("DRAINING") || stopping.state().equals("CLOSED"));
      alice.execute("ok", broker == null ? 3 : 5);
      assertEquals(broker == null ? 2 : 4, secondApi.read(APP, id).runs().size());

      // The same app/profile is rebuilt: old control must never land on the new process.
      alice.close();
      try (var replacement = new Helper("alice")) {
        fixture.selected(replacement);
        assertNotEquals(alice.identity.getInstanceId(), replacement.identity.getInstanceId());
        var runtime = second.getBean(LogicFlowDebugRuntime.class);
        assertThrows(BusinessException.class, () -> runtime.control(fixture.stores.getFirst().getCapture(APP, id).orElseThrow(),
            LogicFlowDebugRuntime.Operation.STATUS, System.currentTimeMillis()));
        String newId = UUID.randomUUID().toString();
        assertEquals("ACTIVE", secondApi.start(APP, "runtime-alice", "flow", newId).state());
        replacement.execute("ok", 1);
        await(() -> terminalRuns(secondApi, newId) == 1, "replacement has a new explicit capture");
        fixture.clients.getFirst().clear(fixture.region);
        assertThrows(BusinessException.class, () -> secondApi.renew(APP, newId));
      }
    }
  }

  private static int terminalRuns(LogicFlowDebugCaptureService service, String id) {
    return (int) service.read(APP, id).runs().stream().filter(run -> run.terminalSeq() != null).count();
  }

  private static void await(BooleanSupplier condition, String reason) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(100);
    }
    fail("Timed out: " + reason);
  }

  private static String docker(String... arguments) throws Exception {
    var command = new ArrayList<String>(); command.add("docker"); command.addAll(List.of(arguments));
    var process = new ProcessBuilder(command).redirectErrorStream(true).start();
    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Docker fixture lifecycle timed out");
    String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    assertEquals(0, process.exitValue(), output);
    return output;
  }

  private static final class Helper implements AutoCloseable {
    final Process process;
    final BufferedWriter input;
    final Path log;
    final HelperIdentity identity;

    Helper(String profile) throws Exception {
      Path root = Path.of("target", "logicflow-mq-it", UUID.randomUUID().toString()).toAbsolutePath();
      Files.createDirectories(root);
      log = root.resolve("helper-" + profile + ".log");
      var ready = root.resolve("identity.json");
      var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
          "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
          DebugMqFixture.class.getName(), APP, profile, System.getProperty("logicflow.rabbit.port"), ready.toString());
      builder.environment().put("OUROBOROS_APP_INCARNATION_ID", UUID.randomUUID().toString());
      process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
      input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
      try {
        await(() -> {
          assertTrue(process.isAlive(), "Helper startup failed: " + log);
          return Files.isRegularFile(ready);
        }, "helper ready: " + log);
        var value = Json.tryToMap(Files.readString(ready)).get();
        identity = new HelperIdentity((String) value.get("instanceId"), ((Number) value.get("startedAtEpochMillis")).longValue(),
            (String) value.get("appIncarnationId"));
      } catch (Exception | AssertionError failure) {
        process.destroyForcibly();
        throw failure;
      }
    }

    void execute(String operation, int expectedCount) throws Exception {
      input.write(operation); input.newLine(); input.flush();
      await(() -> {
        try { return Files.readString(log).lines().filter(line -> line.startsWith("BUSINESS|")).count() >= expectedCount; }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
      }, "business execution remains responsive: " + log);
      String last = Files.readString(log).lines().filter(line -> line.startsWith("BUSINESS|")).reduce((a,b) -> b).orElseThrow();
      assertEquals("BUSINESS|" + (operation.equals("fail") ? "failed" : "success"), last);
    }

    @Override public void close() throws Exception {
      if (process.isAlive()) {
        input.write("exit"); input.newLine(); input.flush();
        boolean graceful = process.waitFor(10, TimeUnit.SECONDS);
        if (!graceful) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        assertTrue(graceful, "Helper dynamic MQ bindings did not shut down: " + log);
        assertEquals(0, process.exitValue(), "Helper shutdown failed: " + log);
      }
      input.close();
    }
  }

  private static final class Fixture implements AutoCloseable {
    final String region = "logicflow-mq-it-" + UUID.randomUUID();
    final List<LettuceConnectionFactory> connections = new ArrayList<>();
    final List<SpringBeanCacheManager> clients = new ArrayList<>();
    final List<CacheLogicFlowDebugEventStore> stores = new ArrayList<>();
    final List<ConfigurableApplicationContext> platforms = new ArrayList<>();
    final DevRuntimeService runtime = mock(DevRuntimeService.class);

    Fixture() {
      when(runtime.inspect(any())).thenReturn(Try.failure(new IllegalStateException("not selected")));
    }

    void selected(Helper helper) {
      var instance = RuntimeInstance.builder().runtimeId("runtime-alice").appName(APP).profile(OWNER)
          .backendType(BackendType.DOCKER).runtimeKind(RuntimeKind.TEST_APP).status(RuntimeStatus.RUNNING)
          .runtimeGeneration(1L).backendMetadata(Map.of("OUROBOROS_DEBUG_OWNER", OWNER,
              "OUROBOROS_APP_INCARNATION_ID", helper.identity.getAppIncarnationId())).build();
      when(runtime.inspect(RuntimeIdentityKey.from(instance))).thenReturn(Try.success(instance));
    }

    ConfigurableApplicationContext platform() {
      var connection = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("logicflow.redis.port")));
      connection.afterPropertiesSet(); connections.add(connection);
      var manager = RedisCacheManager.builder(connection).cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()).build();
      manager.afterPropertiesSet();
      var beans = new StaticListableBeanFactory(Map.of("cacheManager", manager, "connections", connection));
      var client = new SpringBeanCacheManager(beans.getBeanProvider(CacheManager.class), beans.getBeanProvider(RedisConnectionFactory.class));
      var store = new CacheLogicFlowDebugEventStore(() -> client, region, System::currentTimeMillis,
          CacheLogicFlowDebugEventStore.Limits.defaults(), true);
      clients.add(client); stores.add(store);
      LogicFlowDebugAccess access = new LogicFlowDebugAccess() {
        @Override public String currentOwner() { return OWNER; }
        @Override public boolean canDebug(String app, String profile, String owner) {
          return APP.equals(app) && OWNER.equals(owner) && (profile == null || OWNER.equals(profile));
        }
      };
      var context = DebugMqFixture.start(DebugMqFixture.Platform.class, "develop-mq-it", "it",
          Integer.parseInt(System.getProperty("logicflow.rabbit.port")),
          Map.of("debugStore", store, "runtimeService", runtime, "debugAccess", access));
      platforms.add(context);
      return context;
    }

    @Override public void close() {
      try {
        platforms.forEach(ConfigurableApplicationContext::close);
        if (!clients.isEmpty()) clients.getFirst().clear(region);
      } finally { connections.forEach(LettuceConnectionFactory::destroy); }
    }
  }
}
