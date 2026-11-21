package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EditorLanguageServicesTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  @TempDir Path directory;

  private LanguageEnvironment environment() { return new LanguageEnvironment("a".repeat(64), 21, List.of()); }

  private LanguageServerProvider plugin(String language) {
    return new LanguageServerProvider() {
      public String languageId() { return language; }
      public String fileExtension() { return ".fixture"; }
      public boolean available() { return true; }
      public List<String> command(Path workspace, LanguageEnvironment environment) {
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"), FixtureLanguageServer.class.getName(), language);
      }
      public List<JsonNode> contextMessages(Path workspace, JsonNode context) {
        var projected = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", "workspace/didChangeConfiguration");
        projected.putObject("params").put("field", context.path("params").path("snapshot").path("field").asText());
        return List.of(projected);
      }
    };
  }
  @Test void discoversPluginsAndReusesLifecycleWithDifferentContextProjectionWithoutGroovyProtocol() throws Exception {
    long before = ProcessHandle.current().descendants().count();
    try (var services = new EditorLanguageServices(List.of(plugin("fixture-a"), plugin("fixture-b")), directory, 4, java.time.Duration.ofMinutes(10), java.time.Clock.systemUTC())) {
      assertEquals(List.of("fixture-a", "fixture-b"), services.languages().stream().map(EditorLanguageServices.Descriptor::languageId).toList());
      assertThrows(IllegalArgumentException.class, () -> services.exchange("missing", environment(), UUID.randomUUID().toString(), List.of()));
      for (String language : List.of("fixture-a", "fixture-b")) {
        String session = UUID.randomUUID().toString();
        var opened = services.exchange(language, environment(), session, List.of(
            message("initialize", "init", "{}"), message("textDocument/didOpen", null, "{\"textDocument\":{\"text\":\"hello\",\"version\":1}}"),
            message("ouroboros/context", null, "{\"snapshot\":{\"field\":\"customerName\"}}"))).join();
        assertTrue(opened.getFirst().path("result").path("capabilities").has("completionProvider"));
        List<JsonNode> notifications = new java.util.ArrayList<>(opened);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (notifications.stream().noneMatch(m -> m.path("method").asText().equals("textDocument/publishDiagnostics")) && System.nanoTime() < deadline) {
          Thread.sleep(20);
          notifications.addAll(services.exchange(language, environment(), session, List.of()).join());
        }
        var diagnostic = notifications.stream().filter(m -> m.path("method").asText().equals("textDocument/publishDiagnostics")).findFirst().orElseThrow();
        assertEquals(LanguageServerRuntime.uri(session), diagnostic.path("params").path("uri").asText());
        assertEquals(1, diagnostic.path("params").path("version").asInt());
        assertEquals("fixture diagnostic", diagnostic.path("params").path("diagnostics").get(0).path("message").asText());
        var result = services.exchange(language, environment(), session, List.of(message("textDocument/completion", "complete", "{\"textDocument\":{},\"position\":{\"line\":0,\"character\":0}}"))).join().stream().filter(m -> m.has("result")).findFirst().orElseThrow().path("result").get(0);
        assertEquals("customerName", result.path("label").asText());
        assertTrue(result.path("detail").asText().startsWith(language + ":file:"));
        assertTrue(result.path("detail").asText().endsWith(".fixture"));
        services.closeSession(language, environment(), session).join();
        assertThrows(RuntimeException.class, () -> services.exchange(language, environment(), session, List.of(message("textDocument/hover", "old", "{}"))).join());
      }
    }
    assertEquals(before, ProcessHandle.current().descendants().count());
    try (var files = java.nio.file.Files.list(directory)) { assertEquals(2, files.count()); }
    try (var files = java.nio.file.Files.walk(directory)) { assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("runtime-"))); }
  }
  @Test void rejectsStaleDocumentsAfterProcessExitAndAcceptsNewSessions() throws Exception {
    try (var services = new EditorLanguageServices(List.of(plugin("fixture-recovery")), directory, 4, java.time.Duration.ofMinutes(10), java.time.Clock.systemUTC())) {
      String old = UUID.randomUUID().toString();
      services.exchange("fixture-recovery", environment(), old, List.of(message("textDocument/didOpen", null, "{\"textDocument\":{\"text\":\"before\",\"version\":1}}"))).join();
      var process = ProcessHandle.current().descendants().filter(p -> p.info().commandLine().orElse("").contains("FixtureLanguageServer fixture-recovery")).findFirst().orElseThrow();
      process.destroyForcibly(); process.onExit().get(5, java.util.concurrent.TimeUnit.SECONDS);
      assertThrows(RuntimeException.class, () -> services.exchange("fixture-recovery", environment(), old, List.of(message("textDocument/completion", "stale", "{}"))).join());
      String fresh = UUID.randomUUID().toString();
      services.exchange("fixture-recovery", environment(), fresh, List.of(message("textDocument/didOpen", null, "{\"textDocument\":{\"text\":\"after\",\"version\":1}}"),
          message("ouroboros/context", null, "{\"snapshot\":{\"field\":\"freshField\"}}"))).join();
      var response = services.exchange("fixture-recovery", environment(), fresh, List.of(message("textDocument/completion", "fresh", "{\"textDocument\":{}}"))).join();
      assertEquals("freshField", response.stream().filter(m -> m.has("result")).findFirst().orElseThrow().path("result").get(0).path("label").asText());
    }
  }
  @Test void initializationFailureClosesSpawnedServer() throws Exception {
    try (var services = new EditorLanguageServices(List.of(plugin("fixture-failed-init")), directory, 4, java.time.Duration.ofMinutes(10), java.time.Clock.systemUTC())) {
      var failure = assertThrows(RuntimeException.class, () -> services.exchange("fixture-failed-init", environment(), UUID.randomUUID().toString(),
          List.of(message("textDocument/didOpen", null, "{}"))).join());
      assertTrue(failure.getMessage().contains("fixture initialization failure"));
      assertFalse(ProcessHandle.current().descendants().anyMatch(p -> p.info().commandLine().orElse("").contains("FixtureLanguageServer fixture-failed-init")));
    }
  }
  @Test void rejectsDuplicateLanguageRegistrations() {
    assertThrows(IllegalArgumentException.class, () -> new EditorLanguageServices(List.of(plugin("fixture"), plugin("fixture")), directory, 4, java.time.Duration.ofMinutes(10), java.time.Clock.systemUTC()));
  }
  @Test void boundsProcessesReusesEnvironmentAndReclaimsIdleCapacity() throws Exception {
    try (var services = new EditorLanguageServices(List.of(plugin("fixture")), directory, 1, java.time.Duration.ofMinutes(10), java.time.Clock.systemUTC())) {
      var first = environment();
      var second = new LanguageEnvironment("b".repeat(64), 21, List.of());
      String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString(), c = UUID.randomUUID().toString();
      services.exchange("fixture", first, a, List.of(message("textDocument/didOpen", null, "{\"textDocument\":{\"text\":\"one\",\"version\":1}}"))).join();
      services.exchange("fixture", first, b, List.of(message("textDocument/didOpen", null, "{\"textDocument\":{\"text\":\"two\",\"version\":1}}"))).join();
      assertEquals(1, ProcessHandle.current().descendants().filter(p -> p.info().commandLine().orElse("").contains("FixtureLanguageServer fixture")).count());
      assertTrue(assertThrows(RuntimeException.class, () -> services.exchange("fixture", second, c, List.of(message("textDocument/didOpen", null, "{}"))).join()).getMessage().contains("容量"));
      services.closeSession("fixture", first, a).join(); services.closeSession("fixture", first, b).join();
      services.exchange("fixture", second, c, List.of(message("textDocument/didOpen", null, "{\"textDocument\":{\"text\":\"three\",\"version\":1}}"))).join();
      assertEquals(1, ProcessHandle.current().descendants().filter(p -> p.info().commandLine().orElse("").contains("FixtureLanguageServer fixture")).count());
    }
  }
  @Test void reapsInactiveEnvironmentsButPollingKeepsAnEditorAlive() throws Exception {
    var time = new java.util.concurrent.atomic.AtomicLong(1_000_000);
    var clock = new java.time.Clock() {
      public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
      public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
      public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(time.get()); }
    };
    try (var services = new EditorLanguageServices(List.of(plugin("fixture-idle")), directory, 1, java.time.Duration.ofMinutes(10), clock)) {
      String session = UUID.randomUUID().toString();
      services.exchange("fixture-idle", environment(), session, List.of(message("textDocument/didOpen", null,
          "{\"textDocument\":{\"text\":\"active\",\"version\":1}}"))).join();
      var child = ProcessHandle.current().descendants().filter(p -> p.info().commandLine().orElse("").contains("FixtureLanguageServer fixture-idle")).findFirst().orElseThrow();
      time.addAndGet(java.time.Duration.ofMinutes(9).toMillis());
      services.exchange("fixture-idle", environment(), session, List.of()).join();
      time.addAndGet(java.time.Duration.ofMinutes(9).toMillis());
      services.reap();
      assertTrue(child.isAlive());
      time.addAndGet(java.time.Duration.ofMinutes(2).toMillis());
      services.reap();
      assertFalse(child.isAlive());
      try (var paths = java.nio.file.Files.walk(directory)) { assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith("runtime-"))); }
    }
  }
  private static JsonNode message(String method, String id, String params) throws Exception {
    var message = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method);
    if (id != null) message.put("id", id);
    message.set("params", JSON.readTree(params)); return message;
  }
}
