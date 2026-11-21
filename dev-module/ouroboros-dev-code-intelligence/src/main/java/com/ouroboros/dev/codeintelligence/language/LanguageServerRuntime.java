package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.LoggerFactory;

/** A platform environment owns one warm compiler process; editor sessions own independent documents. */
public final class LanguageServerRuntime implements AutoCloseable {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final LanguageServerProvider provider;
  private final LanguageServerDeployment deployment;
  private volatile java.net.URI documentRoot;
  private final Path storage;
  private final LanguageEnvironment environment;
  private final java.time.Clock clock;
  private final Map<String, Long> sessions = new ConcurrentHashMap<>();
  private final Map<String, JsonNode> diagnostics = new ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicInteger activeRequests = new java.util.concurrent.atomic.AtomicInteger();
  private CompletableFuture<LanguageServerConnection> starting;
  private JsonNode initialization;
  private volatile Path workspace;
  private boolean closed;
  private volatile long lastActivity;

  public LanguageServerRuntime(LanguageServerProvider provider, Path storage, LanguageEnvironment environment) {
    this(provider, storage, environment, java.time.Clock.systemUTC());
  }

  LanguageServerRuntime(LanguageServerProvider provider, Path storage, LanguageEnvironment environment, java.time.Clock clock) {
    this(provider, storage, environment, clock, LanguageServerDeployment.stdio());
  }

  LanguageServerRuntime(LanguageServerProvider provider, Path storage, LanguageEnvironment environment, java.time.Clock clock,
      LanguageServerDeployment deployment) {
    this.deployment = deployment;
    this.provider = provider;
    this.storage = storage;
    this.environment = environment;
    this.clock = clock;
    this.lastActivity = clock.millis();
  }

  public CompletableFuture<List<JsonNode>> exchange(String sessionId, List<JsonNode> messages) {
    activeRequests.incrementAndGet();
    try {
      return exchangeBatch(sessionId, messages).whenComplete((value, error) -> activeRequests.decrementAndGet());
    } catch (RuntimeException failure) { activeRequests.decrementAndGet(); throw failure; }
  }

  private CompletableFuture<List<JsonNode>> exchangeBatch(String sessionId, List<JsonNode> messages) {
    if (sessionId == null || !sessionId.matches("[a-f0-9-]{36}")) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid language session"));
    if (messages == null || messages.size() > 16) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid LSP batch"));
    if (sessions.size() >= 64 && !sessions.containsKey(sessionId)) return CompletableFuture.failedFuture(new IllegalStateException("Editor session capacity exceeded"));
    if (messages.isEmpty()) {
      sessions.computeIfPresent(sessionId, (id, seen) -> clock.millis());
      if (sessions.containsKey(sessionId)) lastActivity = clock.millis();
      return CompletableFuture.completedFuture(drain(uri(sessionId)));
    }
    String uri = uri(sessionId);
    return process().thenCompose(process -> {
      boolean opening = messages.stream().anyMatch(m -> "textDocument/didOpen".equals(m.path("method").asText()));
      if (!opening && !sessions.containsKey(sessionId)) return CompletableFuture.failedFuture(new IllegalStateException("Language session expired; reopen the editor"));
      sessions.put(sessionId, clock.millis());
      lastActivity = clock.millis();
      var results = new ArrayList<JsonNode>();
      CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
      for (JsonNode message : messages) {
        chain = chain.thenCompose(ignored -> {
          String method = message.path("method").asText();
          if (method.equals("initialize")) {
            results.add(JSON.createObjectNode().put("jsonrpc", "2.0").set("id", message.get("id")));
            ((ObjectNode) results.getLast()).set("result", initialization);
            return CompletableFuture.completedFuture(null);
          }
          if (method.equals("initialized") || method.equals("exit")) return CompletableFuture.completedFuture(null);
          if (method.equals("shutdown")) {
            results.add(JSON.createObjectNode().put("jsonrpc", "2.0").set("id", message.get("id")));
            ((ObjectNode) results.getLast()).putNull("result");
            return CompletableFuture.completedFuture(null);
          }
          if (!allowed(method)) return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported LSP method"));
          if (method.equals("ouroboros/inferOutput") && initialization.path("capabilities").path("experimental").path("ouroboros").path("protocolVersion").asInt() != 1)
            return CompletableFuture.failedFuture(new IllegalStateException("Language engine does not support output contracts v1"));
          ObjectNode params = (ObjectNode) message.path("params");
          if (params.has("textDocument")) ((ObjectNode) params.get("textDocument")).put("uri", serverUri(sessionId));
          if (method.equals("ouroboros/context")) {
            params.put("uri", serverUri(sessionId));
            CompletableFuture<Void> projected = CompletableFuture.completedFuture(null);
            for (var adapted : provider.contextMessages(workspace, message)) projected = projected.thenCompose(done -> process.send(adapted).thenApply(response -> { requireSuccess(response); return null; }));
            return projected;
          }
          return process.send(message).thenAccept(response -> { if (response != null) results.add(publicUris(response)); });
        });
      }
      return chain.thenCompose(ignored -> provider.synchronizationMethod()
          .map(method -> process.send(request(method)).thenApply(response -> { requireSuccess(response); return (Void) null; }))
          .orElseGet(() -> CompletableFuture.completedFuture(null)))
          .thenApply(ignored -> {
            results.addAll(drain(uri));
            return List.copyOf(results);
          });
    });
  }

