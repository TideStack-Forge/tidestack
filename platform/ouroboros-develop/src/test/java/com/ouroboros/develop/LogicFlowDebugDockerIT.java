package com.ouroboros.develop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.ouroboros.develop.logicflowdebug.DebugBrowserFixture;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugCaptureService;
import com.ouroboros.util.Json;

/** LF-DBG-CONTAINER-001: actual Docker discovery, extend JARs, command transport and incarnation fencing. */
@EnabledIfSystemProperty(named = "logicflow.docker.image", matches = ".+")
class LogicFlowDebugDockerIT {
  @Test
  void sourceArtifactsCaptureActualContainerAndOldTargetCannotAttachAfterRecreation() throws Exception {
    try (var fixture = new DebugBrowserFixture(Path.of(System.getProperty("logicflow.docker.fixture")))) {
      fixture.create();
      var context = fixture.startPlatform(false);
      var service = context.getBean(LogicFlowDebugCaptureService.class);
      var candidates = service.candidates(DebugBrowserFixture.APP);
      assertEquals(1, candidates.size()); assertTrue(candidates.getFirst().supported());
      String runtimeId = candidates.getFirst().runtimeId();
      String id = UUID.randomUUID().toString();
      assertEquals("ACTIVE", service.start(DebugBrowserFixture.APP, runtimeId, "flow", id).state());
      fixture.execute("ok"); fixture.execute("fail");
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while (service.read(DebugBrowserFixture.APP, id).runs().stream().filter(run -> run.terminalSeq() != null).count() < 2) {
        assertTrue(System.nanoTime() < deadline, "Docker events did not reach shared repository"); Thread.sleep(100);
      }
      var capture = service.read(DebugBrowserFixture.APP, id);
      assertTrue(capture.runs().stream().anyMatch(run -> run.status().equals("COMPLETED")));
      assertTrue(capture.runs().stream().anyMatch(run -> run.status().equals("FAILED")));
      for (var run : capture.runs()) {
        var events = fixture.store.readEvents(DebugBrowserFixture.APP, id, run.runId(), 0, 200);
        assertTrue(events.complete());
        assertTrue(events.items().stream().anyMatch(event -> event.getKind().equals("input.transfer")));
        assertTrue(events.items().stream().anyMatch(event -> event.getPayload().containsKey("outputSnapshots")));
      }
      for (String artifact : java.util.List.of("ouroboros-logicflow-debug-helper", "ouroboros-dev-helper-core",
          "ouroboros-dev-helper-runtime", "ouroboros-common-dev")) {
        assertTrue(fixture.logs().contains("file:/app/deploy/extend/" + artifact + ".jar"), artifact + " did not load from extend");
      }
      String identity = Files.readString(fixture.root.resolve("deploy/identity.json"));
      fixture.remove(); fixture.create();
      assertNotEquals(Json.tryToMap(identity).get().get("instanceId"),
          Json.tryToMap(Files.readString(fixture.root.resolve("deploy/identity.json"))).get().get("instanceId"));
      var replacement = service.candidates(DebugBrowserFixture.APP).getFirst();
      assertNotEquals(runtimeId, replacement.runtimeId());
      assertThrows(com.ouroboros.exception.BusinessException.class, () -> service.renew(DebugBrowserFixture.APP, id));
      assertThrows(com.ouroboros.exception.BusinessException.class,
          () -> service.start(DebugBrowserFixture.APP, runtimeId, "flow", UUID.randomUUID().toString()));
      assertEquals("ACTIVE", service.start(DebugBrowserFixture.APP, replacement.runtimeId(), "flow", UUID.randomUUID().toString()).state());
    }
  }
}
