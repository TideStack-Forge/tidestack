package com.ouroboros.dev.codeintelligence.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import io.vavr.control.Try;

/** A second engine proves TCP environment delivery and standard editor requests share the official pool. */
@EnabledIfSystemProperty(named = "groovy.language.distribution", matches = ".+")
class TcpLanguageDeploymentTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  @TempDir Path directory;
  @Test void leasesThroughRuntimeServiceAndReclaimsOnConnectionClose() throws Exception {
    int port; try (var reserved = new java.net.ServerSocket(0)) { port = reserved.getLocalPort(); }
    var host = startHost(port);
    var runtimes = mock(DevRuntimeService.class);
    var instance = RuntimeInstance.builder().status(RuntimeStatus.RUNNING).accessRef(Map.of("language", "tcp://127.0.0.1:" + port)).build();
    when(runtimes.create(any())).thenReturn(Try.success(instance));
    when(runtimes.start(any())).thenReturn(Try.success(instance));
    when(runtimes.inspect(any())).thenReturn(Try.success(instance));
    when(runtimes.delete(any())).thenReturn(Try.success(null));
    var deployment = new ManagedLanguageServerDeployment(runtimes, BackendType.DOCKER, Map.of("fixture-tcp", "fixture-image"), 1073741824L, 2000000000L);
    try {
      var started = deployment.start(plugin(), directory, new LanguageEnvironment("b".repeat(64), 21, List.of()), ignored -> {});
      assertTrue(started.connection().isAlive());
      var spec = org.mockito.ArgumentCaptor.forClass(RuntimeSpec.class);
      verify(runtimes).create(spec.capture());
      assertNull(spec.getValue().getAppName());
      assertEquals(WorkspaceMode.NONE, spec.getValue().getWorkspaceMode());
      assertEquals(RuntimeKind.LANGUAGE_SERVICE, spec.getValue().getRuntimeKind());
      assertEquals("1073741824", spec.getValue().getResourceLimits().get("memoryBytes"));
      assertEquals(java.time.Duration.ofHours(1), spec.getValue().getTtl());
      verify(runtimes, never()).delete(any());
      started.connection().close();
      assertFalse(started.connection().isAlive());
      verify(runtimes).delete(RuntimeIdentityKey.from(spec.getValue()));
    } finally { host.destroy(); if (!host.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) host.destroyForcibly(); }
  }
  @Test void rollsBackFailedStartupAndPreservesCleanupFailure() {
    var runtimes = mock(DevRuntimeService.class);
    when(runtimes.create(any())).thenReturn(Try.success(RuntimeInstance.builder().build()));
    var startup = new IllegalStateException("runtime could not start");
    var cleanup = new IllegalStateException("runtime could not delete");
    when(runtimes.start(any())).thenReturn(Try.failure(startup));
    when(runtimes.delete(any())).thenReturn(Try.failure(cleanup));
    var deployment = new ManagedLanguageServerDeployment(runtimes, BackendType.KUBERNETES, Map.of("fixture-tcp", "fixture-image"), 1, 1);
    var failure = assertThrows(IllegalStateException.class, () -> deployment.start(plugin(), directory, new LanguageEnvironment("b".repeat(64), 21, List.of()), ignored -> {}));
    assertSame(startup, failure);
    assertArrayEquals(new Throwable[]{cleanup}, failure.getSuppressed());
    verify(runtimes).delete(any());
    var unavailable = new ManagedLanguageServerDeployment(runtimes, BackendType.DOCKER, Map.of(), 1, 1);
    assertFalse(unavailable.available(plugin()));
    assertThrows(IllegalArgumentException.class, () -> unavailable.start(plugin(), directory, new LanguageEnvironment("b".repeat(64), 21, List.of()), ignored -> {}));
  }
  @Test void materializesVerifiedArtifactsAndRunsAnotherLanguageWithTheSharedLifecycle() throws Exception {
    int port; try (var reserved = new java.net.ServerSocket(0)) { port = reserved.getLocalPort(); }
    Path artifact = directory.resolve("dependency.jar"); byte[] bytes = new byte[400000]; new Random(42).nextBytes(bytes); Files.write(artifact, bytes);
    var host = startHost(port);
    try {
      var plugin = new LanguageServerProvider() {
        public String languageId() { return "fixture-tcp"; }
        public String fileExtension() { return ".fixture"; }
        public boolean available() { return true; }
        public List<String> command(Path path, LanguageEnvironment environment) { throw new AssertionError("TCP cannot start local engine"); }
        public List<JsonNode> contextMessages(Path path, JsonNode message) { return List.of(); }
      };
      var environment = new LanguageEnvironment("a".repeat(64), 21, List.of(artifact));
      for (int run = 0; run < 2; run++) {
        try (var service = new EditorLanguageServices(List.of(plugin), directory.resolve("client"), 1, java.time.Duration.ofMinutes(1), java.time.Clock.systemUTC(),
            new TcpLanguageServerDeployment(new java.net.InetSocketAddress("127.0.0.1", port)))) {
          String session = UUID.randomUUID().toString();
          var open = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", "textDocument/didOpen"); open.putObject("params").putObject("textDocument").put("text", "hello").put("version", 1);
          service.exchange("fixture-tcp", environment, session, List.of(open)).join();
          var query = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", "textDocument/completion").put("id", "query"); query.putObject("params").putObject("textDocument");
          var response = service.exchange("fixture-tcp", environment, session, List.of(query)).join();
          assertTrue(response.stream().anyMatch(item -> item.path("result").isArray()));
          assertTrue(response.toString().contains("host/workspace-"), response.toString());
          assertFalse(response.toString().contains("client/fixture-tcp"), response.toString());
          try (var files = Files.list(directory.resolve("host"))) {
            assertEquals(1, files.filter(path -> path.toString().endsWith(".jar")).count());
          }
        }
      }
      try (var cached = Files.list(directory.resolve("host"))) {
        var path = cached.filter(item -> item.toString().endsWith(".jar")).findFirst().orElseThrow();
        assertArrayEquals(bytes, Files.readAllBytes(path));
      }
    } finally { host.destroy(); if (!host.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) host.destroyForcibly(); }
  }
  @Test void rejectsIncompatibleEnvironmentsOffsetsAndHashesAndRecoversOnTheSameHost() throws Exception {
    int port; try (var reserved = new java.net.ServerSocket(0)) { port = reserved.getLocalPort(); }
    var host = startHost(port);
    try (var connection = LanguageServerConnection.tcp(new java.net.InetSocketAddress("127.0.0.1", port), ignored -> {})) {
      byte[] content = "verified artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      String digest = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
      var manifest = JSON.createObjectNode().put("protocolVersion", 2).put("language", "fixture-tcp").put("engineVersion", "fixture-tcp").put("javaMajor", 21).put("environmentVersion", "f".repeat(64));
      manifest.putArray("artifacts").addObject().put("sha256", digest).put("size", content.length);
      assertTrue(request(connection, "ouroboros/prepareEnvironment", manifest).has("error"));
      manifest.put("protocolVersion", 1);
      assertEquals(digest, request(connection, "ouroboros/prepareEnvironment", manifest).at("/result/missing/0").asText());
      assertTrue(request(connection, "ouroboros/start", JSON.createObjectNode()).has("error"));
      var chunk = JSON.createObjectNode().put("sha256", digest).put("offset", 1).put("data", Base64.getEncoder().encodeToString(content));
      assertTrue(request(connection, "ouroboros/putArtifact", chunk).has("error"));
      chunk.put("offset", 0).put("data", Base64.getEncoder().encodeToString(new byte[content.length]));
      assertTrue(request(connection, "ouroboros/putArtifact", chunk).path("error").path("message").asText().contains("digest"));
      chunk.put("data", Base64.getEncoder().encodeToString(content));
      assertFalse(request(connection, "ouroboros/putArtifact", chunk).has("error"));
      assertTrue(request(connection, "ouroboros/start", JSON.createObjectNode()).at("/result/documentRoot").asText().startsWith("file:"));
    } finally { host.destroy(); if (!host.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) host.destroyForcibly(); }
  }
  private JsonNode request(LanguageServerConnection connection, String method, JsonNode params) {
    var message = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method).put("id", UUID.randomUUID().toString()); message.set("params", params);
    return connection.send(message).join();
  }
  private LanguageServerProvider plugin() {
    return new LanguageServerProvider() {
      public String languageId() { return "fixture-tcp"; }
      public String fileExtension() { return ".fixture"; }
      public boolean available() { return true; }
      public List<String> command(Path path, LanguageEnvironment environment) { throw new AssertionError("TCP cannot start local engine"); }
      public List<JsonNode> contextMessages(Path path, JsonNode message) { return List.of(); }
    };
  }
  private Process startHost(int port) throws Exception {
    Path definition = directory.resolve("engine.json");
    JSON.writeValue(definition.toFile(), Map.of("language", "fixture-tcp", "engineVersion", "fixture-tcp", "command", List.of(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"), FixtureLanguageServer.class.getName(), "fixture-tcp")));
    var host = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar",
        Path.of(System.getProperty("groovy.language.distribution"), "host.jar").toString(), String.valueOf(port), directory.resolve("host").toString(), definition.toString())
        .redirectError(directory.resolve("host.log").toFile()).start();
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
      while (true) {
        try (var socket = new java.net.Socket("127.0.0.1", port)) { break; }
        catch (java.net.ConnectException failure) { if (!host.isAlive() || System.nanoTime() > deadline) throw failure; Thread.sleep(25); }
      }
    return host;
  }

}