  public CompletableFuture<Void> closeSession(String sessionId) {
    sessions.remove(sessionId);
    String uri = uri(sessionId);
    diagnostics.remove(uri);
    CompletableFuture<LanguageServerConnection> current;
    synchronized (this) { current = starting; }
    if (current == null || current.isCompletedExceptionally()) return CompletableFuture.completedFuture(null);
    ObjectNode message = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", "textDocument/didClose");
    message.putObject("params").putObject("textDocument").put("uri", serverUri(sessionId));
    return current.thenCompose(process -> process.send(message)).thenApply(ignored -> null);
  }

  private static boolean allowed(String method) {
    return List.of("textDocument/didOpen", "textDocument/didChange", "textDocument/didClose", "textDocument/didSave",
        "textDocument/completion", "textDocument/hover", "textDocument/signatureHelp", "textDocument/definition",
        "textDocument/references", "textDocument/rename", "textDocument/documentSymbol", "textDocument/formatting",
        "textDocument/codeAction", "textDocument/semanticTokens/full", "ouroboros/inferOutput", "ouroboros/context", "$/cancelRequest").contains(method);
  }

  private static void requireSuccess(JsonNode response) {
    if (response != null && response.has("error")) throw new IllegalStateException("Language plugin protocol failed: " + response.get("error"));
  }

  private List<JsonNode> drain(String uri) {
    JsonNode latest = diagnostics.remove(uri);
    return latest == null ? List.of() : List.of(latest);
  }

  private synchronized CompletableFuture<LanguageServerConnection> process() {
    if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Language runtime is closed"));
    if (starting != null && !starting.isCompletedExceptionally() && (!starting.isDone() || starting.join().isAlive())) return starting;
    if (starting != null) {
      sessions.clear(); diagnostics.clear();
      removeWorkspace();
    }
    starting = CompletableFuture.supplyAsync(() -> {
      LanguageServerConnection process = null;
      try {
        Files.createDirectories(storage);
        workspace = Files.createTempDirectory(storage, "runtime-");
        var started = deployment.start(provider, workspace, environment, this::notification);
        process = started.connection(); documentRoot = started.documentRoot();
        ObjectNode init = request("initialize");
        var initParams = init.putObject("params");
        initParams.put("rootUri", documentRoot.toString());
        initParams.putObject("initializationOptions").putObject("ouroboros").put("protocolVersion", 1);
        initParams.putObject("capabilities").putObject("workspace").putObject("workspaceEdit").put("documentChanges", true);
        JsonNode response = process.send(init).join();
        if (response.has("error")) { throw new IllegalStateException("Editor initialization failed: " + response.get("error")); }
        initialization = response.get("result");
        process.send(JSON.createObjectNode().put("jsonrpc", "2.0").put("method", "initialized").set("params", JSON.createObjectNode())).join();
        return process;
      } catch (Exception e) {
        if (process != null) {
          try { process.close(); } catch (RuntimeException closeError) { e.addSuppressed(closeError); }
        }
        throw new IllegalStateException("Cannot start " + provider.languageId() + " language service: " + e.getMessage(), e);
      }
    });
    return starting;
  }

  private void notification(JsonNode message) {
    if ("textDocument/publishDiagnostics".equals(message.path("method").asText())) {
      message = publicUris(message);
      String uri = message.path("params").path("uri").asText();
      if (sessions.keySet().stream().anyMatch(session -> uri(session).equals(uri))) diagnostics.put(uri, message);
    } else if ("window/logMessage".equals(message.path("method").asText())) {
      LoggerFactory.getLogger(LanguageServerRuntime.class).warn("Editor language service: {}", message.path("params").path("message").asText());
    }
  }

  private String serverUri(String session) { return documentRoot.resolve(session + provider.fileExtension()).toString(); }

  private JsonNode publicUris(JsonNode value) {
    if (value.isObject()) {
      var object = (ObjectNode) value;
      object.properties().forEach(entry -> {
        if ((entry.getKey().equals("uri") || entry.getKey().equals("targetUri")) && entry.getValue().isTextual()) {
          for (String session : sessions.keySet()) if (serverUri(session).equals(entry.getValue().asText())) object.put(entry.getKey(), uri(session));
        } else publicUris(entry.getValue());
      });
      // WorkspaceEdit.changes uses URIs as object keys.
      if (object.path("changes").isObject()) {
        var changes = (ObjectNode) object.get("changes");
        for (String session : sessions.keySet()) { var edits = changes.remove(serverUri(session)); if (edits != null) changes.set(uri(session), edits); }
      }
    } else if (value.isArray()) value.forEach(this::publicUris);
    return value;
  }

  private static ObjectNode request(String method) {
    return JSON.createObjectNode().put("jsonrpc", "2.0").put("id", UUID.randomUUID().toString()).put("method", method).set("params", JSON.createObjectNode());
  }
  public static String uri(String sessionId) { return "ouroboros://language/" + sessionId + "/script"; }

  void expireSessions(long cutoff) {
    if (activeRequests.get() > 0) return;
    sessions.forEach((session, seen) -> {
      if (seen < cutoff) closeSession(session).exceptionally(error -> { LoggerFactory.getLogger(LanguageServerRuntime.class).error("Could not expire language session", error); return null; });
    });
  }

  boolean idle() { return activeRequests.get() == 0 && sessions.isEmpty() && (starting == null || starting.isDone()); }
  long lastActivity() { return lastActivity; }

  private void removeWorkspace() {
    if (workspace == null) return;
    try (var paths = Files.walk(workspace)) {
      for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    } catch (java.io.IOException error) {
      LoggerFactory.getLogger(LanguageServerRuntime.class).warn("Could not remove language workspace {}", workspace, error);
    }
    workspace = null;
  }

  @Override public synchronized void close() {
    closed = true;
    if (starting != null) starting.handle((process, error) -> { if (process != null) process.close(); return null; }).join();
    starting = null;
    sessions.clear(); diagnostics.clear();
    removeWorkspace();
  }
}
